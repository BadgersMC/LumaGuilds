package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.api.events.GuildLevelChangedEvent
import net.lumalyte.lg.application.services.DiscordGuildRoleSyncSummary
import net.lumalyte.lg.application.services.GuildDiscordRoleService
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CompletableFuture

private const val ROLE_LEVEL = 50

/** Verifies level changes reach guild Discord-role reconciliation. */
internal class GuildDiscordRoleListenerTest {
    /** A level change asks the service to reconcile the affected guild. */
    @Test
    fun reconcilesGuildLevelChange() {
        val guildId = UUID.randomUUID()
        val service = mockk<GuildDiscordRoleService>()
        every { service.reconcileGuild(guildId) } returns
            CompletableFuture.completedFuture(DiscordGuildRoleSyncSummary())

        GuildDiscordRoleListener(service).onGuildLevelChanged(GuildLevelChangedEvent(guildId, ROLE_LEVEL))

        verify(exactly = 1) { service.reconcileGuild(guildId) }
    }
}
