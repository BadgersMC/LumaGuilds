package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RelationType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class GuildInfoRelationResolverTest {
    private val sourceId = UUID.randomUUID()
    private val alpha = guild("alpha")
    private val bravo = guild("Bravo")
    private val charlie = guild("charlie")
    private val missingId = UUID.randomUUID()

    @Test
    fun `resolver returns only active existing guilds for requested type in stable name order`() {
        val guilds = mapOf(alpha.id to alpha, bravo.id to bravo, charlie.id to charlie)
        val relations = listOf(
            relation(sourceId, charlie.id, RelationType.ALLY),
            relation(sourceId, alpha.id, RelationType.ALLY),
            relation(sourceId, bravo.id, RelationType.ALLY),
            relation(sourceId, missingId, RelationType.ALLY),
            relation(sourceId, UUID.randomUUID(), RelationType.ALLY, RelationStatus.PENDING),
            relation(sourceId, UUID.randomUUID(), RelationType.ENEMY),
        )

        val resolved = GuildInfoRelationResolver.resolve(
            sourceId,
            RelationType.ALLY,
            relations,
            guilds::get,
        )

        assertEquals(listOf("alpha", "Bravo", "charlie"), resolved.map { it.guild.name })
    }
    @Test
    fun `resolver deduplicates duplicate durable relation rows by guild`() {
        val relations = listOf(
            relation(sourceId, alpha.id, RelationType.ENEMY),
            relation(sourceId, alpha.id, RelationType.ENEMY),
        )

        val resolved = GuildInfoRelationResolver.resolve(
            sourceId,
            RelationType.ENEMY,
            relations,
        ) { if (it == alpha.id) alpha else null }

        assertEquals(listOf(alpha.id), resolved.map { it.guild.id })
    }

    @Test
    fun `pagination covers every entry without dropping the twenty ninth guild`() {
        val values = (1..29).toList()

        assertEquals(2, GuildInfoRelationResolver.totalPages(values.size, 28))
        assertEquals((1..28).toList(), GuildInfoRelationResolver.page(values, 0, 28))
        assertEquals(listOf(29), GuildInfoRelationResolver.page(values, 1, 28))
    }

    @Test
    fun `pagination clamps invalid pages and keeps empty lists on one logical page`() {
        val values = listOf("a", "b", "c")

        assertEquals(listOf("a", "b"), GuildInfoRelationResolver.page(values, -10, 2))
        assertEquals(listOf("c"), GuildInfoRelationResolver.page(values, 99, 2))
        assertEquals(1, GuildInfoRelationResolver.totalPages(0, 28))
        assertEquals(emptyList(), GuildInfoRelationResolver.page(emptyList<String>(), 0, 28))
    }

    private fun guild(name: String) = Guild(
        id = UUID.randomUUID(),
        name = name,
        createdAt = Instant.EPOCH,
    )

    private fun relation(
        first: UUID,
        second: UUID,
        type: RelationType,
        status: RelationStatus = RelationStatus.ACTIVE,
    ): Relation = Relation.create(
        guildA = first,
        guildB = second,
        type = type,
        status = status,
        createdAt = Instant.EPOCH,
    )
}
