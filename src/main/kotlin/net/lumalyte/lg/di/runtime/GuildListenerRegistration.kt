package net.lumalyte.lg.di.runtime

import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.infrastructure.services.LumaGuildsChannelProvider
import net.lumalyte.lg.interaction.listeners.BannerFuelPreventionListener
import net.lumalyte.lg.interaction.listeners.BlockLaunchListener
import net.lumalyte.lg.interaction.listeners.GuildVaultFuelPreventionListener
import org.bukkit.Bukkit
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Registers LumaGuilds' Bukkit listeners and optional plugin hooks from the active graph.
 *
 * Claim listeners register only when claims are enabled. Optional hooks (RoseChat, EnthusiaStaff,
 * LiteBans, DiscordSRV) keep their late-enable listeners and construct plugin-bound classes only
 * once the dependency is enabled. Listener handles are released by the runtime's
 * `HandlerList.unregisterAll`; explicit integration releases are recorded on [shutdown].
 */
internal class GuildListenerRegistration(
    private val plugin: LumaGuilds,
    private val graph: GuildRuntimeGraph,
    private val shutdown: RuntimeShutdown,
) {
    private val server get() = plugin.server
    private var liteBansStrikeHookRegistered = false
    private var enthusiaStaffStrikeFeed: AutoCloseable? = null

    private fun listen(listener: Listener) = server.pluginManager.registerEvents(listener, plugin)

    fun register(claimsEnabled: Boolean) {
        val claims = graph.claims
        if (claimsEnabled && claims != null) {
            registerClaimEvents(claims)
            plugin.logColored("✓ Claims system enabled")
        } else {
            plugin.logColored("⚠ Claims system disabled - all claim features unavailable")
        }
        registerNonClaimEvents()
    }

    private fun registerClaimEvents(claims: ClaimRuntimeGraph) {
        listen(BlockLaunchListener(plugin))
        listen(claims.claimAnchorListener)
        listen(claims.claimDestructionListener)
        listen(claims.editToolListener)
        listen(claims.editToolVisualisingListener)
        listen(claims.harvestReplantListener)
        listen(claims.moveToolListener)
        listen(claims.partitionUpdateListener)
        listen(claims.playerClaimProtectionListener)
        listen(claims.claimPvpProtectionListener)
        listen(claims.toolRemovalListener)
        listen(claims.worldClaimProtectionListener)
        listen(claims.closeInventoryListener)
    }

    private fun registerNonClaimEvents() {
        // Shared per-player chat-input state lives in this single graph instance.
        listen(graph.chatInputListener)
        val config = graph.configService.loadConfig()

        // Guild chat is owned by RoseChat's LumaGuildsChannel hook; /g chat delegates to this singleton.
        graph.guildChatListener
        plugin.logColored("✓ /g chat wired to RoseChat guild channel")

        val partyChatListener = graph.partyChatListener
        server.pluginManager.registerEvent(
            io.papermc.paper.event.player.AsyncChatEvent::class.java,
            partyChatListener,
            parsePriority(config.party.partyChatListenerPriority),
            { _, event -> partyChatListener.onPlayerChat(event as io.papermc.paper.event.player.AsyncChatEvent) },
            plugin,
            false, // run even if RoseChat/other plugins already cancelled
        )
        plugin.logColored("✓ Party chat auto-routing registered (priority: ${config.party.partyChatListenerPriority})")

        listen(graph.bannerSelectionListener)
        listen(BannerFuelPreventionListener())
        listen(graph.warBannerListener)
        listen(graph.spawnBannerListener)
        listen(net.lumalyte.lg.interaction.listeners.GuildVaultCraftingPreventionListener())
        listen(GuildVaultFuelPreventionListener())
        listen(graph.bannermanListeners)

        val progressionEventListener = graph.progressionEventListener
        listen(progressionEventListener)
        // Cancels/joins only its child flush job, then drains pending XP before the context closes.
        shutdown.register(ShutdownStage.FLUSH, "progression XP flush") { progressionEventListener.shutdown() }
        listen(graph.questProgressListener)
        listen(graph.guildChannelCreationListener)

        val vaultProtectionListener = graph.vaultProtectionListener
        plugin.vaultProtectionListener = vaultProtectionListener
        listen(vaultProtectionListener)
        listen(graph.vaultInventoryListener)
        listen(graph.guildOnboardingListener)
        listen(graph.guildRecruitmentListener)
        listen(graph.playerSessionListener)
        listen(graph.warKillTrackingListener)
        listen(graph.combatAntiGriefListener)
        listen(graph.guildDisbandedListener)

        listen(graph.guildEmojiGrantListener)
        val emojiResult = graph.guildEmojiGrantService.reconcileAll()
        if (!emojiResult.successful) {
            plugin.logger.warning("Emoji permission reconciliation completed with ${emojiResult.failed} failure(s)")
        }

        registerDiscord()
        registerRoseChat()

        // Resolved at shutdown, as before: hides any boss bars the indicator owns.
        shutdown.register(ShutdownStage.GATES, "chat destination indicator") { graph.chatDestinationIndicator.close() }

        // Session-only bypass must be cleared on logout even when claims are disabled.
        listen(net.lumalyte.lg.interaction.listeners.AdminOverrideListener(graph.adminOverrideService, graph.guildRolePermissionResolver))

        registerStrikeFeeds()

        // NexoItemProvider listener fires on NexoItemsLoadedEvent.
        net.lumalyte.lg.utils.NexoItemProvider.register(plugin)

        // Vanilla icons for Bedrock players and Vanilla-style guilds; plain titles for Bedrock.
        runCatching {
            net.lumalyte.lg.infrastructure.services.MenuIconAdapter(
                plugin,
                graph.platformDetectionService,
                graph.guildService,
                bedrockConfig = { graph.configService.loadConfig().bedrock },
            ).register()
        }.onFailure { plugin.logger.warning("Menu icon adapter not started: ${it.message}") }
    }

    /** The listener is always registered; role sync is a no-op unless discord.guild_roles.enabled. */
    private fun registerDiscord() {
        listen(graph.guildDiscordRoleListener)
        if (graph.configService.loadConfig().discordGuildRoles.enabled) {
            val accountLinks = graph.discordAccountLinkSubscription
            accountLinks.subscribe()
            shutdown.register(ShutdownStage.INTEGRATIONS, "DiscordSRV account-link listener") { accountLinks.unsubscribe() }
        }
        // Public guild profiles are independent of role syncing.
        val profiles = graph.discordGuildProfileSubscription
        profiles.subscribe()
        shutdown.register(ShutdownStage.INTEGRATIONS, "DiscordSRV guild profile commands") { profiles.unsubscribe() }
    }

    /**
     * LumaGuilds can enable before RoseChat despite `depend: [RoseChat]`; wire now when enabled,
     * otherwise on RoseChat's PluginEnableEvent.
     */
    private fun registerRoseChat() {
        val wireRoseChatHook = {
            listen(graph.roseChatCleanupListener)
            listen(graph.guildChatReconnectListener)
            plugin.logColored("✓ RoseChat integration registered for chat cleanup")
            // Enforce guild mutes and invite blocks on the live RoseChat message path.
            listen(graph.guildMuteChatListener)
            listen(graph.inviteBlockChatListener)
            registerRoseChatChannels()
        }
        when {
            server.pluginManager.isPluginEnabled("RoseChat") -> wireRoseChatHook()
            server.pluginManager.getPlugin("RoseChat") != null ->
                listen(net.lumalyte.lg.infrastructure.listeners.RoseChatEnableListener { wireRoseChatHook() })
        }
    }

    /** Registers the channel provider and re-reads channels.yml one tick later. */
    private fun registerRoseChatChannels() {
        try {
            val channelManager = dev.rosewood.rosechat.api.RoseChatAPI.getInstance().channelManager
            channelManager.register(LumaGuildsChannelProvider(graph.channelDependencies()))
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                try {
                    channelManager.reload()
                    plugin.logColored("✓ LumaGuilds RoseChat channels registered (guild, guild-ally, guild-modchat)")
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to reload RoseChat channels after provider registration: ${e.message}")
                }
            }, 1L)
        } catch (e: NoClassDefFoundError) {
            plugin.logColored("⚠ RoseChat API classes unavailable — guild channels not registered")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to register LumaGuilds RoseChat channels: ${e.message}")
        }
    }

    /** Guild Strikes: prefer the provider-neutral EnthusiaStaff feed; LiteBans is the fallback hook. */
    private fun registerStrikeFeeds() {
        listen(net.lumalyte.lg.infrastructure.enthusiastaff.EnthusiaStaffEnableListener { registerEnthusiaStaffStrikeFeed() })
        if (server.pluginManager.isPluginEnabled("EnthusiaStaff")) {
            registerEnthusiaStaffStrikeFeed()
        } else {
            plugin.logger.info("EnthusiaStaff not yet enabled - Guild Strikes feed will wire when it enables")
        }

        listen(net.lumalyte.lg.infrastructure.litebans.LiteBansEnableListener { registerLiteBansStrikeHook() })
        if (net.lumalyte.lg.infrastructure.litebans.LiteBansEnableListener.liteBansPresent(server) &&
            server.pluginManager.isPluginEnabled("LiteBans")
        ) {
            registerLiteBansStrikeHook()
        } else {
            plugin.logger.info("LiteBans not yet enabled - Guild Strikes hook will wire when it enables")
        }
    }

    private fun registerEnthusiaStaffStrikeFeed() {
        if (enthusiaStaffStrikeFeed != null || liteBansStrikeHookRegistered) return
        try {
            val feed = net.lumalyte.lg.infrastructure.enthusiastaff.EnthusiaStaffStrikeFeed(
                plugin = plugin,
                guildService = graph.guildService,
                strikeService = graph.strikeService,
                membershipHistoryRepository = graph.membershipHistoryRepository,
                configProvider = { graph.strikesConfig },
            )
            if (!feed.start()) {
                plugin.logger.info("EnthusiaStaff lifecycle API is not available yet; Guild Strikes feed remains unwired")
                return
            }
            enthusiaStaffStrikeFeed = feed
            shutdown.register(ShutdownStage.GATES, "EnthusiaStaff strike feed") {
                enthusiaStaffStrikeFeed = null
                feed.close()
            }
            plugin.logColored("✓ Guild Strikes hooked into EnthusiaStaff punishment lifecycle")
        } catch (e: LinkageError) {
            plugin.logger.warning("Failed to load EnthusiaStaff Guild Strikes API: ${e.message}")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to register EnthusiaStaff Guild Strikes feed: ${e.message}")
        }
    }

    /**
     * Wires the Guild Strikes listener into LiteBans and starts the idempotent backfill. LiteBans
     * classes are constructed only after LiteBans is enabled (true optional dependency); a
     * LinkageError from an incompatible API never takes LumaGuilds down.
     */
    private fun registerLiteBansStrikeHook() {
        if (liteBansStrikeHookRegistered || enthusiaStaffStrikeFeed != null) return
        try {
            val strikeService = graph.strikeService
            val guildService = graph.guildService
            val membershipHistoryRepository = graph.membershipHistoryRepository
            val configProvider = { graph.strikesConfig }
            val listener = net.lumalyte.lg.infrastructure.litebans.LiteBansStrikeListener(
                plugin = plugin,
                guildService = guildService,
                strikeService = strikeService,
                membershipHistoryRepository = membershipHistoryRepository,
                configProvider = configProvider,
            )
            litebans.api.Events.get().register(listener)
            liteBansStrikeHookRegistered = true
            shutdown.register(ShutdownStage.INTEGRATIONS, "LiteBans strike listener") {
                litebans.api.Events.get().unregister(listener)
            }
            plugin.logColored("✓ Guild Strikes hooked into LiteBans (punishments tracked per guild)")

            // One-shot asynchronous backfill, deduped by LiteBans entry id.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                try {
                    val result = net.lumalyte.lg.infrastructure.litebans.StrikeBackfillService(
                        strikeService = strikeService,
                        membershipHistoryRepository = membershipHistoryRepository,
                        guildService = guildService,
                        configProvider = configProvider,
                    ).run()
                    plugin.logColored(
                        "✓ Guild Strikes backfill: ${result.recorded} recorded, " +
                            "${result.attributed} attributed, ${result.skippedUnattributable} skipped (unattributable)",
                    )
                } catch (e: LinkageError) {
                    plugin.logger.warning("Guild Strikes backfill unavailable (LiteBans API incompatible): ${e.message}")
                } catch (e: Exception) {
                    plugin.logger.warning("Guild Strikes backfill failed: ${e.message}")
                }
            })
        } catch (e: LinkageError) {
            plugin.logger.warning("Failed to register LiteBans strike listener (LiteBans API incompatible): ${e.message}")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to register LiteBans strike listener: ${e.message}")
        }
    }

    private fun parsePriority(priority: String): EventPriority =
        runCatching { EventPriority.valueOf(priority.uppercase()) }.getOrElse {
            plugin.logColored("⚠ Invalid event priority '$priority', defaulting to NORMAL")
            EventPriority.NORMAL
        }
}
