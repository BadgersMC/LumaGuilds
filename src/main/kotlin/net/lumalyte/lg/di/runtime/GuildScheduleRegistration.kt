package net.lumalyte.lg.di.runtime

import co.aikar.idb.Database
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.BankInterestScheduler
import net.lumalyte.lg.infrastructure.services.ChapterRatedWarReconciliationGate
import net.lumalyte.lg.infrastructure.services.ChapterRolloverScheduler
import net.lumalyte.lg.infrastructure.services.DailyWarCostsScheduler
import net.lumalyte.lg.infrastructure.services.ExperienceTransactionCleanupScheduler
import net.lumalyte.lg.infrastructure.services.QualifiedRecruitScheduler
import net.lumalyte.lg.interaction.menus.bedrock.FormStateManager
import java.util.logging.Level

/**
 * Starts LumaGuilds' managed tasks once services, integrations, listeners and commands exist.
 *
 * Bukkit timers that mutate world/entity state stay on the server thread (`runTaskTimer`);
 * Nexus-owned coroutine cleanup uses the context's virtual-thread dispatcher. Each start records
 * its stop; persistence flushes are recorded in [ShutdownStage.FLUSH] so they finish before the
 * Nexus context and storage close.
 */
internal class GuildScheduleRegistration(
    private val plugin: LumaGuilds,
    private val graph: GuildRuntimeGraph,
    private val storage: Storage<Database>,
    private val shutdown: RuntimeShutdown,
) {
    private val scheduler get() = plugin.server.scheduler
    private val logger get() = plugin.logger

    fun start() {
        // Registered first so it runs last in TASKS: anything not stopped explicitly is cancelled.
        shutdown.register(ShutdownStage.TASKS, "remaining Bukkit tasks") { scheduler.cancelTasks(plugin) }
        startManagedCleanup()
        startVault()
        startProjections()
        startGuildRuntime()
        repairStartupState()
        startMaintenance()
        startWebApi()
        startDiscordReconciliation()
    }

    private fun startManagedCleanup() {
        // Child of the Nexus scope; shutdown() cancels/joins only this job and clears form state.
        FormStateManager.start(graph.scope, graph.virtualDispatcher)
        shutdown.register(ShutdownStage.FLUSH, "Bedrock form state") { FormStateManager.shutdown() }
        val formCache = graph.formCacheService
        shutdown.register(ShutdownStage.FLUSH, "Bedrock form cache") { formCache.shutdown() }
        graph.guildRolePermissionResolver?.let { resolver ->
            shutdown.register(ShutdownStage.FLUSH, "guild role permission resolver") { resolver.shutdown() }
        }
    }

    private fun startVault() {
        val autoSave = graph.vaultAutoSaveService
        autoSave.start()
        // stop() cancels its tasks and performs the final blocking vault save.
        shutdown.register(ShutdownStage.FLUSH, "vault auto-save") { autoSave.stop() }

        val backups = graph.vaultBackupService
        backups.startAutoBackup(intervalMinutes = 60)
        shutdown.register(ShutdownStage.TASKS, "vault backups") { backups.stopAutoBackup() }
        plugin.logColored("✓ Vault auto-backup started (interval: 60 minutes)")

        graph.guildVaultService.restoreAllVaultChests()
        val holograms = graph.vaultHologramService
        holograms.start()
        shutdown.register(ShutdownStage.TASKS, "vault holograms") { holograms.stop() }
        holograms.recreateAllHolograms()
    }

    private fun startProjections() {
        val spawnBanners = graph.spawnBannerService
        val projection = graph.guildLeaderboardProjectionService
        projection.start { changed -> changed.forEach { key -> spawnBanners.refreshLeaderboard(key.type, key.period) } }
        shutdown.register(ShutdownStage.INTEGRATIONS, "guild leaderboard projection") { projection.stop() }
        spawnBanners.start()
        shutdown.register(ShutdownStage.INTEGRATIONS, "spawn banner refresh") { spawnBanners.stop() }

        val bannermanRenderer = graph.bannermanRenderService
        net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanTickTask(
            plugin,
            bannermanRenderer,
            graph.bannermanListeners::trySpawn, // respawn banners despawned for swim/elytra poses
        ).start()
        bannermanRenderer.sweepOrphans()
        shutdown.register(ShutdownStage.FLUSH, "bannerman displays") { bannermanRenderer.despawnAll() }
    }

    private fun startGuildRuntime() {
        try {
            val weeklyQuests = graph.weeklyQuestCoordinator
            weeklyQuests.start(plugin)
            shutdown.register(ShutdownStage.INTEGRATIONS, "weekly quest coordinator") { weeklyQuests.stop() }
            plugin.logColored("✓ Weekly quest coordinator started")
        } catch (e: Exception) {
            plugin.logColored("❌ Failed to start weekly quest coordinator: ${e.message}")
        }
        graph.guildRecruitmentListener.start()
        val achievements = graph.guildAchievementRuntime
        achievements.start()
        shutdown.register(ShutdownStage.GATES, "guild achievement runtime") { achievements.stop() }
    }

    private fun repairStartupState() {
        val warService = graph.warService
        val recovered = warService.reconcilePendingKillVictories()
        if (recovered > 0) plugin.logColored("✓ Recovered $recovered decisive war kill(s) after restart")
        try {
            // Repair Guild.level drift from XP (one-shot; cheap if already in sync).
            val synced = graph.progressionService.syncGuildLevels()
            if (synced > 0) plugin.logColored("✓ Synced $synced guild level(s) from stored XP")
        } catch (e: Exception) {
            logger.warning("Failed to sync guild levels from XP: ${e.message}")
        }
    }

    private fun startMaintenance() {
        val warService = graph.warService
        scheduler.runTaskTimer(plugin, Runnable {
            try {
                val processed = warService.processExpiredWars()
                if (processed > 0) logger.info("War maintenance processed $processed expired/recoverable record(s)")
            } catch (error: Exception) {
                logger.log(Level.SEVERE, "War maintenance failed", error)
            }
        }, 20L * 60L, 20L * 60L)

        guarded("daily war costs scheduler") {
            val daily = DailyWarCostsScheduler(plugin, graph.dailyWarCostsService)
            daily.startDailyScheduler()
            shutdown.register(ShutdownStage.TASKS, "daily war costs") { daily.stopDailyScheduler() }
        }
        guarded("bank interest scheduler") {
            val interest = BankInterestScheduler(plugin, graph.bankAutomationService)
            interest.start()
            shutdown.register(ShutdownStage.TASKS, "bank interest") { interest.stop() }
            val gold = graph.guildGoldService
            val bankXp = graph.bankDepositXpDeliveryService
            scheduler.runTaskTimer(plugin, Runnable {
                try {
                    gold.reconcilePending(System.currentTimeMillis() - 300_000L)
                } catch (error: Exception) {
                    logger.severe("Guild gold recovery failed; unfinished transfers remain held: ${error.message}")
                }
                // Already-credited deposits must still drain if another currency transfer is held or fails.
                try {
                    bankXp.drain()
                } catch (error: Exception) {
                    logger.warning("Bank XP delivery remains queued: ${error.message}")
                }
                try {
                    bankXp.retryCache()
                } catch (error: Exception) {
                    logger.warning("Bank XP cache publication remains queued: ${error.message}")
                }
            }, 1L, 1_200L)
        }
        guarded("qualified recruit scheduler") {
            val recruits = QualifiedRecruitScheduler(plugin, graph.chapterTwoGuildAwardService)
            recruits.start()
            shutdown.register(ShutdownStage.TASKS, "qualified recruits") { recruits.stop() }
        }
        guarded("experience transaction cleanup scheduler") {
            val progression = graph.configService.loadConfig().progression
            val cleanup = ExperienceTransactionCleanupScheduler(
                plugin,
                graph.progressionRepository,
                progression.transactionRetentionDays,
                progression.transactionCleanupIntervalHours,
            )
            cleanup.start()
            shutdown.register(ShutdownStage.TASKS, "experience transaction cleanup") { cleanup.stop() }
        }
        guarded("relation maintenance scheduler") {
            // Server thread: truce expiry fires Bukkit events.
            val relations = graph.relationService
            scheduler.runTaskTimer(plugin, Runnable {
                try {
                    val expired = relations.processExpiredRelations()
                    val cleaned = relations.cleanupStaleRelations()
                    if (expired > 0 || cleaned > 0) logger.info("Relation maintenance: expired=$expired, cleaned=$cleaned")
                } catch (e: Exception) {
                    logger.warning("Relation maintenance run failed: ${e.message}")
                }
            }, 20L * 60L, 20L * 60L * 5L)
        }
        val rollover = ChapterRolloverScheduler(
            plugin,
            storage,
            ChapterRatedWarReconciliationGate(graph.warRepository, graph.seasonalEloCoordinator),
        )
        rollover.start()
        shutdown.register(ShutdownStage.TASKS, "chapter rollover") { rollover.stop() }
    }

    private fun startWebApi() {
        try {
            val web = graph.webApiServer
            web.start()
            shutdown.register(ShutdownStage.SERVICES, "web API") { web.stop() }
        } catch (e: Exception) {
            logger.warning("Failed to start Web API server: ${e.message}")
        }
    }

    /** Startup reconciliation plus a five-minute repair timer for transient DiscordSRV outages. */
    private fun startDiscordReconciliation() {
        if (!graph.configService.loadConfig().discordGuildRoles.enabled) return
        val gateway = graph.discordGuildRoleGateway
        val roles = graph.guildDiscordRoleService
        if (!gateway.isAvailable()) {
            logger.info("DiscordSRV is not ready yet; guild-role reconciliation will run when DiscordReadyEvent fires")
        } else {
            roles.reconcileAll().whenComplete { result, error ->
                if (error != null) {
                    logger.warning("Discord guild-role startup reconciliation failed: ${error.message}")
                } else {
                    plugin.logColored(
                        "✓ Discord guild roles reconciled: ${result.guildsReconciled} guild(s), " +
                            "${result.rolesCreated} role(s) created, ${result.memberRolesApplied} member role(s) applied, " +
                            "${result.failures} failure(s)",
                    )
                }
            }
        }
        scheduler.runTaskTimer(plugin, Runnable {
            if (!gateway.isAvailable()) return@Runnable
            roles.reconcileAll().whenComplete { result, error ->
                if (error != null) {
                    logger.warning("Discord guild-role periodic reconciliation failed: ${error.message}")
                } else if (result.failures > 0) {
                    logger.warning("Discord guild-role periodic reconciliation completed with ${result.failures} failure(s)")
                }
            }
        }, 20L * 60L, 20L * 60L * 5L)
    }

    /** A failed optional scheduler is logged and does not prevent startup, as before. */
    private inline fun guarded(name: String, block: () -> Unit) {
        try {
            block()
            plugin.logColored("✓ ${name.replaceFirstChar { it.uppercase() }} started")
        } catch (e: Exception) {
            plugin.logColored("❌ Failed to initialize $name: ${e.message}")
        }
    }
}
