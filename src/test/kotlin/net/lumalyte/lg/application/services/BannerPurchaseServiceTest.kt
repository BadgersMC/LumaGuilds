package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.BannerPurchaseRepository
import net.lumalyte.lg.domain.gold.*
import net.lumalyte.lg.infrastructure.persistence.guilds.BannerPurchaseRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.guilds.GuildGoldRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.*

class BannerPurchaseServiceTest {
    @TempDir lateinit var directory: Path

    @Test fun `restart after debit reuses payment and saved banner before delivering once`() {
        val storage = VirtualThreadSQLiteStorage(directory.toFile())
        try {
            val guild = UUID.randomUUID(); val player = UUID.randomUUID()
            val gold = gold(storage)
            gold.creditSystem(UUID.randomUUID(), guild, player, 1_000, GuildGoldRoute.SYSTEM, "seed")
            val sql = BannerPurchaseRepositorySQL(storage)
            val broken = object : BannerPurchaseRepository by sql {
                override fun transition(id: UUID, expected: String, next: String): Boolean {
                    if (next == "PAID") error("lost payment marker")
                    return sql.transition(id, expected, next)
                }
            }
            var delivered = 0
            val first = BannerPurchaseService(broken, gold).purchase(guild, player, 100, "original") { delivered++ }
            assertTrue(first is BannerPurchaseResult.Pending)
            assertEquals(900, gold.balance(guild)); assertEquals(0, delivered)
            val restarted = BannerPurchaseService(BannerPurchaseRepositorySQL(storage), gold(storage))
            val second = restarted.purchase(guild, player, 500, "changed") { assertEquals("original", it); delivered++ }
            assertTrue(second is BannerPurchaseResult.Completed)
            assertEquals(900, gold.balance(guild)); assertEquals(1, delivered)
        } finally { storage.connection.close(5, TimeUnit.SECONDS) }
    }

    @Test fun `uncertain delivery cannot charge or deliver again after restart`() {
        val storage = VirtualThreadSQLiteStorage(directory.toFile())
        try {
            val guild = UUID.randomUUID(); val player = UUID.randomUUID(); val gold = gold(storage)
            gold.creditSystem(UUID.randomUUID(), guild, player, 1_000, GuildGoldRoute.SYSTEM, "seed")
            var deliveries = 0
            val first = BannerPurchaseService(BannerPurchaseRepositorySQL(storage), gold)
                .purchase(guild, player, 100, "banner") { deliveries++; error("inventory changed, reply lost") }
            assertTrue(first is BannerPurchaseResult.Pending)
            val second = BannerPurchaseService(BannerPurchaseRepositorySQL(storage), gold(storage))
                .purchase(guild, player, 100, "banner") { deliveries++ }
            assertEquals(first, second)
            assertEquals(1, deliveries); assertEquals(900, gold.balance(guild))
        } finally { storage.connection.close(5, TimeUnit.SECONDS) }
    }

    private fun gold(storage: VirtualThreadSQLiteStorage) = GuildGoldService(GuildGoldRepositorySQL(storage),
        GuildGoldPolicyProvider { GuildGoldPolicy(1, 100_000, 1.0, 50_000, 0.0, 0.0, 0, 0, 100_000, 100_000, false) },
        GuildGoldCapacityProvider { GuildGoldCapacity(100_000, 0) })
}
