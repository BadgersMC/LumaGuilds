package net.lumalyte.lg.di.runtime

import co.aikar.idb.Database
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.di.nexus.GuildNexusFoundation
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.bukkit.plugin.ServicePriority
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/** The active runtime: one Nexus context, its typed graph and the storage it was built on. */
internal class ActiveGuildRuntime(
    val foundation: GuildNexusFoundation,
    val graph: GuildRuntimeGraph,
    val storage: Storage<Database>,
)

/**
 * Paper implementation of [GuildRuntimeSteps], replacing the Koin/ACF startup in LumaGuilds.kt.
 *
 * All steps run on the server thread during enable/disable. Bukkit timers remain synchronous
 * where they were synchronous; Nexus-managed coroutine work uses the context's virtual-thread
 * dispatcher. Every acquisition records its release on the [RuntimeShutdown].
 */
internal class PaperGuildRuntime(
    private val plugin: LumaGuilds,
    private val claimsEnabled: Boolean,
    private val commands: GuildCommandRegistration,
) : GuildRuntimeSteps<Storage<Database>, ActiveGuildRuntime> {
    private val logger get() = plugin.logger

    override fun verifyCommandParity() = commands.verify(claimsEnabled)

    override fun openStorage(): Storage<Database> = plugin.createStorage(claimsEnabled)

    override fun migrate(storage: Storage<Database>) = plugin.initDatabase(storage, claimsEnabled)

    override fun closeStorage(storage: Storage<Database>) {
        storage.connection.close()
        plugin.logColored("✓ Guild storage closed")
    }

    override fun activate(storage: Storage<Database>): ActiveGuildRuntime {
        val foundation = GuildNexusFoundation.prepare(plugin, storage, claimsEnabled)
        return ActiveGuildRuntime(foundation, NexusGuildRuntimeGraph(foundation.context, claimsEnabled), storage)
    }

    override fun closeContext(context: ActiveGuildRuntime) {
        val executor = (context.foundation.context.dispatchers?.virtualThread as? ExecutorCoroutineDispatcher)
            ?.executor as? ExecutorService
        context.foundation.close()
        // NexusContext.close() requests shutdown without waiting; bound the wait like the legacy executor.
        if (executor != null && !executor.awaitTermination(5, TimeUnit.SECONDS)) {
            logger.warning("Nexus virtual-thread work did not finish within 5 seconds; interrupting remaining tasks")
            executor.shutdownNow()
        }
    }

    override fun publishServices(context: ActiveGuildRuntime, shutdown: RuntimeShutdown) {
        val graph = context.graph
        reconcileRewardAccounts(graph)
        shutdown.register(ShutdownStage.SERVICES, "Bukkit services") { Bukkit.getServicesManager().unregisterAll(plugin) }
        publishGuildServices(plugin, graph, context.storage.dialect, shutdown)
        logColored("✓ Guild service APIs registered in ServicesManager")
    }

    override fun registerIntegrations(context: ActiveGuildRuntime, shutdown: RuntimeShutdown) {
        val graph = context.graph
        // First listener registrations (Apollo) happen here; release every plugin listener in TASKS.
        shutdown.register(ShutdownStage.TASKS, "remaining Bukkit tasks") { plugin.server.scheduler.cancelTasks(plugin) }
        shutdown.register(ShutdownStage.TASKS, "Bukkit listeners") { HandlerList.unregisterAll(plugin) }
        registerPlaceholderExpansion(graph, shutdown)
        registerApollo(graph, shutdown)
        net.lumalyte.lg.infrastructure.services.GuildInvitationManager.initialize(
            graph.guildInvitationRepository,
            graph.inviteBlockService,
            notificationProvider = { graph.guildNotificationService },
        )
        net.lumalyte.lg.application.utilities.GoldBalanceButton.initialize(plugin, graph.configService)
    }

    override fun registerListeners(context: ActiveGuildRuntime, shutdown: RuntimeShutdown) {
        GuildListenerRegistration(plugin, context.graph, shutdown).register(claimsEnabled)
    }

    override fun registerCommands(context: ActiveGuildRuntime, shutdown: RuntimeShutdown) {
        commands.register(plugin, context, claimsEnabled)
    }

    override fun startSchedules(context: ActiveGuildRuntime, shutdown: RuntimeShutdown) {
        GuildScheduleRegistration(plugin, context.graph, context.storage, shutdown).start()
    }

    private fun reconcileRewardAccounts(graph: GuildRuntimeGraph) {
        // Idempotent repair: every live guild must have reward/prestige state before APIs publish.
        try {
            val reconciled = graph.rewardOwnershipRepository.reconcileMissingGuildAccounts()
            if (reconciled > 0) logColored("✓ Initialized Chapter 2 reward/prestige state for $reconciled existing guild(s)")
        } catch (error: Exception) {
            logger.log(
                Level.SEVERE,
                "Failed to reconcile missing guild reward/prestige accounts; affected guilds will remain unavailable until repaired",
                error,
            )
        }
    }

    private fun registerPlaceholderExpansion(graph: GuildRuntimeGraph, shutdown: RuntimeShutdown) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            logColored("⚠ PlaceholderAPI not found. LumaGuilds placeholders will not be available.")
            return
        }
        try {
            val expansion = graph.lumaGuildsExpansion ?: return
            if (expansion.register()) {
                shutdown.register(ShutdownStage.INTEGRATIONS, "PlaceholderAPI expansion") { expansion.unregister() }
                logColored("✓ Successfully registered LumaGuilds PlaceholderAPI expansion!")
                logColored("Available placeholders: %lumaguilds_guild_name%, %lumaguilds_guild_tag%, etc.")
            } else {
                logColored("❌ Failed to register LumaGuilds PlaceholderAPI expansion!")
            }
        } catch (e: Exception) {
            // Optional integration: NoClassDefFoundError/LinkageError or API mismatch must not stop startup.
            logger.log(Level.SEVERE, "Error registering PlaceholderAPI expansion: ${e.message}", e)
        } catch (e: LinkageError) {
            logger.log(Level.SEVERE, "PlaceholderAPI expansion classes unavailable: ${e.message}", e)
        }
    }

    private fun registerApollo(graph: GuildRuntimeGraph, shutdown: RuntimeShutdown) {
        val config = plugin.config
        if (!config.getBoolean("apollo.enabled", true)) {
            logColored("⚠ Apollo integration disabled in config")
            return
        }
        if (Bukkit.getPluginManager().getPlugin("Apollo-Bukkit") == null) {
            logColored("⚠ Apollo-Bukkit plugin not found. Lunar Client features unavailable.")
            logColored("  Install Apollo-Bukkit from https://modrinth.com/plugin/lunar-client-apollo")
            return
        }
        try {
            val lunarClientService = graph.lunarClientService
            if (lunarClientService == null || !lunarClientService.isApolloAvailable()) {
                logColored("⚠ Apollo API not responding - features disabled")
                return
            }
            logColored("✓ Successfully initialized Apollo (Lunar Client) integration!")
            logColored("Enhanced features available for Lunar Client users")
            val events = plugin.server.pluginManager
            if (config.getBoolean("apollo.teams.enabled", true)) {
                logColored("  ⭐ Teams Module: Guild members visible on minimap/HUD")
                graph.guildTeamService?.let { teams ->
                    teams.start()
                    shutdown.register(ShutdownStage.INTEGRATIONS, "Apollo team service") { teams.stop() }
                }
                graph.guildTeamListener?.let { events.registerEvents(it, plugin) }
            }
            if (config.getBoolean("apollo.waypoints.enabled", true)) logColored("  📍 Waypoints Module: Guild home navigation")
            if (config.getBoolean("apollo.beams.enabled", true)) logColored("  ⚡ Beams Module: Vault location markers")
            if (config.getBoolean("apollo.borders.enabled", true)) logColored("  🔲 Borders Module: War territory visualization")
            if (config.getBoolean("apollo.notifications.enabled", true)) {
                logColored("  📢 Notifications Module: Rich guild event alerts")
                graph.guildNotificationListener?.let { events.registerEvents(it, plugin) }
            }
            if (config.getBoolean("apollo.richpresence.enabled", true)) {
                logColored("  👥 Rich Presence Module: Discord/Launcher guild status")
                graph.guildRichPresenceListener?.let { events.registerEvents(it, plugin) }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error initializing Apollo integration: ${e.message}", e)
        } catch (e: LinkageError) {
            logger.log(Level.SEVERE, "Apollo integration classes unavailable: ${e.message}", e)
        }
    }

    private fun logColored(message: String) = plugin.logColored(message)
}

