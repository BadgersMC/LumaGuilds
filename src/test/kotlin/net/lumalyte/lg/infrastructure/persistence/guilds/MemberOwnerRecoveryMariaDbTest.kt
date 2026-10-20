package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.application.results.OwnerRecoveryRejection
import net.lumalyte.lg.application.results.OwnerRecoveryResult
import net.lumalyte.lg.infrastructure.persistence.storage.MariaDBStorage
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Explicit opt-in contracts for the fixed disposable loopback database, never production. */
internal class MemberOwnerRecoveryMariaDbTest {
    @Test fun recoversOnceAndPreservesNativeDatetimeMembership() = fixture { f ->
        val joinedBefore = f.storedJoinedAt(f.target)
        val preview = f.preview()
        assertEquals(f.joinedAt, preview.member.joinedAt)
        assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(preview))
        assertEquals(f.ownerRank, f.storedRank(f.target))
        assertEquals(f.ownerRank, f.repo.getRankId(f.target.playerId, f.guild))
        assertEquals(f.thirdRank, f.storedRank(f.other))
        assertEquals(joinedBefore, f.storedJoinedAt(f.target))
        assertEquals(1, f.ownerCount())
        f.assertProgressUnchanged()
        assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.ALREADY_OWNED)
        assertEquals(f.ownerRank, MemberRepositorySQLite(f.storage).getRankId(f.target.playerId, f.guild))
    }

    @Test fun simultaneousRecoveriesAcrossAdaptersProduceOneOwner() = fixture { f ->
        val secondRepo = MemberRepositorySQLite(f.storage)
        val previews = listOf(f.preview(), f.preview(f.other.playerId))
        val start = CountDownLatch(1)
        val attempts = listOf(f.repo, secondRepo).mapIndexed { index, repo ->
            CompletableFuture.supplyAsync {
                check(start.await(5, TimeUnit.SECONDS))
                repo.recoverOwnership(previews[index])
            }
        }
        start.countDown()
        val results = attempts.map { it.get(10, TimeUnit.SECONDS) }
        assertEquals(1, results.count { it is OwnerRecoveryResult.Recovered })
        assertRejected(results.single { it is OwnerRecoveryResult.Rejected }, OwnerRecoveryRejection.ALREADY_OWNED)
        assertEquals(1, f.ownerCount())
        f.assertProgressUnchanged()
    }

    @Test fun storedOwnerCannotBeRemovedOrChangedByAnOldCache() = fixture { f ->
        val staleRepo = MemberRepositorySQLite(f.storage)
        val preview = f.preview()
        assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(preview))
        assertFalse(staleRepo.remove(f.target.playerId, f.guild))
        assertFalse(staleRepo.update(f.target))
        assertFalse(staleRepo.update(f.other.copy(rankId = f.ownerRank)))
        assertEquals(f.ownerRank, f.storedRank(f.target))
        assertEquals(1, f.ownerCount())
        assertTrue(f.repo.transferOwnership(preview.member.copy(rankId = f.ownerRank), f.other, f.lowerRank))
        assertTrue(f.repo.remove(f.target.playerId, f.guild))
        assertEquals(1, f.ownerCount())
    }

    @Test fun changedNativeDatetimeInvalidatesPreview() = fixture { f ->
        val preview = f.preview()
        f.storage.connection.executeUpdate(
            "UPDATE members SET joined_at = ? WHERE player_id = ?",
            Timestamp.from(f.joinedAt.plusSeconds(1)), f.target.playerId.toString(),
        )
        assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.STALE_PREVIEW)
        assertEquals(f.lowerRank, f.storedRank(f.target))
        assertEquals(0, f.ownerCount())
    }

    @Test fun failedWriteRollsBackAndDoesNotPublishCache() = fixture { f ->
        val preview = f.preview()
        f.storage.connection.executeUpdate(
            "CREATE TRIGGER reject_recovery BEFORE UPDATE ON members FOR EACH ROW " +
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test fault'",
        )
        assertRejected(f.repo.recoverOwnership(preview), OwnerRecoveryRejection.STORAGE_FAILURE)
        assertEquals(f.lowerRank, f.storedRank(f.target))
        assertEquals(f.lowerRank, f.repo.getRankId(f.target.playerId, f.guild))
        assertEquals(0, f.ownerCount())
        f.storage.connection.executeUpdate("DROP TRIGGER reject_recovery")
        assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(preview))
    }

    @Test fun previewAndRecoveryNeverUpdateGuildRows() = fixture { f ->
        f.storage.connection.executeUpdate(
            "CREATE TRIGGER forbid_guild_update BEFORE UPDATE ON guilds FOR EACH ROW " +
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'guild state must not change'",
        )
        val preview = f.preview()
        assertEquals(0, f.ownerCount())
        assertIs<OwnerRecoveryResult.Recovered>(f.repo.recoverOwnership(preview))
        f.assertProgressUnchanged()
    }

    private fun fixture(block: (OwnerRecoveryFixture) -> Unit) {
        val port = System.getenv("GUILD_OWNERSHIP_TEST_MARIA_PORT")?.toIntOrNull()
        assumeTrue(port != null, "Disposable loopback MariaDB not configured")
        val storage = MariaDBStorage("127.0.0.1", checkNotNull(port), "lg_shop_xp_test", "xp_test", "xp_test")
        try {
            // This fixed test database is shared with the existing native ownership contract.
            listOf("members", "ranks", "guilds").forEach {
                storage.connection.executeUpdate("DROP TABLE IF EXISTS $it")
            }
            block(OwnerRecoveryFixture(storage))
        } finally {
            storage.connection.close(5, TimeUnit.SECONDS)
        }
    }

    private fun assertRejected(result: OwnerRecoveryResult, reason: OwnerRecoveryRejection) {
        assertEquals(reason, assertIs<OwnerRecoveryResult.Rejected>(result).reason)
    }
}
