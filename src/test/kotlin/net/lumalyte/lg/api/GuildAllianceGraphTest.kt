package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RelationType
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Checks alliance capability and filters used by protected KOTH matches. */
internal class GuildAllianceGraphTest {
    /** Legacy construction reports unknown capability rather than no alliances. */
    @Test
    fun legacyCapability() {
        assertNull(GuildLookupImpl(mockk(), mockk(), mockk(), mockk()).getActiveAllianceGraph())
    }

    /** Isolated guilds remain present, while inactive and enemy edges are omitted. */
    @Test
    fun activeAllianceSnapshot() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        val guilds = mockk<GuildService>()
        val relations = mockk<RelationService>()
        every { guilds.getAllGuilds() } returns guildSet(a, b, c)
        val active = Relation.create(guildA = a, guildB = b, type = RelationType.ALLY)
        val pending = ally(a, c, RelationStatus.PENDING)
        val expired = ally(b, c, RelationStatus.EXPIRED)
        val enemy = Relation.create(guildA = a, guildB = c, type = RelationType.ENEMY)
        every { relations.getGuildRelations(a) } returns setOf(active, pending, enemy)
        every { relations.getGuildRelations(b) } returns setOf(active, expired)
        every { relations.getGuildRelations(c) } returns setOf(pending, expired, enemy)
        val api = GuildLookupImpl(guilds, mockk(), mockk(), mockk(), relations)
        assertEquals(mapOf(a to setOf(b), b to setOf(a), c to emptySet()), api.getActiveAllianceGraph())
    }

    private fun guildSet(vararg ids: UUID): Set<Guild> =
        ids
            .mapIndexed { index, id ->
                Guild(id = id, name = "guild$index", createdAt = Instant.now())
            }
            .toSet()

    private fun ally(a: UUID, b: UUID, status: RelationStatus): Relation =
        Relation.create(
            guildA = a,
            guildB = b,
            type = RelationType.ALLY,
            status = status,
        )
}
