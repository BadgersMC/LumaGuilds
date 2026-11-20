package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REQ-190: existing guilds' member counts are recorded once as protected floors. */
class MemberCapacityFloorSQLTest : RewardSqlTestFixture() {
    @Test
    fun `snapshot records every existing guild once and never again`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val guilds = GuildRepositorySQLite(storage)
        val ranks = RankRepositorySQLite(storage)
        val members = MemberRepositorySQLite(storage)
        val big = UUID.randomUUID()
        val empty = UUID.randomUUID()
        guilds.add(Guild(big, "Big", createdAt = Instant.now()))
        guilds.add(Guild(empty, "Empty", createdAt = Instant.now()))
        val rank = Rank(UUID.randomUUID(), big, "Member", 1, emptySet())
        ranks.add(rank)
        repeat(3) { members.add(Member(UUID.randomUUID(), big, rank.id, Instant.now())) }

        val floors = MemberCapacityFloorsSQL(storage)
        assertEquals(2, floors.snapshotOnce())
        assertEquals(3, floors.floor(big))
        assertEquals(0, floors.floor(empty))

        members.add(Member(UUID.randomUUID(), big, rank.id, Instant.now()))
        assertEquals(0, floors.snapshotOnce(), "the snapshot is taken only once")
        val later = UUID.randomUUID()
        guilds.add(Guild(later, "Later", createdAt = Instant.now()))
        closeStorage(storage)

        val reopened = MemberCapacityFloorsSQL(openStorage())
        assertEquals(3, reopened.floor(big))
        assertNull(reopened.floor(later), "guilds created later have no floor")
    }
}
