package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.results.OwnerRecoveryPreview
import net.lumalyte.lg.application.results.OwnerRecoveryPreviewResult
import net.lumalyte.lg.application.results.OwnerRecoveryResult
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.persistence.getInstantNotNull
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MemberRepositorySQLite(private val storage: Storage<Database>) : MemberRepository {
    private val logger = LoggerFactory.getLogger(MemberRepositorySQLite::class.java)

    private fun persistedJoinedAt(instant: Instant): Any =
        if (storage.dialect == SqlDialect.MARIADB) java.sql.Timestamp.from(instant) else instant.toString()
    
    private val members = ConcurrentHashMap<Pair<UUID, UUID>, Member>() // (playerId, guildId) -> Member
    
    init {
        createMemberTable()
        preload()
    }
    
    private fun createMemberTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS members (
                player_id TEXT NOT NULL,
                guild_id TEXT NOT NULL,
                rank_id TEXT NOT NULL,
                joined_at TEXT NOT NULL,
                PRIMARY KEY (player_id, guild_id),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
                FOREIGN KEY (rank_id) REFERENCES ranks(id) ON DELETE CASCADE
            );
        """.trimIndent()
        
        try {
            storage.connection.executeUpdate(sql)
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create members table", e)
        }
    }
    
    private fun preload() {
        val sql = "SELECT * FROM members"
        
        try {
            val results = storage.connection.getResults(sql)
            for (result in results) {
                val member = mapResultSetToMember(result)
                members[Pair(member.playerId, member.guildId)] = member
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to preload members", e)
        }
    }
    
    private fun mapResultSetToMember(rs: co.aikar.idb.DbRow): Member {
        val playerId = UUID.fromString(rs.getString("player_id"))
        val guildId = UUID.fromString(rs.getString("guild_id"))
        val rankId = UUID.fromString(rs.getString("rank_id"))
        val joinedAt = rs.getInstantNotNull("joined_at")

        return Member(
            playerId = playerId,
            guildId = guildId,
            rankId = rankId,
            joinedAt = joinedAt
        )
    }
    
    override fun getAll(): Set<Member> = members.values.toSet()
    
    override fun getByPlayerAndGuild(playerId: UUID, guildId: UUID): Member? = 
        members[Pair(playerId, guildId)]
    
    override fun getByGuild(guildId: UUID): Set<Member> = 
        members.values.filter { it.guildId == guildId }.toSet()
    
    override fun getGuildsByPlayer(playerId: UUID): Set<UUID> = 
        members.values.filter { it.playerId == playerId }.map { it.guildId }.toSet()
    
    override fun getRankId(playerId: UUID, guildId: UUID): UUID? = 
        getByPlayerAndGuild(playerId, guildId)?.rankId
    
    override fun getByRank(guildId: UUID, rankId: UUID): Set<Member> = 
        members.values.filter { it.guildId == guildId && it.rankId == rankId }.toSet()
    
    override fun getMemberCount(guildId: UUID): Int = getByGuild(guildId).size
    
    @Synchronized
    override fun add(member: Member): Boolean {
        val sql = """
            INSERT INTO members (player_id, guild_id, rank_id, joined_at)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                member.playerId.toString(),
                member.guildId.toString(),
                member.rankId.toString(),
                persistedJoinedAt(member.joinedAt)
            )
            if (rowsAffected > 0) {
                members[Pair(member.playerId, member.guildId)] = member
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            logger.error("Failed to persist member {} in guild {}", member.playerId, member.guildId, e)
            false
        }
    }
    
    @Synchronized
    override fun transferOwnership(current: Member, next: Member, demotedRankId: UUID): Boolean {
        if (current.guildId != next.guildId || current.playerId == next.playerId ||
            current.rankId == next.rankId
        ) {
            return false
        }
        val committed = MemberOwnershipTransferSQL(storage).transfer(current, next, demotedRankId)
        if (committed) {
            members[Pair(current.playerId, current.guildId)] = current.copy(rankId = demotedRankId)
            members[Pair(next.playerId, next.guildId)] = next.copy(rankId = current.rankId)
        }
        return committed
    }

    override fun previewOwnerRecovery(guildId: UUID, playerId: UUID): OwnerRecoveryPreviewResult =
        MemberOwnerRecoverySQL(storage).preview(guildId, playerId)

    @Synchronized
    override fun recoverOwnership(preview: OwnerRecoveryPreview): OwnerRecoveryResult {
        val result = MemberOwnerRecoverySQL(storage).recover(preview)
        if (result is OwnerRecoveryResult.Recovered) {
            val recovered = preview.member.copy(rankId = preview.ownerRankId)
            members[Pair(recovered.playerId, recovered.guildId)] = recovered
        }
        return result
    }

    @Synchronized
    override fun update(member: Member): Boolean {
        val sql = """
            UPDATE members SET rank_id = ?, joined_at = ?
            WHERE player_id = ? AND guild_id = ?
                AND EXISTS (
                    SELECT 1 FROM ranks current_rank
                    WHERE current_rank.id = members.rank_id AND current_rank.guild_id = members.guild_id
                        AND current_rank.priority >= 0
                        AND (current_rank.priority > 0 OR current_rank.id = ?)
                )
                AND EXISTS (
                    SELECT 1 FROM ranks next_rank
                    WHERE next_rank.id = ? AND next_rank.guild_id = members.guild_id
                        AND next_rank.priority >= 0
                        AND (next_rank.priority > 0 OR next_rank.id = members.rank_id)
                )
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                member.rankId.toString(),
                persistedJoinedAt(member.joinedAt),
                member.playerId.toString(),
                member.guildId.toString(),
                member.rankId.toString(),
                member.rankId.toString(),
            )
            if (rowsAffected > 0) {
                members[Pair(member.playerId, member.guildId)] = member
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    @Synchronized
    override fun remove(playerId: UUID, guildId: UUID): Boolean {
        // Check the stored rank in the deleting statement: a concurrent recovery/transfer
        // must not turn an already-approved non-Owner removal into an Owner deletion.
        val sql = """
            DELETE FROM members WHERE player_id = ? AND guild_id = ?
                AND EXISTS (
                    SELECT 1 FROM ranks
                    WHERE ranks.id = members.rank_id AND ranks.guild_id = members.guild_id AND ranks.priority > 0
                )
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, playerId.toString(), guildId.toString())
            if (rowsAffected > 0) {
                members.remove(Pair(playerId, guildId))
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    @Synchronized
    override fun removeByGuild(guildId: UUID): Boolean {
        val sql = "DELETE FROM members WHERE guild_id = ?"
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql, guildId.toString())
            if (rowsAffected > 0) {
                members.entries.removeIf { it.value.guildId == guildId }
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean = 
        getByPlayerAndGuild(playerId, guildId) != null
    
    override fun getTotalCount(): Int = members.size

    @Synchronized
    override fun evictGuild(guildId: UUID) {
        members.entries.removeIf { it.value.guildId == guildId }
    }
}
