package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.PrestigeRoleAwardRepository
import net.lumalyte.lg.domain.values.PrestigeRoleAward
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.util.UUID

/** REQ-298: additive table of earned prestige-role tiers, one row per member, guild and tier. */
class PrestigeRoleAwardRepositorySQL(private val storage: Storage<Database>) : PrestigeRoleAwardRepository {
    private val mariaDb = storage.dialect == SqlDialect.MARIADB

    init {
        val engine = if (mariaDb) " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci" else ""
        storage.connection.executeUpdate(
            """CREATE TABLE IF NOT EXISTS guild_prestige_role_awards (
                   player_id VARCHAR(36) NOT NULL,
                   guild_id VARCHAR(36) NOT NULL,
                   tier INTEGER NOT NULL,
                   earned_at BIGINT NOT NULL,
                   PRIMARY KEY (player_id, guild_id, tier)
               )$engine"""
        )
    }

    override fun record(awards: Collection<PrestigeRoleAward>): Int = awards.sumOf { award ->
        storage.connection.executeUpdate(
            (if (mariaDb) "INSERT IGNORE" else "INSERT OR IGNORE") +
                " INTO guild_prestige_role_awards (player_id, guild_id, tier, earned_at) VALUES (?, ?, ?, ?)",
            award.playerId.toString(), award.guildId.toString(), award.tier, award.earnedAt.toEpochMilli(),
        )
    }

    override fun highestTier(playerId: UUID): Int? =
        storage.connection.getFirstRow(
            "SELECT MAX(tier) AS tier FROM guild_prestige_role_awards WHERE player_id = ?", playerId.toString(),
        )?.let { (it.get<Any?>("tier") as? Number)?.toInt() }

    override fun players(): Set<UUID> =
        storage.connection.getResults("SELECT DISTINCT player_id FROM guild_prestige_role_awards")
            .map { UUID.fromString(it.getString("player_id")) }.toSet()
}
