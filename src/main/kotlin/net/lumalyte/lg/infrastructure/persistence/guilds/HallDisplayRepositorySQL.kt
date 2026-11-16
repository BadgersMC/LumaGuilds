package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.HallDisplayRepository
import net.lumalyte.lg.domain.rewards.HallArmorPart
import net.lumalyte.lg.domain.rewards.HallArmorPiece
import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

/** SQLite and MariaDB storage for Hall themes and display choices (REQ-260, REQ-261). */
@Suppress("LibraryEntitiesShouldNotBePublic")
class HallDisplayRepositorySQL(private val storage: Storage<Database>) : HallDisplayRepository {
    init {
        try {
            jdbc { create(it, storage.dialect == SqlDialect.MARIADB) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create Hall display tables", e)
        }
    }

    override fun owned(guildId: UUID): List<HallTheme> = jdbc { c ->
        c.prepareStatement("SELECT theme FROM guild_hall_themes WHERE guild_id = ? ORDER BY earned_at, theme").use { ps ->
            ps.setString(1, guildId.toString())
            ps.executeQuery().use { rows -> buildList { while (rows.next()) HallTheme.parse(rows.getString(1))?.let(::add) } }
        }
    }

    override fun earn(guildId: UUID, theme: HallTheme, limit: Int, at: Instant): Boolean = transaction { c ->
        val owned = c.prepareStatement("SELECT theme FROM guild_hall_themes WHERE guild_id = ?").use { ps ->
            ps.setString(1, guildId.toString())
            ps.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
        }
        if (theme.name in owned || owned.size >= limit) return@transaction false
        c.prepareStatement("INSERT INTO guild_hall_themes (guild_id, theme, earned_at) VALUES (?, ?, ?)").use { ps ->
            ps.setString(1, guildId.toString())
            ps.setString(2, theme.name)
            ps.setLong(3, at.toEpochMilli())
            ps.executeUpdate() == 1
        }
    }

    override fun choices(guildId: UUID): Map<HallSlot, HallTheme> = jdbc { c ->
        c.prepareStatement("SELECT slot, value FROM guild_hall_display WHERE guild_id = ?").use { ps ->
            ps.setString(1, guildId.toString())
            ps.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        val slot = HallSlot.entries.firstOrNull { it.name == rows.getString(1) } ?: continue
                        HallTheme.parse(rows.getString(2))?.let { put(slot, it) }
                    }
                }
            }
        }
    }

    override fun choose(guildId: UUID, slot: HallSlot, theme: HallTheme?) = put(guildId, slot.name, theme?.name)

    override fun skin(guildId: UUID): UUID? = jdbc { c ->
        c.prepareStatement("SELECT value FROM guild_hall_display WHERE guild_id = ? AND slot = ?").use { ps ->
            ps.setString(1, guildId.toString())
            ps.setString(2, SKIN)
            ps.executeQuery().use { rows -> if (rows.next()) runCatching { UUID.fromString(rows.getString(1)) }.getOrNull() else null }
        }
    }

    override fun setSkin(guildId: UUID, playerId: UUID?) = put(guildId, SKIN, playerId?.toString())

    override fun armor(guildId: UUID): Map<HallArmorPart, HallArmorPiece> = jdbc { c ->
        c.prepareStatement("SELECT slot, value FROM guild_hall_display WHERE guild_id = ? AND slot LIKE 'ARMOR_%'").use { ps ->
            ps.setString(1, guildId.toString())
            ps.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        val part = HallArmorPart.entries.firstOrNull { ARMOR + it.name == rows.getString(1) } ?: continue
                        HallArmorPiece.decode(rows.getString(2))?.let { put(part, it) }
                    }
                }
            }
        }
    }

    override fun setArmor(guildId: UUID, part: HallArmorPart, piece: HallArmorPiece?) = put(guildId, ARMOR + part.name, piece?.encode())

    override fun deleteAll(guildId: UUID) {
        transaction { c ->
            for (table in listOf("guild_hall_themes", "guild_hall_display")) {
                c.prepareStatement("DELETE FROM $table WHERE guild_id = ?").use { ps ->
                    ps.setString(1, guildId.toString())
                    ps.executeUpdate()
                }
            }
        }
    }

    private fun put(guildId: UUID, slot: String, value: String?) {
        transaction { c ->
            c.prepareStatement("DELETE FROM guild_hall_display WHERE guild_id = ? AND slot = ?").use { ps ->
                ps.setString(1, guildId.toString())
                ps.setString(2, slot)
                ps.executeUpdate()
            }
            if (value != null) {
                c.prepareStatement("INSERT INTO guild_hall_display (guild_id, slot, value) VALUES (?, ?, ?)").use { ps ->
                    ps.setString(1, guildId.toString())
                    ps.setString(2, slot)
                    ps.setString(3, value)
                    ps.executeUpdate()
                }
            }
        }
    }

    private fun create(connection: Connection, mariaDb: Boolean) {
        val id = if (mariaDb) "VARCHAR(36)" else "TEXT"
        val key = if (mariaDb) "VARCHAR(32)" else "TEXT"
        val value = if (mariaDb) "VARCHAR(64)" else "TEXT"
        val bigint = if (mariaDb) "BIGINT" else "INTEGER"
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS guild_hall_themes (guild_id $id NOT NULL, theme $key NOT NULL, " +
                    "earned_at $bigint NOT NULL, PRIMARY KEY (guild_id, theme))$engine",
            )
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS guild_hall_display (guild_id $id NOT NULL, slot $key NOT NULL, " +
                    "value $value NOT NULL, PRIMARY KEY (guild_id, slot))$engine",
            )
        }
    }

    private fun <T> jdbc(block: (Connection) -> T): T = storage.connection.connection.use(block)

    private fun <T> transaction(block: (Connection) -> T): T = jdbc { c ->
        val auto = c.autoCommit
        c.autoCommit = false
        try {
            block(c).also { c.commit() }
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = auto
        }
    }

    private companion object {
        const val SKIN = "SKIN"
        const val ARMOR = "ARMOR_"
    }
}
