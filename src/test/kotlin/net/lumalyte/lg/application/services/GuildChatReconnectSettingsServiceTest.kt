package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildChatReconnectSettingsRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guild authority and rendered preference decisions are enforced at the service boundary. */
internal class GuildChatReconnectSettingsServiceTest {
    private val repository = mockk<GuildChatReconnectSettingsRepository>()
    private val guilds = mockk<GuildService>()
    private val service = GuildChatReconnectSettingsService(repository, guilds)
    private val guild = Guild(UUID.randomUUID(), "Reconnect", createdAt = Instant.now())
    private val actor = UUID.randomUUID()

    /** Missing guilds cannot save preferences. */
    @Test
    fun missingGuild() {
        every { guilds.getGuild(guild.id) } returns null
        assertFalse(service.apply(guild.id, false, true, actor))
        verify(exactly = 0) { repository.compareAndSet(any(), any(), any()) }
    }

    /** Revoked permission prevents writes even if the form was previously authorized. */
    @Test
    fun revokedAuthority() {
        authorize(false)
        assertFalse(service.apply(guild.id, false, true, actor))
        verify(exactly = 0) { repository.compareAndSet(any(), any(), any()) }
    }

    /** Current managers delegate an atomic expected-state write. */
    @Test
    fun authorizedChange() {
        authorize(true)
        every { repository.resetOnJoin(guild.id) } returns false
        every { repository.compareAndSet(guild.id, false, true) } returns true
        assertTrue(service.apply(guild.id, false, true, actor))
    }

    /** Submitting unchanged fields never overwrites a newer setting. */
    @Test
    fun unchangedForm() {
        authorize(true)
        assertTrue(service.apply(guild.id, false, false, actor))
        verify(exactly = 0) { repository.compareAndSet(any(), any(), any()) }
    }

    /** A concurrent identical choice is successful without another write. */
    @Test
    fun alreadyApplied() {
        authorize(true)
        every { repository.resetOnJoin(guild.id) } returns true
        assertTrue(service.apply(guild.id, false, true, actor))
        verify(exactly = 0) { repository.compareAndSet(any(), any(), any()) }
    }

    /** Reset policy considers only actual guild memberships. */
    @Test
    fun memberPolicy() {
        every { guilds.getPlayerGuilds(actor) } returns setOf(guild)
        every { repository.resetOnJoin(guild.id) } returns true
        assertTrue(service.shouldReset(actor))
        every { guilds.getPlayerGuilds(actor) } returns emptySet()
        assertFalse(service.shouldReset(actor))
    }

    private fun authorize(allowed: Boolean) {
        every { guilds.getGuild(guild.id) } returns guild
        every { guilds.hasPermission(actor, guild.id, RankPermission.MANAGE_GUILD_SETTINGS) } returns allowed
    }
}
