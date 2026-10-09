package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Native transaction proof against a fixed disposable loopback database, never production. */
internal class MemberOwnershipMariaDbTest {
    @Test fun commitsAndRejectsStaleRetry() {
        fixture { f ->
            assertTrue(f.repo.transferOwnership(f.owner, f.next, f.lowerRank))
            f.assertRanks(f.lowerRank, f.ownerRank)
            assertFalse(f.repo.transferOwnership(f.owner, f.next, f.lowerRank))
            f.assertRanks(f.lowerRank, f.ownerRank)
            val restarted = MemberRepositorySQLite(f.storage)
            assertEquals(f.ownerRank, restarted.getRankId(f.next.playerId, f.guild))
        }
    }

    @Test fun secondWriteRollsBack() {
        fixture { f ->
            f.storage.connection.executeUpdate(
                "CREATE TRIGGER reject_transfer BEFORE UPDATE ON members FOR EACH ROW " +
                    "BEGIN IF NEW.player_id = '${f.next.playerId}' THEN " +
                    "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test fault'; END IF; END",
            )
            assertFailsWith<SQLException> { f.repo.transferOwnership(f.owner, f.next, f.lowerRank) }
            f.assertRanks(f.ownerRank, f.lowerRank)
            f.storage.connection.executeUpdate("DROP TRIGGER reject_transfer")
            assertTrue(f.repo.transferOwnership(f.owner, f.next, f.lowerRank))
            f.assertRanks(f.lowerRank, f.ownerRank)
        }
    }

    private fun fixture(block: (Fixture) -> Unit) {
        val port = System.getenv("GUILD_OWNERSHIP_TEST_MARIA_PORT")?.toIntOrNull()
        assumeTrue(port != null, "Disposable loopback MariaDB not configured")
        val storage = MariaDBStorage("127.0.0.1", checkNotNull(port), "lg_shop_xp_test", "xp_test", "xp_test")
        try {
            block(Fixture(storage))
        } finally {
            storage.connection.close(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private class Fixture(val storage: MariaDBStorage) {
        val guild = UUID.randomUUID()
        val ownerRank = UUID.randomUUID()
        val lowerRank = UUID.randomUUID()
        val owner = Member(UUID.randomUUID(), guild, ownerRank, Instant.EPOCH)
        val next = Member(UUID.randomUUID(), guild, lowerRank, Instant.EPOCH)
        val repo: MemberRepositorySQLite

        init {
            createSchema()
            for ((priority, id) in listOf(ownerRank, lowerRank).withIndex()) {
                insertRank(id, priority)
            }
            repo = MemberRepositorySQLite(storage)
            check(repo.add(owner))
            check(repo.add(next))
        }

        private fun createSchema() {
            storage.connection.executeUpdate("DROP TRIGGER IF EXISTS reject_transfer")
            listOf("members", "ranks", "guilds").forEach {
                storage.connection.executeUpdate("DROP TABLE IF EXISTS $it")
            }
            storage.connection.executeUpdate("CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY) ENGINE=InnoDB")
            storage.connection.executeUpdate("INSERT INTO guilds VALUES (?)", guild.toString())
            storage.connection.executeUpdate(
                "CREATE TABLE ranks (id VARCHAR(36) PRIMARY KEY, guild_id VARCHAR(36), priority INT) ENGINE=InnoDB",
            )
            storage.connection.executeUpdate(
                "CREATE TABLE members (player_id VARCHAR(36), guild_id VARCHAR(36), rank_id VARCHAR(36), " +
                    "joined_at VARCHAR(64), PRIMARY KEY (player_id, guild_id)) ENGINE=InnoDB",
            )
        }

        private fun insertRank(id: UUID, priority: Int) {
            storage.connection.executeUpdate(
                "INSERT INTO ranks VALUES (?, ?, ?)",
                id.toString(),
                guild.toString(),
                priority,
            )
        }

        fun assertRanks(current: UUID, successor: UUID) {
            assertEquals(current, repo.getRankId(owner.playerId, guild))
            assertEquals(successor, repo.getRankId(next.playerId, guild))
            assertEquals(current.toString(), persisted(owner))
            assertEquals(successor.toString(), persisted(next))
        }

        private fun persisted(member: Member): String {
            val sql = "SELECT rank_id FROM members WHERE player_id = ? AND guild_id = ?"
            return storage.connection
                .getResults(sql, member.playerId.toString(), guild.toString())
                .single()
                .getString("rank_id")
        }
    }

    private companion object {
        const val CLOSE_TIMEOUT_SECONDS = 5L
    }
}
