package net.lumalyte.lg.di.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.AuditRepository
import net.lumalyte.lg.application.persistence.GuildBannerRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.infrastructure.services.AuditServiceBukkit
import net.lumalyte.lg.infrastructure.services.GuildBannerServiceBukkit
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildCoreConstructorInjectionTest {
    @Test
    fun `audit service writes through an explicitly injected repository`() {
        val repository = mockk<AuditRepository>()
        every { repository.save(any()) } returns true

        val service = AuditServiceBukkit(repository)
        assertTrue(service.recordAction(UUID.randomUUID(), "TEST", null, null, null))
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `guild banner service rejects unknown guild without Koin`() {
        val guilds = mockk<GuildRepository>()
        val banners = mockk<GuildBannerRepository>()
        val guildId = UUID.randomUUID()
        every { guilds.getById(guildId) } returns null

        val service = GuildBannerServiceBukkit(banners, guilds)
        assertFalse(service.canSetBanners(guildId))
        verify(exactly = 1) { guilds.getById(guildId) }
    }
}
