package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.config.MainConfig
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BankDepositXpQuotesTest {
    @Test fun `quest revision is captured before the quote timestamp`() {
        val config = mockk<ConfigService>()
        val activity = mockk<PlaytimeActivityService>()
        val actor = UUID.randomUUID()
        every { config.loadConfig() } returns MainConfig()
        every { activity.isXpBlocked(actor) } returns false
        var revision = 5L
        val quotes = BankDepositXpQuotes(config, activity,
            clock = { revision = 6L; Instant.parse("2026-10-29T00:00:00Z") },
            questRevision = { revision },
        )
        assertEquals(5L, quotes.capture(actor).questRevision)
    }

    @Test fun `a failed revision lookup cannot silently produce a quote`() {
        val quotes = BankDepositXpQuotes(mockk(), mockk(), questRevision = { error("revision unavailable") })
        assertFailsWith<IllegalStateException> { quotes.capture(UUID.randomUUID()) }
    }
}
