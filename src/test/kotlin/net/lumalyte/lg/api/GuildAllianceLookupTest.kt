package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationStatus
import net.lumalyte.lg.domain.entities.RelationType
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildAllianceLookupTest {
    @Test fun `only current active alliances grant access and repository warms before event reads`() {
        val guild = UUID.randomUUID()
        val ally = UUID.randomUUID()
        val repo = mockk<RelationRepository>()
        every { repo.getAll() } returns emptySet()
        var relation: Relation? = Relation.create(UUID.randomUUID(), guild, ally, RelationType.ALLY, createdAt = Instant.now())
        every { repo.getByGuilds(guild, ally) } answers { relation }
        val lookup = GuildAllianceLookupImpl(repo)
        verify(exactly = 1) { repo.getAll() }
        assertTrue(lookup.areAllied(guild, ally))
        relation = relation!!.copy(status = RelationStatus.PENDING)
        assertFalse(lookup.areAllied(guild, ally))
        relation = relation!!.copy(type = RelationType.ENEMY, status = RelationStatus.ACTIVE)
        assertFalse(lookup.areAllied(guild, ally))
        relation = null
        assertFalse(lookup.areAllied(guild, ally))
        assertFalse(lookup.areAllied(guild, guild))
    }
}
