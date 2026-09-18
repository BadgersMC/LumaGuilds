package net.lumalyte.lg.application.services

import io.mockk.*
import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class GuildRewardPurchaseServiceTest {
    private val actor = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val catalog = RewardCatalog.chapterTwo()
    private val reads = mockk<GuildRewardService>()
    private val gold = mockk<GuildGoldService>()
    private var enabled = true
    private var allowed = true
    private val service = GuildRewardPurchaseService(reads, gold, { enabled }, { _, _ -> allowed })

    private fun ready() {
        every { reads.read(guild) } returns GuildRewardRead.Available(100, 7,
            RewardEntitlementResolver(catalog).resolve(100, RewardOwnership()))
    }

    @Test fun `quote uses current server price and version without spending`() {
        ready()
        val quote = assertNotNull(service.quote(actor, guild, "bank-1"))
        assertEquals(actor, quote.actorId)
        assertEquals(guild, quote.guildId)
        assertEquals(100L, quote.quotedPrice)
        assertEquals(7L, quote.expectedVersion)
        assertNotEquals(quote.transactionId, service.quote(actor, guild, "bank-1")!!.transactionId)
        verify { gold wasNot Called }
    }

    @Test fun `disabled and unauthorized quoting never reads storage`() {
        enabled = false
        assertNull(service.quote(actor, guild, "bank-1"))
        enabled = true
        allowed = false
        assertNull(service.quote(actor, guild, "bank-1"))
        verify { reads wasNot Called; gold wasNot Called }
    }

    @Test fun `missing locked and owned offers cannot be quoted`() {
        every { reads.read(guild) } returns GuildRewardRead.Unavailable
        assertNull(service.quote(actor, guild, "bank-1"))
        every { reads.read(guild) } returns GuildRewardRead.Available(1, 0,
            RewardEntitlementResolver(catalog).resolve(1, RewardOwnership()))
        assertNull(service.quote(actor, guild, "bank-1"))
        ready()
        assertNull(service.quote(actor, guild, "unknown"))
        every { reads.read(guild) } returns GuildRewardRead.Available(100, 7,
            RewardEntitlementResolver(catalog).resolve(100, RewardOwnership(currentRun = setOf("bank-1"))))
        assertNull(service.quote(actor, guild, "bank-1"))
    }

    @Test fun `gate reload and wrong actor prevent confirmation`() {
        ready()
        val quote = service.quote(actor, guild, "bank-1")!!
        enabled = false
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAVAILABLE), service.confirm(actor, quote))
        enabled = true
        assertEquals(RewardPurchaseResult.Rejected(RewardPurchaseRejection.UNAUTHORIZED), service.confirm(UUID.randomUUID(), quote))
        verify { gold wasNot Called }
    }

    @Test fun `unknown outcome retries the exact request without rereading ownership`() {
        ready()
        val quote = service.quote(actor, guild, "bank-1")!!
        val applied = RewardPurchaseResult.Applied(quote.transactionId, "bank-1", 100, 1000, 900, 8)
        every { gold.purchaseReward(quote) } returnsMany listOf(RewardPurchaseResult.Failed(quote.transactionId), applied, applied)
        assertIs<RewardPurchaseResult.Failed>(service.confirm(actor, quote))
        assertEquals(applied, service.confirm(actor, quote))
        assertEquals(applied, service.confirm(actor, quote))
        verify(exactly = 1) { reads.read(guild) }
        verify(exactly = 3) { gold.purchaseReward(quote) }
    }
}
