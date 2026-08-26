package net.lumalyte.lg.interaction.commands

import co.aikar.commands.annotation.CommandCompletion
import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.utils.GuildResolver
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class GuildBalanceCommandContractTest {
    @Test
    fun `balance command completes every guild name`() {
        val guildService = mockk<GuildService>()
        every { guildService.getAllGuilds() } returns setOf(
            guild("Zephyr"),
            guild("Badgers"),
            guild("Aurora")
        )
        val balanceMethod = GuildCommand::class.java.declaredMethods.single { it.name == "onBalance" }

        assertEquals("@guilds", balanceMethod.getAnnotation(CommandCompletion::class.java).value)
        assertEquals(listOf("Aurora", "Badgers", "Zephyr"), GuildResolver.suggestions(guildService))
    }

    private fun guild(name: String): Guild = Guild(
        id = UUID.randomUUID(),
        name = name,
        createdAt = Instant.EPOCH
    )
}
