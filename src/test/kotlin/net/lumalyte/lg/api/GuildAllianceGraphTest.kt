package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildAllianceGraphTest {
    @Test fun `old construction reports unsupported not an empty alliance graph`() {
        assertNull(GuildLookupImpl(mockk(),mockk(),mockk(),mockk()).getActiveAllianceGraph())
    }
    @Test fun `snapshot includes isolated guilds and only active allies`() {
        val a=UUID.randomUUID(); val b=UUID.randomUUID(); val c=UUID.randomUUID()
        val guilds=mockk<GuildService>(); val relations=mockk<RelationService>()
        every { guilds.getAllGuilds() } returns listOf(a,b,c).mapIndexed { index,id -> Guild(id=id,name="guild$index",createdAt=Instant.now()) }.toSet()
        val active=Relation.create(guildA=a,guildB=b,type=RelationType.ALLY)
        val pending=Relation.create(guildA=a,guildB=c,type=RelationType.ALLY,status=RelationStatus.PENDING)
        val expired=Relation.create(guildA=b,guildB=c,type=RelationType.ALLY,status=RelationStatus.EXPIRED)
        val enemy=Relation.create(guildA=a,guildB=c,type=RelationType.ENEMY)
        every { relations.getGuildRelations(a) } returns setOf(active,pending,enemy)
        every { relations.getGuildRelations(b) } returns setOf(active,expired)
        every { relations.getGuildRelations(c) } returns setOf(pending,expired,enemy)
        val api=GuildLookupImpl(guilds,mockk(),mockk(),mockk(),relations)
        assertEquals(mapOf(a to setOf(b),b to setOf(a),c to emptySet()),api.getActiveAllianceGraph())
    }
}
