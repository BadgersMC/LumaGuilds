package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildListRepository
import net.lumalyte.lg.domain.entities.GuildListRankedRow
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID

internal class GuildListRepositorySQL(
    private val storage: Storage<Database>,
) : GuildListRepository {
    override fun getDetails(guildIds: Set<UUID>): Map<UUID, net.lumalyte.lg.domain.entities.GuildDirectoryDetails> {
        if (guildIds.isEmpty()) return emptyMap()
        require(guildIds.size <= DIRECTORY_PAGE_SIZE) { "Directory details must be paged" }
        val placeholders = guildIds.joinToString(",") { "?" }
        val owners = guildIds.associateWith { mutableListOf<UUID>() }
        val allies = guildIds.associateWith { mutableSetOf<String>() }
        storage.connection.connection.use { connection ->
            loadOwners(connection, placeholders, guildIds, owners)
            loadAllies(connection, placeholders, guildIds, allies)
        }
        return guildIds.associateWith { id ->
            net.lumalyte.lg.domain.entities.GuildDirectoryDetails(
                owners.getValue(id).distinct().sortedBy(UUID::toString),
                allies.getValue(id).sorted(),
            )
        }
    }

    private fun loadOwners(
        connection: java.sql.Connection,
        placeholders: String,
        guildIds: Set<UUID>,
        owners: Map<UUID, MutableList<UUID>>,
    ) {
        val sql = """
            SELECT m.guild_id, m.player_id FROM members m
            JOIN ranks r ON r.id = m.rank_id AND r.guild_id = m.guild_id
            WHERE r.priority = 0 AND m.guild_id IN ($placeholders)
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            bindIds(statement, guildIds.toList())
            statement.executeQuery().use { rows -> readOwners(rows, owners) }
        }
    }

    private fun readOwners(rows: java.sql.ResultSet, owners: Map<UUID, MutableList<UUID>>) {
        while (rows.next()) {
            val guildId = UUID.fromString(rows.getString("guild_id"))
            owners[guildId]?.add(UUID.fromString(rows.getString("player_id")))
        }
    }

    private fun loadAllies(
        connection: java.sql.Connection,
        placeholders: String,
        guildIds: Set<UUID>,
        allies: Map<UUID, MutableSet<String>>,
    ) {
        val sql = """
            SELECT r.guild_a, r.guild_b, a.name AS name_a, b.name AS name_b, r.expires_at
            FROM relations r JOIN guilds a ON a.id = r.guild_a JOIN guilds b ON b.id = r.guild_b
            WHERE r.type = 'ALLY' AND r.status = 'ACTIVE'
            AND (r.guild_a IN ($placeholders) OR r.guild_b IN ($placeholders))
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            bindIds(statement, guildIds.toList() + guildIds.toList())
            statement.executeQuery().use { rows -> readAllies(rows, allies) }
        }
    }

    private fun readAllies(rows: java.sql.ResultSet, allies: Map<UUID, MutableSet<String>>) {
        while (rows.next()) {
            val expires = rows.getString("expires_at")?.let(Instant::parse)
            if (expires != null && !expires.isAfter(Instant.now())) continue
            allies[UUID.fromString(rows.getString("guild_a"))]?.add(rows.getString("name_b"))
            allies[UUID.fromString(rows.getString("guild_b"))]?.add(rows.getString("name_a"))
        }
    }

    private fun bindIds(statement: java.sql.PreparedStatement, ids: List<UUID>) {
        ids.forEachIndexed { index, id -> statement.setString(index + 1, id.toString()) }
    }


    override fun getCount(): Int =
        storage.connection.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) AS total FROM guilds").use { statement ->
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getInt("total") else 0
                }
            }
        }

    override fun getPage(
        offset: Int,
        limit: Int,
        sortKey: GuildListSortKey,
        ascending: Boolean,
        weeklyStart: Instant,
        claimsEnabled: Boolean,
        uniqueKillWeight: Int,
    ): List<GuildListRankedRow> {
        if (limit <= 0) return emptyList()
        val direction = if (ascending) "ASC" else "DESC"
        return when (sortKey) {
            GuildListSortKey.ALL_TIME_ACTIVE ->
                activityPage(
                    direction = direction,
                    offset = offset,
                    limit = limit,
                    weeklyStart = null,
                    claimsEnabled = claimsEnabled,
                    uniqueKillWeight = 0,
                )

            GuildListSortKey.WEEKLY_ACTIVE ->
                activityPage(
                    direction = direction,
                    offset = offset,
                    limit = limit,
                    weeklyStart = weeklyStart,
                    claimsEnabled = claimsEnabled,
                    uniqueKillWeight = uniqueKillWeight.coerceAtLeast(0),
                )

            GuildListSortKey.GUILD_LEVEL -> {
                val sql = """
                    SELECT
                        g.id,
                        CAST(g.level AS BIGINT) AS sort_value,
                        0 AS unique_pvp_kills,
                        COALESCE(m.member_count, 0) AS member_count
                    FROM guilds g
                    LEFT JOIN (
                        SELECT guild_id, COUNT(*) AS member_count
                        FROM members
                        GROUP BY guild_id
                    ) m ON m.guild_id = g.id
                    ORDER BY g.level $direction, LOWER(g.name) ASC, g.created_at ASC, g.id ASC
                    LIMIT ? OFFSET ?
                """.trimIndent()
                rows(sql, limit, offset)
            }

            GuildListSortKey.CREATED_AT -> {
                val sql = """
                    SELECT
                        g.id,
                        0 AS sort_value,
                        0 AS unique_pvp_kills,
                        COALESCE(m.member_count, 0) AS member_count
                    FROM guilds g
                    LEFT JOIN (
                        SELECT guild_id, COUNT(*) AS member_count
                        FROM members
                        GROUP BY guild_id
                    ) m ON m.guild_id = g.id
                    ORDER BY g.created_at $direction, LOWER(g.name) ASC, g.created_at ASC, g.id ASC
                    LIMIT ? OFFSET ?
                """.trimIndent()
                rows(sql, limit, offset)
            }
        }
    }

    private fun activityPage(
        direction: String,
        offset: Int,
        limit: Int,
        weeklyStart: Instant?,
        claimsEnabled: Boolean,
        uniqueKillWeight: Int,
    ): List<GuildListRankedRow> {
        val claimMultiplier = if (claimsEnabled) 2 else 0
        val playerKillScore = if (weeklyStart == null) "amount" else "0"

        val activityWhere = if (weeklyStart == null) "" else "WHERE timestamp >= ?"
        val killJoin = if (weeklyStart == null) {
            "LEFT JOIN (SELECT NULL AS guild_id, 0 AS unique_kills) k ON 1 = 0"
        } else {
            """
            LEFT JOIN (
                SELECT killer_guild_id AS guild_id, COUNT(DISTINCT victim_id) AS unique_kills
                FROM kills
                WHERE timestamp >= ?
                  AND killer_guild_id IS NOT NULL
                  AND victim_guild_id IS NOT NULL
                  AND killer_guild_id <> victim_guild_id
                GROUP BY killer_guild_id
            ) k ON k.guild_id = g.id
            """.trimIndent()
        }
        val scoreExpression = if (weeklyStart == null) {
            "COALESCE(a.activity_score, 0)"
        } else {
            "(COALESCE(a.activity_score, 0) + (COALESCE(k.unique_kills, 0) * $uniqueKillWeight))"
        }
        val sql = """
            SELECT
                g.id,
                $scoreExpression AS sort_value,
                COALESCE(k.unique_kills, 0) AS unique_pvp_kills,
                COALESCE(m.member_count, 0) AS member_count
            FROM guilds g
            LEFT JOIN (
                SELECT guild_id, COUNT(*) AS member_count
                FROM members
                GROUP BY guild_id
            ) m ON m.guild_id = g.id
            LEFT JOIN (
                SELECT guild_id,
                    SUM(
                        CASE
                            WHEN source = 'MEMBER_JOINED' THEN amount * 2
                            WHEN source = 'WAR_WON' THEN amount * 3
                            WHEN source = 'CLAIM_CREATED' THEN amount * $claimMultiplier
                            WHEN source = 'PLAYER_KILL' THEN $playerKillScore
                            ELSE amount
                        END
                    ) AS activity_score
                FROM experience_transactions
                $activityWhere
                GROUP BY guild_id
            ) a ON a.guild_id = g.id
            $killJoin
            ORDER BY sort_value $direction, LOWER(g.name) ASC, g.created_at ASC, g.id ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val params = mutableListOf<Any>()
        if (weeklyStart != null) {
            params += weeklyStart.toEpochMilli()
            params += weeklyStart.toString()
        }
        params += limit
        params += offset
        return rows(sql, *params.toTypedArray())
    }

    private fun rows(sql: String, vararg params: Any): List<GuildListRankedRow> =
        storage.connection.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, value ->
                    statement.setObject(index + 1, value)
                }
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                GuildListRankedRow(
                                    guildId = UUID.fromString(rows.getString("id")),
                                    sortValue = rows.getLong("sort_value"),
                                    uniquePvpKills = rows.getInt("unique_pvp_kills"),
                                    memberCount = rows.getInt("member_count"),
                                )
                            )
                        }
                    }
                }
            }
        }
    private companion object {
        const val DIRECTORY_PAGE_SIZE = 36
    }
}
