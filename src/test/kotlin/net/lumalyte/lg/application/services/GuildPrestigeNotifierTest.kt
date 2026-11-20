package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-153: one completion per newly committed prestige; replays, rejections and failures stay silent. */
class GuildPrestigeNotifierTest {
    private val actor = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val quote = PrestigeQuote(UUID.randomUUID(), guild, actor, "bank-1", 100, 1, 2)
    private val applied = PrestigeResult.Applied(quote.transactionId, "bank-1", 100, 1000, 900, 2, 3, 10000, 4)
    private val committedAt = Instant.ofEpochMilli(1_730_000_000_000L)
    private val record = PrestigeRecord(quote.transactionId, guild, actor, 3, "bank-1", committedAt)
    private val repository = mockk<PrestigeRepository>()
    private val history = mockk<PrestigeHistory>()
    private val progression = mockk<ProgressionRepository>(relaxed = true)
    private val announcements = mutableListOf<Pair<UUID, Int>>()

    private fun service(vararg notifiers: GuildPrestigeNotifier) = GuildPrestigeService(
        mockk(), progression, RewardCatalog.chapterTwo(), repository, mockk(),
        mockk<ConfigService>().also { every { it.loadConfig() } returns MainConfig() },
        GuildPrestigeAnnouncement { id, tier -> announcements += id to tier },
        history,
        notifiers.toList(),
    )

    private fun commitsOnceThenReplays() {
        every { repository.confirm(quote, any()) } returns applied
        every { repository.confirmWithCommitStatus(quote, any()) } returnsMany listOf(
            PrestigeConfirmation(applied, true), PrestigeConfirmation(applied),
        )
    }

    @Test
    fun `new commit publishes one completion with history time and server-first flag`() {
        commitsOnceThenReplays()
        every { history.history(guild) } returns listOf(record)
        every { history.firstToReach(3) } returns record
        val seen = mutableListOf<PrestigeCompletion>()
        val subject = service(GuildPrestigeNotifier { seen += it })

        assertEquals(applied, subject.confirm(actor, quote))
        assertEquals(applied, subject.confirm(actor, quote))

        assertEquals(
            listOf(PrestigeCompletion(quote.transactionId, guild, actor, 3, "bank-1", committedAt, true)),
            seen,
        )
    }

    @Test
    fun `a later guild reaching the same tier is not first`() {
        commitsOnceThenReplays()
        every { history.history(guild) } returns listOf(record)
        every { history.firstToReach(3) } returns record.copy(transactionId = UUID.randomUUID(), guildId = UUID.randomUUID())
        val seen = mutableListOf<PrestigeCompletion>()
        service(GuildPrestigeNotifier { seen += it }).confirm(actor, quote)
        assertEquals(false, seen.single().firstToReachTier)
    }

    @Test
    fun `a failing notifier does not stop others, the announcement or the result`() {
        commitsOnceThenReplays()
        every { history.history(guild) } returns listOf(record)
        every { history.firstToReach(3) } returns record
        var later = 0
        val subject = service(GuildPrestigeNotifier { error("listener broke") }, GuildPrestigeNotifier { later++ })
        assertEquals(applied, subject.confirm(actor, quote))
        assertEquals(1, later)
        assertEquals(listOf(guild to 3), announcements)
    }

    @Test
    fun `rejections and failures publish nothing`() {
        val rejected = PrestigeResult.Rejected(PrestigeRejection.ACTIVE_WAR)
        val failed = PrestigeResult.Failed(quote.transactionId)
        every { repository.confirm(quote, any()) } returnsMany listOf(rejected, failed)
        every { repository.confirmWithCommitStatus(quote, any()) } returnsMany listOf(
            PrestigeConfirmation(rejected), PrestigeConfirmation(failed),
        )
        val seen = mutableListOf<PrestigeCompletion>()
        val subject = service(GuildPrestigeNotifier { seen += it })
        subject.confirm(actor, quote)
        subject.confirm(actor, quote)
        assertTrue(seen.isEmpty())
    }
}
