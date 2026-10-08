package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Both SQL rows and repository caches must survive a failed ownership change. */
internal class MemberOwnershipTransferTest {
    @TempDir var directory: Path? = null

    private fun fixture(block: (Fixture) -> Unit) {
        val storage = VirtualThreadSQLiteStorage(checkNotNull(directory).toFile())
        try {
            block(Fixture(storage))
        } finally {
            storage.connection.close()
        }
    }

    @Test fun commitsBothRanks() =
        fixture { f ->
            assertTrue(f.repo.transferOwnership(f.owner, f.next, f.lowerRank))
            f.assertRanks(f.lowerRank, f.ownerRank)
            val restarted = MemberRepositorySQLite(f.storage)
            assertEquals(f.ownerRank, restarted.getRankId(f.next.playerId, f.guild))
            assertFalse(f.repo.transferOwnership(f.owner, f.next, f.lowerRank))
            f.assertRanks(f.lowerRank, f.ownerRank)
        }

    @Test fun secondWriteRollsBack() =
        fixture { f ->
            f.storage.connection.executeUpdate(
                "CREATE TRIGGER reject_transfer BEFORE UPDATE ON members " +
                    "WHEN NEW.player_id = '${f.next.playerId}' BEGIN SELECT RAISE(ABORT, 'test fault'); END",
            )
            assertFailsWith<SQLException> { f.repo.transferOwnership(f.owner, f.next, f.lowerRank) }
            f.assertRanks(f.ownerRank, f.lowerRank)
            f.storage.connection.executeUpdate("DROP TRIGGER reject_transfer")
            assertTrue(f.repo.transferOwnership(f.owner, f.next, f.lowerRank))
            f.assertRanks(f.lowerRank, f.ownerRank)
        }

    @Test fun staleTargetRollsBack() =
        fixture { f ->
            f.storage.connection.executeUpdate(
                "UPDATE members SET rank_id = ? WHERE player_id = ?",
                f.thirdRank.toString(),
                f.next.playerId.toString(),
            )
            assertFalse(f.repo.transferOwnership(f.owner, f.next, f.lowerRank))
            assertEquals(f.ownerRank, f.repo.getRankId(f.owner.playerId, f.guild))
            assertEquals(f.ownerRank.toString(), f.persisted(f.owner))
            assertEquals(f.thirdRank.toString(), f.persisted(f.next))
        }

    @Test fun foreignRankRejected() =
        fixture { f ->
            assertFalse(f.repo.transferOwnership(f.owner, f.next, UUID.randomUUID()))
            f.assertRanks(f.ownerRank, f.lowerRank)
            assertFalse(f.repo.transferOwnership(f.owner, f.owner, f.lowerRank))
        }

    private class Fixture(
        val storage: VirtualThreadSQLiteStorage,
    ) {
        val guild = UUID.randomUUID()
        val ownerRank = UUID.randomUUID()
        val lowerRank = UUID.randomUUID()
        val thirdRank = UUID.randomUUID()
        val owner = Member(UUID.randomUUID(), guild, ownerRank, Instant.EPOCH)
        val next = Member(UUID.randomUUID(), guild, lowerRank, Instant.EPOCH)
        val repo: MemberRepositorySQLite

        init {
            storage.connection.executeUpdate("CREATE TABLE guilds (id TEXT PRIMARY KEY)")
            storage.connection.executeUpdate("INSERT INTO guilds VALUES (?)", guild.toString())
            storage.connection.executeUpdate("CREATE TABLE ranks (id TEXT PRIMARY KEY, guild_id TEXT, priority INT)")
            listOf(ownerRank, lowerRank, thirdRank).forEachIndexed { priority, id ->
                storage.connection.executeUpdate("INSERT INTO ranks VALUES (?, ?, ?)", id.toString(), guild.toString(), priority)
            }
            repo = MemberRepositorySQLite(storage)
            check(repo.add(owner))
            check(repo.add(next))
        }

        fun persisted(member: Member): String =
            storage.connection
                .getResults(
                    "SELECT rank_id FROM members WHERE player_id = ? AND guild_id = ?",
                    member.playerId.toString(),
                    guild.toString(),
                ).single()
                .getString("rank_id")

        fun assertRanks(
            current: UUID,
            successor: UUID,
        ) {
            assertEquals(current, repo.getRankId(owner.playerId, guild))
            assertEquals(successor, repo.getRankId(next.playerId, guild))
            assertEquals(current.toString(), persisted(owner))
            assertEquals(successor.toString(), persisted(next))
        }
    }
}
