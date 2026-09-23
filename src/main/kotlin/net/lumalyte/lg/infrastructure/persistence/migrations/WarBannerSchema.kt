package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection

object WarBannerSchema {
    fun create(connection: Connection, mariaDb: Boolean) {
        val bool = if (mariaDb) "TINYINT(1)" else "INTEGER"
        val engine = if (mariaDb) " ENGINE=InnoDB" else ""
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS war_banners (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    banner_id VARCHAR(36) NOT NULL UNIQUE,
                    world_id VARCHAR(36) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    placed_by VARCHAR(36) NOT NULL,
                    transaction_id VARCHAR(36) NOT NULL UNIQUE,
                    placed_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    cooldown_until BIGINT NOT NULL,
                    active $bool NOT NULL DEFAULT 1
                )$engine
                """.trimIndent()
            )
            if (!mariaDb) {
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_war_banners_active_expiry " +
                        "ON war_banners(active, expires_at)"
                )
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_war_banners_location " +
                        "ON war_banners(world_id, x, y, z, active)"
                )
            }
        }
        if (mariaDb) {
            createMariaIndex(connection, "idx_war_banners_location",
                "CREATE INDEX idx_war_banners_location ON war_banners(world_id, x, y, z, active)")
            createMariaIndex(connection, "idx_war_banners_active_expiry",
                "CREATE INDEX idx_war_banners_active_expiry ON war_banners(active, expires_at)")
        }
    }

    /**
     * Existing ranks predate PLACE_WAR_BANNER. Preserve the intent of existing
     * war-management roles by granting it to every rank that can DECLARE_WAR,
     * plus the highest-priority rank in each guild so an owner can always
     * delegate the new permission later.
     */
    fun backfillRankPermission(connection: Connection): Int {
        if (!tableExists(connection, "ranks")) return 0

        data class StoredRank(
            val id: String,
            val guildId: String,
            val priority: Int,
            val permissions: MutableSet<String>,
        )

        val ranks = connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT id, guild_id, priority, permissions FROM ranks"
            ).use { rows ->
                buildList {
                    while (rows.next()) {
                        val raw = rows.getString("permissions").orEmpty()
                        add(StoredRank(
                            id = rows.getString("id"),
                            guildId = rows.getString("guild_id"),
                            priority = rows.getInt("priority"),
                            permissions = raw.split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toMutableSet(),
                        ))
                    }
                }
            }
        }
        val highestByGuild = ranks.groupBy { it.guildId }
            .mapValues { (_, guildRanks) -> guildRanks.minOf { it.priority } }

        var updated = 0
        connection.prepareStatement(
            "UPDATE ranks SET permissions = ? WHERE id = ?"
        ).use { statement ->
            ranks.forEach { rank ->
                val eligible = rank.priority == highestByGuild[rank.guildId] ||
                    "DECLARE_WAR" in rank.permissions
                if (eligible && rank.permissions.add("PLACE_WAR_BANNER")) {
                    statement.setString(1, rank.permissions.joinToString(","))
                    statement.setString(2, rank.id)
                    statement.addBatch()
                    updated++
                }
            }
            if (updated > 0) statement.executeBatch()
        }
        return updated
    }

    private fun tableExists(connection: Connection, name: String): Boolean =
        connection.metaData.getTables(null, null, null, arrayOf("TABLE")).use { rows ->
            var found = false
            while (rows.next() && !found) {
                found = rows.getString("TABLE_NAME").equals(name, ignoreCase = true)
            }
            found
        }

    private fun createMariaIndex(connection: Connection, name: String, ddl: String) {
        val exists = connection.metaData.getIndexInfo(null, null, "war_banners", false, false).use { rows ->
            generateSequence { if (rows.next()) rows.getString("INDEX_NAME") else null }
                .any { it.equals(name, ignoreCase = true) }
        }
        if (!exists) connection.createStatement().use { it.execute(ddl) }
    }
}
