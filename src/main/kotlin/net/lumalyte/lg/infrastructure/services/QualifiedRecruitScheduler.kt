package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ChapterTwoGuildAwardService
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.slf4j.LoggerFactory

class QualifiedRecruitScheduler(
    private val plugin: Plugin,
    private val awardService: ChapterTwoGuildAwardService,
) {
    private val logger = LoggerFactory.getLogger(QualifiedRecruitScheduler::class.java)
    private var task: BukkitTask? = null

    fun start() {
        if (task != null) return
        task = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable {
                try {
                    val awarded = awardService.processQualifiedRecruits()
                    if (awarded > 0) logger.info("Awarded qualified-recruit XP for $awarded retained member(s)")
                } catch (e: Exception) {
                    logger.error("Failed to process qualified recruits", e)
                }
            },
            RUN_INTERVAL_TICKS,
            RUN_INTERVAL_TICKS,
        )
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private companion object {
        const val RUN_INTERVAL_TICKS = 60L * 60L * 20L
    }
}
