package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankInterestSchedulerThreadingContractTest {
    @Test
    fun `audit pruning is never scheduled on the Bukkit main thread`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/BankInterestScheduler.kt"
        ).readText()

        val startBody = source.substringAfter("fun start()").substringBefore("/** Stops")
        assertTrue(startBody.contains("runTaskTimerAsynchronously"))
        assertTrue(startBody.contains("bankAutomationService.pruneAuditLogs()"))

        val mainTaskBody = startBody
            .substringAfter("scheduledTask = object : BukkitRunnable()")
            .substringBefore("pruneTask = object : BukkitRunnable()")
        assertFalse(mainTaskBody.contains("pruneAuditLogs()"))
    }

    @Test
    fun `both bank scheduler tasks are cancelled on stop`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/BankInterestScheduler.kt"
        ).readText()
        val stopBody = source.substringAfter("fun stop()")
        assertTrue(stopBody.contains("scheduledTask?.cancel()"))
        assertTrue(stopBody.contains("pruneTask?.cancel()"))
    }
}
