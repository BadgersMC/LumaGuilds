package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.SecondVaultRepository
import net.lumalyte.lg.application.persistence.SecondVaultSpot
import net.lumalyte.lg.infrastructure.persistence.migrations.SecondVaultSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

/** SQLite and MariaDB storage for the second vault (REQ-243, REQ-244). */
@Suppress("LibraryEntitiesShouldNotBePublic")
class SecondVaultRepositorySQL(private val storage: Storage<Database>) : SecondVaultRepository {
    init {
        try {
            jdbc { SecondVaultSchema.create(it, mariaDb = storage.dialect == SqlDialect.MARIADB) }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create second vault tables", e)
        }
    }

    override fun spots(): Map<UUID, SecondVaultSpot> = jdbc { c ->
        c.prepareStatement("SELECT guild_id, world_id, x, y, z FROM guild_second_vaults").use { ps ->
            ps.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(
                            UUID.fromString(rows.getString("guild_id")),
                            SecondVaultSpot(UUID.fromString(rows.getString("world_id")), rows.getInt("x"), rows.getInt("y"), rows.getInt("z")),
                        )
                    }
                }
            }
        }
    }

    override fun place(guildId: UUID, spot: SecondVaultSpot, at: Instant): Boolean = jdbc { c ->
        val exists = c.prepareStatement("SELECT 1 FROM guild_second_vaults WHERE guild_id = ?").use { ps ->
            ps.setString(1, guildId.toString())
            ps.executeQuery().use { it.next() }
        }
        if (exists) return@jdbc false
        c.prepareStatement(
            "INSERT INTO guild_second_vaults (guild_id, world_id, x, y, z, placed_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).use { ps ->
            ps.setString(1, guildId.toString())
            ps.setString(2, spot.worldId.toString())
            ps.setInt(3, spot.x)
            ps.setInt(4, spot.y)
            ps.setInt(5, spot.z)
            ps.setLong(6, at.toEpochMilli())
            ps.executeUpdate() == 1
        }
    }

    override fun removeSpot(guildId: UUID) {
        jdbc { c -> delete(c, "guild_second_vaults", guildId) }
    }

    override fun items(guildId: UUID): Map<Int, String> = jdbc { c ->
        c.prepareStatement("SELECT slot, item_data FROM guild_second_vault_slots WHERE guild_id = ?").use { ps ->
            ps.setString(1, guildId.toString())
            ps.executeQuery().use { rows ->
                buildMap { while (rows.next()) put(rows.getInt("slot"), rows.getString("item_data")) }
            }
        }
    }

    override fun saveItems(guildId: UUID, items: Map<Int, String>) {
        transaction { c ->
            delete(c, "guild_second_vault_slots", guildId)
            c.prepareStatement("INSERT INTO guild_second_vault_slots (guild_id, slot, item_data) VALUES (?, ?, ?)").use { ps ->
                items.forEach { (slot, data) ->
                    ps.setString(1, guildId.toString())
                    ps.setInt(2, slot)
                    ps.setString(3, data)
                    ps.addBatch()
                }
                if (items.isNotEmpty()) ps.executeBatch()
            }
        }
    }

    override fun deleteAll(guildId: UUID) {
        transaction { c ->
            delete(c, "guild_second_vault_slots", guildId)
            delete(c, "guild_second_vaults", guildId)
        }
    }

    private fun delete(c: Connection, table: String, guildId: UUID) {
        c.prepareStatement("DELETE FROM $table WHERE guild_id = ?").use { ps ->
            ps.setString(1, guildId.toString())
            ps.executeUpdate()
        }
    }

    private fun <T> jdbc(block: (Connection) -> T): T = storage.connection.connection.use(block)

    private fun transaction(block: (Connection) -> Unit) = jdbc { c ->
        val auto = c.autoCommit
        c.autoCommit = false
        try {
            block(c)
            c.commit()
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = auto
        }
    }
}
