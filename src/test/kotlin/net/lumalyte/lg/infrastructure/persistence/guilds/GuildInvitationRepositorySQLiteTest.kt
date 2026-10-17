package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.GuildInvitation
import net.lumalyte.lg.infrastructure.persistence.migrations.InvitationStatisticsSchema
import net.lumalyte.lg.infrastructure.persistence.storage.VirtualThreadSQLiteStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class GuildInvitationRepositorySQLiteTest {
    @TempDir lateinit var tempDir: Path
    private lateinit var storage: VirtualThreadSQLiteStorage
    private lateinit var repository: GuildInvitationRepositorySQLite
    private val guildId = UUID.fromString("00000000-0000-0000-0000-000000000100")

    @BeforeEach
    fun setUp() {
        storage = VirtualThreadSQLiteStorage(tempDir.toFile())
        storage.connection.executeUpdate("CREATE TABLE guilds (id TEXT PRIMARY KEY)")
        storage.connection.executeUpdate(
            """CREATE TABLE guild_invitations (
                guild_id TEXT NOT NULL,
                guild_name TEXT NOT NULL,
                invited_player_id TEXT NOT NULL,
                inviter_player_id TEXT NOT NULL,
                inviter_name TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                PRIMARY KEY (invited_player_id, guild_id)
            )""".trimIndent()
        )
        storage.connection.executeUpdate("INSERT INTO guilds (id) VALUES (?)", guildId.toString())
        storage.connection.getConnection().use { InvitationStatisticsSchema.create(it, mariaDb = false) }
        repository = GuildInvitationRepositorySQLite(storage)
    }

    @AfterEach
    fun tearDown() {
        storage.connection.close()
    }

    @Test
    fun `successful invitations append durable history while duplicate pending attempts do not`() {
        val alpha = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val beta = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val targetA = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val targetB = UUID.fromString("00000000-0000-0000-0000-000000000012")

        assertTrue(repository.add(invite(alpha, targetA, "Alpha")))
        assertFalse(repository.add(invite(alpha, targetA, "Alpha")))
        assertEquals(1, repository.getSentInvitationCount(guildId))

        assertTrue(repository.remove(targetA, guildId))
        assertTrue(repository.add(invite(alpha, targetA, "Alpha")))
        assertTrue(repository.add(invite(beta, targetB, "Beta")))

        assertEquals(3, repository.getSentInvitationCount(guildId))
        val leaderboard = repository.getInvitationLeaderboard(guildId, 10)
        assertEquals(listOf(alpha, beta), leaderboard.map { it.inviterPlayerId })
        assertEquals(listOf(2, 1), leaderboard.map { it.inviteCount })
    }
    @Test
    fun `leaderboard pages preserve deterministic global rank order`() {
        val inviters = (1..4).map { value ->
            UUID.fromString("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")
        }
        inviters.forEachIndexed { index, inviter ->
            val target = UUID.fromString("00000000-0000-0000-0001-${(index + 1).toString().padStart(12, '0')}")
            assertTrue(repository.add(invite(inviter, target, "Inviter$index")))
        }

        assertEquals(4, repository.getInvitationLeaderboardInviterCount(guildId))
        val page = repository.getInvitationLeaderboardPage(guildId, offset = 1, limit = 2)
        assertEquals(inviters.subList(1, 3), page.map { it.inviterPlayerId })
        assertEquals(listOf(1, 1), page.map { it.inviteCount })
    }

    @Test
    fun `history failure rolls back pending invitation and cache state`() {
        storage.connection.executeUpdate("DROP TABLE guild_invitation_history")
        val inviter = UUID.randomUUID()
        val target = UUID.randomUUID()

        assertFalse(repository.add(invite(inviter, target, "Inviter")))
        assertFalse(repository.hasInvitation(target, guildId))
        assertEquals(
            0,
            storage.connection.getFirstRow(
                "SELECT COUNT(*) AS total FROM guild_invitations WHERE guild_id = ?",
                guildId.toString()
            )!!.getInt("total")
        )
    }

    private fun invite(inviter: UUID, target: UUID, name: String) = GuildInvitation(
        guildId = guildId,
        guildName = "Badgers",
        invitedPlayerId = target,
        inviterPlayerId = inviter,
        inviterName = name,
        timestamp = Instant.parse("2026-09-21T12:00:00Z")
    )
}
