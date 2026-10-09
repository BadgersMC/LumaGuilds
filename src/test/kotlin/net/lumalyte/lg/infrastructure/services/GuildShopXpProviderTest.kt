package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildShopXpCompletion
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ProgressionService
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class GuildShopXpProviderTest {
    private val repository = mockk<GuildShopXpRepository>()
    private val progression = mockk<ProgressionService>(relaxed = true)
    private val provider = GuildShopXpProvider(repository, mockk<ConfigService>(relaxed = true), progression)
    private val sale = UUID.randomUUID()
    private val guild = UUID.randomUUID()

    @Test
    fun apiVersionIsTwo() {
        assertEquals(2, provider.apiVersion())
    }

    @Test
    fun replayDoesNotRefireProgression() {
        every { repository.complete(sale) } returns GuildShopXpCompletion(AWARDED_FIVE, guild, 2, awardedNow = false)
        assertEquals(AWARDED_FIVE, provider.complete(sale))
        verify(exactly = 0) { progression.onCommittedExperience(any(), any()) }
    }

    @Test
    fun newAwardRefreshesProgression() {
        every { repository.complete(sale) } returns GuildShopXpCompletion(AWARDED_FIVE, guild, 2, awardedNow = true)
        assertEquals(AWARDED_FIVE, provider.complete(sale))
        verify(exactly = 1) { progression.onCommittedExperience(guild, 2) }
    }

    @Test
    fun abortReturnsTerminalStatus() {
        every { repository.abort(sale) } returns GuildShopXpCompletion(ABORTED_STATUS, guild)
        assertEquals(ABORTED_STATUS, provider.abort(sale))
    }

    private companion object {
        const val AWARDED_FIVE = "AWARDED:5"
        const val ABORTED_STATUS = "ABORTED"
    }
}
