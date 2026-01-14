package net.lumalyte.lg.infrastructure.hytale.services

import com.hypixel.hytale.server.core.HytaleServer
import net.lumalyte.lg.application.services.scheduling.SchedulerService
import net.lumalyte.lg.application.services.scheduling.Task
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class HytaleSchedulerService : SchedulerService {

    override fun executeOnMain(task: () -> Unit) {
        // Use Hytale's global scheduled executor
        HytaleServer.SCHEDULED_EXECUTOR.execute(task)
    }

    override fun schedule(delayTicks: Long, task: () -> Unit): Task {
        // Hytale uses milliseconds, 1 tick = 50ms (20 TPS)
        val delayMs = delayTicks * 50

        val future = HytaleServer.SCHEDULED_EXECUTOR.schedule({
            executeOnMain(task)
        }, delayMs, TimeUnit.MILLISECONDS)

        return object : Task {
            override fun cancel() {
                future.cancel(false)
            }
        }
    }
}
