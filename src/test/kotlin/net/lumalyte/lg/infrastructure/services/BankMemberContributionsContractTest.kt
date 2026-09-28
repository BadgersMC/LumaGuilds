package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BankMemberContributionsContractTest {
    private val source = Paths.get(
        "src/main/kotlin/net/lumalyte/lg/infrastructure/services/BankServiceBukkit.kt"
    ).toFile().readText()

    @Test
    fun contributionViewReadsGuildTransactionsOnceAndIncludesWithdrawalFees() {
        val function = source.substringAfter("override fun getMemberContributions")
            .substringBefore("override fun getPlayerDeposits")
        assertEquals(1, "getTransactionsForGuild\\(guildId\\)".toRegex().findAll(function).count())
        assertTrue(function.contains("it.amount + it.fee"))
        assertTrue(function.contains("Bukkit.getOfflinePlayer(playerId).name"))
    }
}
