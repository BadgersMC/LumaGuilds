package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RelationType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Behaviour specification for [RoseChatCleanupListener.shouldLeaveChannel].
 *
 * The decision answers: given a player's current LumaGuilds status, may they remain
 * in the RoseChat channel they are currently in? The listener uses it to move stale
 * players back to the default channel.
 */
class RoseChatCleanupListenerTest {

    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>(relaxed = true)
    private val partyService = mockk<PartyService>()
    private val relationService = mockk<RelationService>()

    private val listener = RoseChatCleanupListener(guildService, memberService, partyService, relationService)

    private val playerId = UUID.randomUUID()

    private fun guild(id: UUID = UUID.randomUUID()) = Guild(id = id, name = "G", createdAt = Instant.now())

    private fun activeAlly(ownerGuild: UUID) = Relation.create(
        guildA = ownerGuild, guildB = UUID.randomUUID(),
        type = RelationType.ALLY, status = RelationStatus.ACTIVE
    )

    // --- fixed 'guild' channel ------------------------------------------

    @Test
    fun `player in guild channel stays while they have a guild`() {
        every { guildService.getPlayerGuilds(playerId) } returns setOf(guild())
        assertFalse(listener.shouldLeaveChannel(playerId, "guild"))
    }

    @Test
    fun `player in guild channel leaves once they have no guild`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        assertTrue(listener.shouldLeaveChannel(playerId, "guild"))
    }

    // --- fixed 'guild-ally' channel -------------------------------------

    @Test
    fun `player in ally channel stays while their guild has an active alliance`() {
        val g = guild()
        every { guildService.getPlayerGuilds(playerId) } returns setOf(g)
        every { relationService.getGuildRelationsByType(g.id, RelationType.ALLY) } returns setOf(activeAlly(g.id))
        assertFalse(listener.shouldLeaveChannel(playerId, "guild-ally"))
    }

    @Test
    fun `player in ally channel leaves when their guild has no alliances`() {
        // A player who still has a guild but lost every ally must be moved out of ally chat.
        val g = guild()
        every { guildService.getPlayerGuilds(playerId) } returns setOf(g)
        every { relationService.getGuildRelationsByType(g.id, RelationType.ALLY) } returns emptySet()
        assertTrue(listener.shouldLeaveChannel(playerId, "guild-ally"))
    }

    @Test
    fun `player in ally channel leaves when the only ally relation is not active`() {
        val g = guild()
        every { guildService.getPlayerGuilds(playerId) } returns setOf(g)
        every { relationService.getGuildRelationsByType(g.id, RelationType.ALLY) } returns setOf(
            Relation.create(
                guildA = g.id, guildB = UUID.randomUUID(),
                type = RelationType.ALLY, status = RelationStatus.PENDING
            )
        )
        assertTrue(listener.shouldLeaveChannel(playerId, "guild-ally"))
    }

    @Test
    fun `player in ally channel stays when any of their guilds has an alliance`() {
        val g1 = guild(); val g2 = guild()
        every { guildService.getPlayerGuilds(playerId) } returns setOf(g1, g2)
        every { relationService.getGuildRelationsByType(g1.id, RelationType.ALLY) } returns emptySet()
        every { relationService.getGuildRelationsByType(g2.id, RelationType.ALLY) } returns setOf(activeAlly(g2.id))
        assertFalse(listener.shouldLeaveChannel(playerId, "guild-ally"))
    }

    // --- dynamic party channels (keyed by party UUID) -------------------

    @Test
    fun `player in a party channel leaves when that party has been dissolved`() {
        val g = guild()
        val dissolvedParty = Party(
            id = UUID.randomUUID(), name = "P", guildIds = setOf(g.id),
            leaderId = UUID.randomUUID(), status = PartyStatus.DISSOLVED, createdAt = Instant.now()
        )
        every { guildService.getPlayerGuilds(playerId) } returns setOf(g)
        // A dissolved party still appears in the full party list, but not the active one.
        every { partyService.getAllPartiesForGuild(g.id) } returns setOf(dissolvedParty)
        every { partyService.getActivePartiesForGuild(g.id) } returns emptySet()
        assertTrue(listener.shouldLeaveChannel(playerId, dissolvedParty.id.toString()))
    }

    @Test
    fun `player in a party channel stays when it is an active party of their guild`() {
        val g = guild()
        val party = Party(
            id = UUID.randomUUID(), name = "P", guildIds = setOf(g.id),
            leaderId = UUID.randomUUID(), status = PartyStatus.ACTIVE, createdAt = Instant.now()
        )
        every { guildService.getPlayerGuilds(playerId) } returns setOf(g)
        every { partyService.getActivePartiesForGuild(g.id) } returns setOf(party)
        assertFalse(listener.shouldLeaveChannel(playerId, party.id.toString()))
    }

    @Test
    fun `non-uuid channels from other plugins are never touched`() {
        // "staff", "global", etc. are not LumaGuilds channels and must be left alone.
        assertFalse(listener.shouldLeaveChannel(playerId, "staff"))
    }

    @Test
    fun `player with no guild leaves a party channel`() {
        every { guildService.getPlayerGuilds(playerId) } returns emptySet()
        assertTrue(listener.shouldLeaveChannel(playerId, UUID.randomUUID().toString()))
    }
}
