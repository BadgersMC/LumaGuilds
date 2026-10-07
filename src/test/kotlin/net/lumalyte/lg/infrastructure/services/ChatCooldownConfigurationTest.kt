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

class ChatCooldownConfigurationTest {
    private val playerId = UUID.randomUUID()
    private val repository = mockk<ChatSettingsRepository>()
    private val configService = mockk<ConfigService>()
    private val config = MainConfig()
    private val service = ChatServiceBukkit(
        repository, mockk(), mockk(), mockk(), mockk(), mockk(), configService,
        mockk(), mockk(), mockk(), mockk(), mockk(),
    )

    init {
        every { configService.loadConfig() } returns config
    }

    @Test
    fun `configured thirty minute announcement cooldown blocks at ten minutes`() {
        every { repository.getRateLimit(playerId) } returns recent(10)
        assertTrue(service.isAnnouncementRateLimited(playerId))
    }

    @Test
    fun `configured five minute ping cooldown blocks at two minutes`() {
        every { repository.getRateLimit(playerId) } returns recent(2)
        assertTrue(service.isPingRateLimited(playerId))
    }

    @Test
    fun `changed cooldowns are read without recreating service`() {
        every { repository.getRateLimit(playerId) } returns recent(2)
        config.chat.announceCooldownMinutes = 1
        config.chat.pingCooldownMinutes = 1
        assertFalse(service.isAnnouncementRateLimited(playerId))
        assertFalse(service.isPingRateLimited(playerId))
        config.chat.announceCooldownMinutes = 15
        config.chat.pingCooldownMinutes = 15
        assertTrue(service.isAnnouncementRateLimited(playerId))
        assertTrue(service.isPingRateLimited(playerId))
    }

    @Test
    fun `disabled cooldowns still enforce hourly limits`() {
        config.chat.announceCooldownMinutes = 0
        config.chat.pingCooldownMinutes = -1
        every { repository.getRateLimit(playerId) } returns recent(0)
        assertFalse(service.isAnnouncementRateLimited(playerId))
        assertFalse(service.isPingRateLimited(playerId))
        every { repository.getRateLimit(playerId) } returns recent(0).copy(announceCount = 3, pingCount = 10)
        assertTrue(service.isAnnouncementRateLimited(playerId))
        assertTrue(service.isPingRateLimited(playerId))
    }

    @Test
    fun `expired cooldowns and hourly windows allow sending`() {
        every { repository.getRateLimit(playerId) } returns recent(120).copy(announceCount = 3, pingCount = 10)
        assertFalse(service.isAnnouncementRateLimited(playerId))
        assertFalse(service.isPingRateLimited(playerId))
    }

    private fun recent(minutes: Long): ChatRateLimit {
        val timestamp = System.currentTimeMillis() - minutes * 60_000L
        return ChatRateLimit(playerId, lastAnnounceTime = timestamp, lastPingTime = timestamp)
    }
}
