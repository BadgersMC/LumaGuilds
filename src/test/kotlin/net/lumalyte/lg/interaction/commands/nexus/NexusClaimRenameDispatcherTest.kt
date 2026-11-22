package net.lumalyte.lg.interaction.commands.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName
import net.lumalyte.lg.application.results.claim.metadata.UpdateClaimNameResult
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.Area
import net.lumalyte.lg.domain.values.Position2D
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-NEXUS-CUTOVER-1: ACF's final String accepts the complete unquoted claim name. */
class NexusClaimRenameDispatcherTest {
    @Test
    fun `unquoted multiword claim name reaches mutation unchanged through the pinned dispatcher`() {
        val player = mockk<Player>(relaxed = true)
        every { player.hasPermission("lumaguilds.command.claim.rename") } returns true
        val source = mockk<CommandSourceStack> { every { sender } returns player }
        val claimId = UUID.randomUUID()
        val partition = Partition(claimId, Area(Position2D(0, 0), Position2D(15, 15)))
        val access = mockk<NexusClaimAccess> {
            every { partitionAtPlayer(player) } returns partition
            every { hasPermission(player, partition, RankPermission.MANAGE_CLAIMS) } returns true
        }
        val update = mockk<UpdateClaimName> {
            every { execute(claimId, "My Guild Home") } returns UpdateClaimNameResult.Success(mockk(relaxed = true))
        }
        val details = mockk<GetClaimDetails> { every { execute(claimId) } returns null }
        val lang = mockk<LangService> {
            every { msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        }
        val dispatcher = claimDispatcher(NexusClaimRenameCommand(lang, access, update, details))

        assertEquals(1, dispatcher.execute("claim rename My Guild Home", source))

        verify(exactly = 1) { update.execute(claimId, "My Guild Home") }
    }
}
