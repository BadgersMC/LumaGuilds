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

/** Verifies level changes reach guild Discord-role reconciliation. */
internal class GuildDiscordRoleListenerTest {
    @Test
    fun reconcilesWhenGuildLevelChanges() {
        val guildId = UUID.randomUUID()
        val service = mockk<GuildDiscordRoleService>()
        every { service.reconcileGuild(guildId) } returns
            CompletableFuture.completedFuture(DiscordGuildRoleSyncSummary())

        GuildDiscordRoleListener(service).onGuildLevelChanged(GuildLevelChangedEvent(guildId, 50))

        verify(exactly = 1) { service.reconcileGuild(guildId) }
    }
}
