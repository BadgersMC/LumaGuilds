package net.lumalyte.lg.infrastructure.services.scheduling

import net.lumalyte.lg.application.services.scheduling.Task
import org.bukkit.scheduler.BukkitRunnable

class TaskBukkit(private val runnable: BukkitRunnable) : Task {
    override fun cancel() {
        runnable.cancel()
    }
}
