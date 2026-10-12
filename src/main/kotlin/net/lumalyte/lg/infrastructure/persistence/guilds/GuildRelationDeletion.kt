package net.lumalyte.lg.infrastructure.persistence.guilds

import java.sql.Connection
import java.util.UUID

/** Both the historical MariaDB migration and current relation adapter schemas remain supported. */
internal fun Connection.deleteGuildRelations(guildId: UUID) {
    val modern = metaData.getColumns(catalog, null, "relations", "guild_a").use { it.next() }
    val columns = if (modern) "guild_a = ? OR guild_b = ?" else "guild_id = ? OR target_guild_id = ?"
    prepareStatement("DELETE FROM relations WHERE $columns").use {
        it.setString(1, guildId.toString())
        it.setString(2, guildId.toString())
        it.executeUpdate()
    }
}
