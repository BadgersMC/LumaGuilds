package net.lumalyte.lg.infrastructure.persistence.guilds

import java.sql.Connection
import java.util.UUID

/**
 * Cross-feature financial gate: buying a paid Outpost cannot precede the
 * verified home-to-entitlement mapping required to use that Outpost.
 * Missing v51 migration/policy is fail-closed; this helper never promotes
 * old guilds to READY or edits saved homes.
 */
internal object GuildHomeOutpostPurchasePolicy {
    fun isReady(connection: Connection, guildId: UUID, forUpdate: Boolean = false): Boolean {
        val exists = connection.metaData.getTables(
            connection.catalog, null, "%", arrayOf("TABLE")
        ).use { tables ->
            generateSequence { if (tables.next()) tables.getString("TABLE_NAME") else null }
                .any { it.equals("guild_home_access_policies", ignoreCase = true) }
        }
        if (!exists) return false

        val lock = if (forUpdate) " FOR UPDATE" else ""
        return connection.prepareStatement(
            "SELECT state FROM guild_home_access_policies WHERE guild_id = ?$lock"
        ).use { statement ->
            statement.setString(1, guildId.toString())
            statement.executeQuery().use { rows ->
                rows.next() && rows.getString("state") == "READY"
            }
        }
    }
}
