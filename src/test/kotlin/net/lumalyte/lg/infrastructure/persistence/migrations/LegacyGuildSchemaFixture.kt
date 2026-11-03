package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Statement

/**
 * Core v28 guild table used by synthetic legacy-upgrade tests.
 *
 * The older id-only stubs were incomplete databases that strict startup schema
 * verification correctly rejected. Keep these columns aligned with the
 * historically required core, Vault, and recruitment fields.
 */
internal fun Statement.createLegacyGuildsTable() {
    execute(
        """CREATE TABLE guilds (
            id TEXT PRIMARY KEY, name TEXT, banner TEXT, emoji TEXT, tag TEXT,
            home_world TEXT, home_x INTEGER, home_y INTEGER, home_z INTEGER,
            level INTEGER, bank_balance INTEGER, mode TEXT, mode_changed_at TEXT,
            created_at TEXT, vault_status TEXT, vault_chest_world TEXT,
            vault_chest_x INTEGER, vault_chest_y INTEGER, vault_chest_z INTEGER,
            is_open INTEGER, join_fee_enabled INTEGER, join_fee_amount INTEGER,
            ally_home_allowed_guilds TEXT
        )""".trimIndent()
    )
}
