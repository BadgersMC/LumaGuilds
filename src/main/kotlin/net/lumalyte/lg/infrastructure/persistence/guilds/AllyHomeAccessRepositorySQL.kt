package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.AllyHomeAccessRepository
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.AllyHomeAccess
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.persistence.migrations.AllyHomeAccessSchema
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** REQ-180, REQ-182: loads everything once, then writes through to SQL before the cache. */
internal class AllyHomeAccessRepositorySQL(private val storage: Storage<Database>) : AllyHomeAccessRepository {
    private val logger = LoggerFactory.getLogger(AllyHomeAccessRepositorySQL::class.java)
    private val ranks = ConcurrentHashMap<UUID, Map<UUID, Set<UUID>>>()
    private val blocked = ConcurrentHashMap<UUID, Set<UUID>>()
    private val homes = ConcurrentHashMap<UUID, GuildHome>()
    private val outbound = ConcurrentHashMap<UUID, Map<UUID, Set<UUID>>>()

    init {
        try {
            load()
        } catch (error: SQLException) {
            logger.error("Unable to load allied-home access rules; hosts' own rules are not applied", error)
        }
    }

    private fun load() = connection { c ->
        c.prepareStatement("SELECT host_guild_id, visitor_guild_id, rank_ids FROM ${AllyHomeAccessSchema.VISITOR_RANKS}")
            .use { st ->
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val host = UUID.fromString(rs.getString(1))
                        ranks[host] = (ranks[host] ?: emptyMap()) + (UUID.fromString(rs.getString(2)) to parseIds(rs.getString(3)))
                    }
                }
            }
        c.prepareStatement("SELECT host_guild_id, player_id FROM ${AllyHomeAccessSchema.BLOCKED}").use { st ->
            st.executeQuery().use { rs ->
                while (rs.next()) {
                    val host = UUID.fromString(rs.getString(1))
                    blocked[host] = (blocked[host] ?: emptySet()) + UUID.fromString(rs.getString(2))
                }
            }
        }
        c.prepareStatement("SELECT guild_id, ally_guild_id, rank_ids FROM ${AllyHomeAccessSchema.OUTBOUND_RANKS}").use { st ->
            st.executeQuery().use { rs ->
                while (rs.next()) {
                    val guild = UUID.fromString(rs.getString(1))
                    outbound[guild] = (outbound[guild] ?: emptyMap()) + (UUID.fromString(rs.getString(2)) to parseIds(rs.getString(3)))
                }
            }
        }
        c.prepareStatement("SELECT guild_id, world_id, x, y, z FROM ${AllyHomeAccessSchema.SECOND_HOMES}").use { st ->
            st.executeQuery().use { rs ->
                while (rs.next()) {
                    homes[UUID.fromString(rs.getString(1))] =
                        GuildHome(UUID.fromString(rs.getString(2)), Position3D(rs.getInt(3), rs.getInt(4), rs.getInt(5)))
                }
            }
        }
    }

    override fun access(hostGuildId: UUID): AllyHomeAccess =
        AllyHomeAccess(ranks[hostGuildId] ?: emptyMap(), blocked[hostGuildId] ?: emptySet())

    @Synchronized
    override fun setVisitorRanks(hostGuildId: UUID, visitorGuildId: UUID, rankIds: Set<UUID>?): Boolean = write {
        delete(it, AllyHomeAccessSchema.VISITOR_RANKS, "host_guild_id = ? AND visitor_guild_id = ?", hostGuildId, visitorGuildId)
        if (rankIds != null) {
            it.prepareStatement(
                "INSERT INTO ${AllyHomeAccessSchema.VISITOR_RANKS} (host_guild_id, visitor_guild_id, rank_ids) VALUES (?, ?, ?)",
            ).use { st ->
                st.setString(1, hostGuildId.toString())
                st.setString(2, visitorGuildId.toString())
                st.setString(3, rankIds.joinToString(",") { id -> id.toString() })
                st.executeUpdate()
            }
        }
    }.also { ok ->
        if (ok) {
            val current = (ranks[hostGuildId] ?: emptyMap()) - visitorGuildId
            ranks[hostGuildId] = if (rankIds == null) current else current + (visitorGuildId to rankIds.toSet())
        }
    }

    @Synchronized
    override fun setBlocked(hostGuildId: UUID, playerId: UUID, blocked: Boolean): Boolean = write {
        delete(it, AllyHomeAccessSchema.BLOCKED, "host_guild_id = ? AND player_id = ?", hostGuildId, playerId)
        if (blocked) {
            it.prepareStatement("INSERT INTO ${AllyHomeAccessSchema.BLOCKED} (host_guild_id, player_id) VALUES (?, ?)").use { st ->
                st.setString(1, hostGuildId.toString())
                st.setString(2, playerId.toString())
                st.executeUpdate()
            }
        }
    }.also { ok ->
        if (ok) {
            val current = this.blocked[hostGuildId] ?: emptySet()
            this.blocked[hostGuildId] = if (blocked) current + playerId else current - playerId
        }
    }

    override fun outboundRanks(guildId: UUID): Map<UUID, Set<UUID>> = outbound[guildId] ?: emptyMap()

    @Synchronized
    override fun setOutboundRanks(guildId: UUID, allyGuildId: UUID, rankIds: Set<UUID>?): Boolean = write {
        delete(it, AllyHomeAccessSchema.OUTBOUND_RANKS, "guild_id = ? AND ally_guild_id = ?", guildId, allyGuildId)
        if (rankIds != null) {
            it.prepareStatement(
                "INSERT INTO ${AllyHomeAccessSchema.OUTBOUND_RANKS} (guild_id, ally_guild_id, rank_ids) VALUES (?, ?, ?)",
            ).use { st ->
                st.setString(1, guildId.toString())
                st.setString(2, allyGuildId.toString())
                st.setString(3, rankIds.joinToString(",") { id -> id.toString() })
                st.executeUpdate()
            }
        }
    }.also { ok ->
        if (ok) {
            val current = (outbound[guildId] ?: emptyMap()) - allyGuildId
            outbound[guildId] = if (rankIds == null) current else current + (allyGuildId to rankIds.toSet())
        }
    }

    override fun secondHome(guildId: UUID): GuildHome? = homes[guildId]

    @Synchronized
    override fun setSecondHome(guildId: UUID, home: GuildHome?): Boolean = write {
        delete(it, AllyHomeAccessSchema.SECOND_HOMES, "guild_id = ?", guildId)
        if (home != null) {
            it.prepareStatement(
                "INSERT INTO ${AllyHomeAccessSchema.SECOND_HOMES} (guild_id, world_id, x, y, z) VALUES (?, ?, ?, ?, ?)",
            ).use { st ->
                st.setString(1, guildId.toString())
                st.setString(2, home.worldId.toString())
                st.setInt(3, home.position.x)
                st.setInt(4, home.position.y)
                st.setInt(5, home.position.z)
                st.executeUpdate()
            }
        }
    }.also { ok ->
        if (ok) {
            if (home == null) homes.remove(guildId) else homes[guildId] = home
        }
    }

    @Synchronized
    override fun deleteGuild(guildId: UUID) {
        val ok = write {
            delete(it, AllyHomeAccessSchema.VISITOR_RANKS, "host_guild_id = ?", guildId)
            delete(it, AllyHomeAccessSchema.BLOCKED, "host_guild_id = ?", guildId)
            delete(it, AllyHomeAccessSchema.SECOND_HOMES, "guild_id = ?", guildId)
            delete(it, AllyHomeAccessSchema.OUTBOUND_RANKS, "guild_id = ?", guildId)
        }
        if (ok) {
            outbound.remove(guildId)
            ranks.remove(guildId)
            blocked.remove(guildId)
            homes.remove(guildId)
        }
    }

    private fun delete(c: Connection, table: String, where: String, vararg ids: UUID) {
        c.prepareStatement("DELETE FROM $table WHERE $where").use { st ->
            ids.forEachIndexed { i, id -> st.setString(i + 1, id.toString()) }
            st.executeUpdate()
        }
    }

    /** Runs [body] in one transaction; false (and a log line) when storage failed. */
    private fun write(body: (Connection) -> Unit): Boolean = try {
        connection { c ->
            val auto = c.autoCommit
            c.autoCommit = false
            try {
                body(c)
                c.commit()
            } catch (error: SQLException) {
                c.rollback()
                throw error
            } finally {
                c.autoCommit = auto
            }
        }
        true
    } catch (error: SQLException) {
        logger.error("Unable to save allied-home access change", error)
        false
    }

    private fun <T> connection(body: (Connection) -> T): T = storage.connection.connection.use(body)

    private fun parseIds(raw: String): Set<UUID> =
        raw.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.toSet()
}
