package net.lumalyte.lg.infrastructure.persistence.guilds

import java.sql.Connection

/** Adds the historical physical-vault fields omitted by MariaDB migrations without rewriting stored guilds. */
internal fun Connection.ensureGuildVaultSchema() {
    val existing =
        metaData.getColumns(catalog, null, "guilds", null).use { columns ->
            buildSet { while (columns.next()) add(columns.getString("COLUMN_NAME").lowercase()) }
        }
    GUILD_VAULT_COLUMNS.filterNot { it.first in existing }.forEach { (_, sql) ->
        createStatement().use { it.executeUpdate(sql) }
    }
}

private val GUILD_VAULT_COLUMNS =
    listOf(
        "vault_status" to "ALTER TABLE guilds ADD COLUMN vault_status VARCHAR(32) DEFAULT 'NEVER_PLACED'",
        "vault_chest_world" to "ALTER TABLE guilds ADD COLUMN vault_chest_world VARCHAR(36)",
        "vault_chest_x" to "ALTER TABLE guilds ADD COLUMN vault_chest_x INTEGER",
        "vault_chest_y" to "ALTER TABLE guilds ADD COLUMN vault_chest_y INTEGER",
        "vault_chest_z" to "ALTER TABLE guilds ADD COLUMN vault_chest_z INTEGER",
    )
