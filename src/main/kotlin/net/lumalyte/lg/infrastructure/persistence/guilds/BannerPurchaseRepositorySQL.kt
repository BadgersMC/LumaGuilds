package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.BannerPurchase
import net.lumalyte.lg.application.persistence.BannerPurchaseRepository
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import java.util.UUID

class BannerPurchaseRepositorySQL(private val storage: Storage<Database>) : BannerPurchaseRepository {
    init {
        val payload = if (storage.dialect == SqlDialect.MARIADB) "LONGTEXT" else "TEXT"
        storage.connection.executeUpdate("CREATE TABLE IF NOT EXISTS guild_banner_purchases (" +
            "guild_id VARCHAR(36) NOT NULL, player_id VARCHAR(36) NOT NULL, transaction_id VARCHAR(36) NOT NULL UNIQUE, " +
            "amount BIGINT NOT NULL, banner $payload NOT NULL, phase VARCHAR(24) NOT NULL, PRIMARY KEY (guild_id, player_id))")
    }

    @Synchronized
    override fun acquire(guildId: UUID, playerId: UUID, amount: Long, banner: String): BannerPurchase {
        require(amount > 0 && banner.isNotEmpty())
        val existing = get(guildId, playerId)
        if (existing != null && existing.phase !in setOf("COMPLETE", "REJECTED")) return existing
        val id = UUID.randomUUID()
        storage.connection.connection.use { connection ->
            if (existing == null) {
                connection.prepareStatement("INSERT INTO guild_banner_purchases (guild_id, player_id, transaction_id, amount, banner, phase) " +
                    "VALUES (?, ?, ?, ?, ?, 'PENDING')").use {
                    it.setString(1, guildId.toString()); it.setString(2, playerId.toString()); it.setString(3, id.toString())
                    it.setLong(4, amount); it.setString(5, banner)
                    try { it.executeUpdate() } catch (error: java.sql.SQLException) {
                        // A competing first insert is benign only if its owner row actually exists.
                        if (error.errorCode !in setOf(19, 1555, 1062) || get(guildId, playerId) == null) throw error
                    }
                }
            } else {
                connection.prepareStatement("UPDATE guild_banner_purchases SET transaction_id = ?, amount = ?, banner = ?, phase = 'PENDING' " +
                    "WHERE guild_id = ? AND player_id = ? AND transaction_id = ? AND phase IN ('COMPLETE', 'REJECTED')").use {
                    it.setString(1, id.toString()); it.setLong(2, amount); it.setString(3, banner)
                    it.setString(4, guildId.toString()); it.setString(5, playerId.toString()); it.setString(6, existing.id.toString())
                    it.executeUpdate()
                }
            }
        }
        return requireNotNull(get(guildId, playerId))
    }

    private fun get(guildId: UUID, playerId: UUID): BannerPurchase? = storage.connection.connection.use { connection ->
        connection.prepareStatement("SELECT * FROM guild_banner_purchases WHERE guild_id = ? AND player_id = ?").use {
            it.setString(1, guildId.toString()); it.setString(2, playerId.toString())
            it.executeQuery().use { rows -> if (!rows.next()) null else BannerPurchase(
                UUID.fromString(rows.getString("transaction_id")), guildId, playerId,
                rows.getLong("amount"), rows.getString("banner"), rows.getString("phase")) }
        }
    }

    override fun transition(id: UUID, expected: String, next: String): Boolean = storage.connection.connection.use { connection ->
        connection.prepareStatement("UPDATE guild_banner_purchases SET phase = ? WHERE transaction_id = ? AND phase = ?").use {
            it.setString(1, next); it.setString(2, id.toString()); it.setString(3, expected); it.executeUpdate() == 1
        }
    }
}
