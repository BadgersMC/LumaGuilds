package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class GuildPrestigeAnnouncementTest {
    private val actor = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val quote = PrestigeQuote(UUID.randomUUID(), guild, actor, "bank-1", 100, 1, 0)
    private val applied = PrestigeResult.Applied(quote.transactionId, "bank-1", 100, 1000, 900, 2, 1, 10000, 2)
    private val repository = mockk<PrestigeRepository>()
    private val progression = mockk<ProgressionRepository>(relaxed = true)
    private val announcements = mutableListOf<Pair<UUID, Int>>()

    private fun service(sender: GuildPrestigeAnnouncement = GuildPrestigeAnnouncement { id, tier ->
        announcements += id to tier
    }): GuildPrestigeService = GuildPrestigeService(
        mockk(), progression, RewardCatalog.chapterTwo(), repository, mockk(), mockk<ConfigService>().also {
            every { it.loadConfig() } returns MainConfig()
        }, sender,
    )

    @Test
    fun `new commit announces once while receipt retries are silent`() {
        every { repository.confirm(quote, any()) } returns applied
        every { repository.confirmWithCommitStatus(quote, any()) } returnsMany listOf(
            PrestigeConfirmation(applied, true), PrestigeConfirmation(applied),
        )
        val subject = service()
        assertEquals(applied, subject.confirm(actor, quote))
        assertEquals(applied, subject.confirm(actor, quote))
        assertEquals(listOf(guild to 1), announcements)
    }

    @Test
    fun `sender failure preserves successful settlement and is not retried`() {
        every { repository.confirm(quote, any()) } returns applied
        every { repository.confirmWithCommitStatus(quote, any()) } returnsMany listOf(
            PrestigeConfirmation(applied, true), PrestigeConfirmation(applied),
        )
        var calls = 0
        val subject = service(GuildPrestigeAnnouncement { _, _ -> calls++; error("sender unavailable") })
        assertEquals(applied, subject.confirm(actor, quote))
        assertEquals(applied, subject.confirm(actor, quote))
        assertEquals(1, calls)
    }

    @Test
    fun `failure and rejection never announce`() {
        val rejected = PrestigeResult.Rejected(PrestigeRejection.INSUFFICIENT_FUNDS)
        val failed = PrestigeResult.Failed(quote.transactionId)
        every { repository.confirm(quote, any()) } returnsMany listOf(rejected, failed)
        every { repository.confirmWithCommitStatus(quote, any()) } returnsMany listOf(
            PrestigeConfirmation(rejected), PrestigeConfirmation(failed),
        )
        val subject = service()
        assertEquals(rejected, subject.confirm(actor, quote))
        assertEquals(failed, subject.confirm(actor, quote))
        assertEquals(emptyList(), announcements)
    }
}
