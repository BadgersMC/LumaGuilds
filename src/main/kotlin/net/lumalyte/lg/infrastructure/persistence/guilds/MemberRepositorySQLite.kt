package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.persistence.getInstantNotNull
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class MemberRepositorySQLite(private val storage: Storage<Database>) : MemberRepository {
    
    private val members: MutableMap<Pair<UUID, UUID>, Member> = mutableMapOf() // (playerId, guildId) -> Member
    
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
                member.joinedAt.toString()
            )
            if (rowsAffected > 0) {
                members[Pair(member.playerId, member.guildId)] = member
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun update(member: Member): Boolean {
        val sql = """
            UPDATE members SET rank_id = ?, joined_at = ?
            WHERE player_id = ? AND guild_id = ?
        """.trimIndent()
        
        return try {
            val rowsAffected = storage.connection.executeUpdate(sql,
                member.rankId.toString(),
                member.joinedAt.toString(),
                member.playerId.toString(),
                member.guildId.toString()
            )
            if (rowsAffected > 0) {
                members[Pair(member.playerId, member.guildId)] = member
            }
            rowsAffected > 0
        } catch (e: SQLException) {
            false
        }
    }
    
    override fun remove(playerId: UUID, guildId: UUID): Boolean {
        val sql = "DELETE FROM members WHERE player_id = ? AND guild_id = ?"
        
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
}
