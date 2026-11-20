package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildBanRepository
import net.lumalyte.lg.domain.entities.GuildBan
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID

/** REQ-194: bans in `guild_bans`, created here like the member capacity tables. Reads go to SQL each time. */
internal class GuildBanRepositorySQL(private val storage: Storage<Database>) : GuildBanRepository {
    init {
        val engine = if (storage.dialect == SqlDialect.MARIADB) " ENGINE=InnoDB" else ""
        storage.connection.connection.use { c ->
            c.createStatement().use {
                it.execute("""CREATE TABLE IF NOT EXISTS guild_bans (
                    guild_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    banned_by VARCHAR(36) NOT NULL,
                    banned_at BIGINT NOT NULL,
                    PRIMARY KEY (guild_id, player_id)
                )$engine""")
            }
        }
    }

    override fun isBanned(guildId: UUID, playerId: UUID): Boolean =
        storage.connection.connection.use { c ->
            c.prepareStatement("SELECT 1 FROM guild_bans WHERE guild_id = ? AND player_id = ?").use { st ->
                st.setString(1, guildId.toString())
                st.setString(2, playerId.toString())
                st.executeQuery().use { it.next() }
            }
        }

    override fun bans(guildId: UUID): List<GuildBan> =
        storage.connection.connection.use { c ->
            c.prepareStatement("SELECT player_id, banned_by, banned_at FROM guild_bans WHERE guild_id = ?").use { st ->
                st.setString(1, guildId.toString())
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                GuildBan(
                                    guildId,
                                    UUID.fromString(rs.getString(1)),
                                    UUID.fromString(rs.getString(2)),
                                    Instant.ofEpochMilli(rs.getLong(3)),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun add(ban: GuildBan): Boolean =
        storage.connection.connection.use { c ->
            val insert = if (storage.dialect == SqlDialect.MARIADB) "INSERT IGNORE" else "INSERT OR IGNORE"
            c.prepareStatement("$insert INTO guild_bans (guild_id, player_id, banned_by, banned_at) VALUES (?, ?, ?, ?)").use { st ->
                st.setString(1, ban.guildId.toString())
                st.setString(2, ban.playerId.toString())
                st.setString(3, ban.bannedBy.toString())
                st.setLong(4, ban.bannedAt.toEpochMilli())
                st.executeUpdate() == 1
            }
        }

    override fun remove(guildId: UUID, playerId: UUID): Boolean =
        storage.connection.connection.use { c ->
            c.prepareStatement("DELETE FROM guild_bans WHERE guild_id = ? AND player_id = ?").use { st ->
                st.setString(1, guildId.toString())
                st.setString(2, playerId.toString())
                st.executeUpdate() > 0
            }
        }

    override fun removeGuild(guildId: UUID) {
        storage.connection.connection.use { c ->
            c.prepareStatement("DELETE FROM guild_bans WHERE guild_id = ?").use { st ->
                st.setString(1, guildId.toString())
                st.executeUpdate()
            }
        }
    }
}
