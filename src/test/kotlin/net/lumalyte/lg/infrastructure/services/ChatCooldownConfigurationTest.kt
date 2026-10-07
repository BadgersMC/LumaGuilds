package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.values.ChatRateLimit
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies reloadable chat cooldowns independently of hourly safeguards. */
internal class ChatCooldownConfigurationTest {
    private val playerId = UUID.randomUUID()
    private val repository = mockk<ChatSettingsRepository>()
    private val configService = mockk<ConfigService>()
    private val config = MainConfig()
    private val service =
        ChatServiceBukkit(
            repository, mockk(), mockk(), mockk(), mockk(), mockk(), configService,
            mockk(), mockk(), mockk(), mockk(), mockk(),
        )

    init {
        every { configService.loadConfig() } returns config
    }

    /** The configured announcement interval exceeds ten minutes. */
    @Test
    fun announcementUsesConfig() {
        every { repository.getRateLimit(playerId) } returns recent(TEN_MINUTES)
        assertTrue(service.isAnnouncementRateLimited(playerId))
    }

    /** The configured ping interval exceeds two minutes. */
    @Test
    fun pingUsesConfig() {
        every { repository.getRateLimit(playerId) } returns recent(2)
        assertTrue(service.isPingRateLimited(playerId))
    }

    /** Configuration changes take effect on the same instance. */
    @Test
    fun reloadChangesCooldowns() {
        every { repository.getRateLimit(playerId) } returns recent(2)
        config.chat.announceCooldownMinutes = 1
        config.chat.pingCooldownMinutes = 1
        assertFalse(service.isAnnouncementRateLimited(playerId))
        assertFalse(service.isPingRateLimited(playerId))
        config.chat.announceCooldownMinutes = FIFTEEN_MINUTES
        config.chat.pingCooldownMinutes = FIFTEEN_MINUTES
        assertTrue(service.isAnnouncementRateLimited(playerId))
        assertTrue(service.isPingRateLimited(playerId))
    }

    /** Disabling a time gate preserves the hourly safeguards. */
    @Test
    fun disabledTimeKeepsHourlyCaps() {
        config.chat.announceCooldownMinutes = 0
        config.chat.pingCooldownMinutes = -1
        every { repository.getRateLimit(playerId) } returns recent(0)
        assertFalse(service.isAnnouncementRateLimited(playerId))
        assertFalse(service.isPingRateLimited(playerId))
        every { repository.getRateLimit(playerId) } returns recent(0).copy(
            announceCount = ANNOUNCEMENTS_PER_HOUR,
            pingCount = PINGS_PER_HOUR,
        )
        assertTrue(service.isAnnouncementRateLimited(playerId))
        assertTrue(service.isPingRateLimited(playerId))
    }

    /** Expired counters do not block subsequent messages. */
    @Test
    fun expiredWindowsAllowSending() {
        every { repository.getRateLimit(playerId) } returns recent(TWO_HOURS).copy(
            announceCount = ANNOUNCEMENTS_PER_HOUR,
            pingCount = PINGS_PER_HOUR,
        )
        assertFalse(service.isAnnouncementRateLimited(playerId))
        assertFalse(service.isPingRateLimited(playerId))
    }

    private fun recent(minutes: Long): ChatRateLimit {
        val timestamp = System.currentTimeMillis() - minutes * MILLIS_PER_MINUTE
        return ChatRateLimit(playerId, lastAnnounceTime = timestamp, lastPingTime = timestamp)
    }

    private companion object {
        const val TEN_MINUTES = 10L
        const val FIFTEEN_MINUTES = 15
        const val TWO_HOURS = 120L
        const val MILLIS_PER_MINUTE = 60_000L
        const val ANNOUNCEMENTS_PER_HOUR = 3
        const val PINGS_PER_HOUR = 10
    }
}
