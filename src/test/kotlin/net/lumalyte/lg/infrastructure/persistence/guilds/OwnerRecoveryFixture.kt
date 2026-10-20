package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.results.OwnerRecoveryPreview
import net.lumalyte.lg.application.results.OwnerRecoveryPreviewResult
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Disposable SQLite/MariaDB data with real rank/member storage and existing guild progress.
 *
 * Distinct fixture literals make unintended edits to membership and guild progress observable.
 *
 * Codacy's FunctionSignature and MultilineExpressionWrapping conflict on expression-body placement.
 * Fitting function expressions stay beside their signatures; variable expressions remain explicitly wrapped.
 */
@Suppress("MagicNumber", "MultilineExpressionWrapping")
internal class OwnerRecoveryFixture(val storage: Storage<Database>) {
    val guild = UUID.randomUUID()
    val ownerRank = UUID.randomUUID()
    val lowerRank = UUID.randomUUID()
    val thirdRank = UUID.randomUUID()
    val joinedAt: Instant = Instant.parse("2026-07-10T02:42:58.123456Z")
    val target = Member(UUID.randomUUID(), guild, lowerRank, joinedAt)
    val other = Member(UUID.randomUUID(), guild, thirdRank, joinedAt.minusSeconds(60))
    val repo: MemberRepositorySQLite

    init {
        createSchema()
        insertRank(ownerRank, 0, "Founder")
        insertRank(lowerRank, 1, "Co-owner")
        insertRank(thirdRank, 2, "Member")
        insertMember(target)
        insertMember(other)
        repo = MemberRepositorySQLite(storage)
    }

    private fun createSchema() {
        storage.connection.executeUpdate(
            "CREATE TABLE guilds (id VARCHAR(36) PRIMARY KEY, name VARCHAR(255), " +
                "level INT, bank_balance INT, total_experience INT, homes TEXT)",
        )
        storage.connection.executeUpdate(
            "INSERT INTO guilds VALUES (?, ?, 30, 117, 448487, ?)",
            guild.toString(),
            "Existing family",
            "saved-home-data",
        )
        storage.connection.executeUpdate(
            "CREATE TABLE ranks (id VARCHAR(36) PRIMARY KEY, guild_id VARCHAR(36), name TEXT, priority INT)",
        )
        val dateType = if (storage.dialect == SqlDialect.MARIADB) "DATETIME(6)" else "TEXT"
        storage.connection.executeUpdate(
            "CREATE TABLE members (player_id VARCHAR(36), guild_id VARCHAR(36), rank_id VARCHAR(36), " +
                "joined_at $dateType, PRIMARY KEY (player_id, guild_id))",
        )
    }

    fun insertRank(id: UUID, priority: Int, name: String = "Extra", guildId: UUID = guild) {
        storage.connection.executeUpdate(
            "INSERT INTO ranks VALUES (?, ?, ?, ?)",
            id.toString(),
            guildId.toString(),
            name,
            priority,
        )
    }

    fun insertMember(member: Member) {
        val date: Any =
            if (storage.dialect == SqlDialect.MARIADB) {
                Timestamp.from(member.joinedAt)
            } else {
                member.joinedAt.toString()
            }
        storage.connection.executeUpdate(
            "INSERT INTO members VALUES (?, ?, ?, ?)",
            member.playerId.toString(),
            member.guildId.toString(),
            member.rankId.toString(),
            date,
        )
    }

    fun preview(playerId: UUID = target.playerId): OwnerRecoveryPreview =
        assertIs<OwnerRecoveryPreviewResult.Ready>(repo.previewOwnerRecovery(guild, playerId)).preview

    fun storedRank(member: Member): UUID? = storage.connection.getResults(
        "SELECT rank_id FROM members WHERE player_id = ? AND guild_id = ?",
        member.playerId.toString(),
        member.guildId.toString(),
    ).singleOrNull()?.getString("rank_id")?.let(UUID::fromString)

    fun storedJoinedAt(member: Member): String = storage.connection.getResults(
        "SELECT joined_at FROM members WHERE player_id = ? AND guild_id = ?",
        member.playerId.toString(),
        member.guildId.toString(),
    ).single().get<Any>("joined_at").toString()

    fun ownerCount(): Int = storage.connection.getResults(
        "SELECT COUNT(*) AS total FROM members m JOIN ranks r ON r.id = m.rank_id " +
            "WHERE m.guild_id = ? AND r.priority = 0",
        guild.toString(),
    ).single().getInt("total")

    fun assertProgressUnchanged() {
        val row = storage.connection.getResults("SELECT * FROM guilds WHERE id = ?", guild.toString()).single()
        assertEquals("Existing family", row.getString("name"))
        assertEquals(30, row.getInt("level"))
        assertEquals(117, row.getInt("bank_balance"))
        assertEquals(448_487, row.getInt("total_experience"))
        assertEquals("saved-home-data", row.getString("homes"))
    }
}