/**
 * Publishes LumaGuilds' external Bukkit service contracts from the active graph's singletons.
 * The durable invite-block gate is released first on shutdown, before services unregister.
 */
internal fun publishGuildServices(
    plugin: LumaGuilds,
    graph: GuildRuntimeGraph,
    dialect: net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect,
    shutdown: RuntimeShutdown,
) {
    val services = plugin.server.servicesManager
    services.register(
        net.lumalyte.lg.api.GuildLookup::class.java,
        net.lumalyte.lg.api.GuildLookupImpl(
            graph.guildService, graph.memberService, graph.rankService, graph.bankService, graph.relationService,
        ),
        plugin,
        ServicePriority.Normal,
    )
    net.lumalyte.lg.infrastructure.services.registerGuildShopXpApi(
        plugin,
        graph.guildShopXpRepository,
        graph.configService,
        graph.progressionService,
        completedSale = { sale, guild -> graph.guildAchievementRuntime.shared(guild, "GUILD_SHOP_SALE", receipt = "market:$sale") },
    )
    services.register(
        net.lumalyte.lg.api.GuildVisualLookup::class.java,
        net.lumalyte.lg.api.GuildVisualLookupImpl(graph.guildService, graph.memberService, graph.rankService),
        plugin,
        ServicePriority.Normal,
    )
    services.register(
        net.lumalyte.lg.api.GuildAllianceLookup::class.java,
        net.lumalyte.lg.api.GuildAllianceLookupImpl(graph.relationRepository),
        plugin,
        ServicePriority.Normal,
    )
    // REQ-121: EnthusiaHolidays grants holiday menu themes through this API.
    services.register(
        net.lumalyte.lg.api.GuildCosmeticUnlocks::class.java,
        net.lumalyte.lg.api.GuildCosmeticUnlocksImpl(graph.guildCosmeticUnlockService),
        plugin,
        ServicePriority.Normal,
    )
    // EnthusiaFriends' Block Everywhere blocks guild invites through this API.
    val durableInviteBlocks = graph.durableInviteBlockService
    shutdown.register(ShutdownStage.GATES, "durable invite blocks") { durableInviteBlocks.close() }
    net.lumalyte.lg.infrastructure.services.registerGuildInviteBlocks(
        plugin, dialect, graph.inviteBlockService, durableInviteBlocks,
    )
}
