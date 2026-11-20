package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.DisbandedPrestigeGuilds
import net.lumalyte.lg.domain.rewards.DisbandedPrestigeGuild
import net.lumalyte.lg.infrastructure.persistence.migrations.PrestigeTombstoneSchema
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID

/** Reads the names of disbanded prestiged guilds (REQ-163). */
class PrestigeTombstonesSQL(private val storage: Storage<Database>) : DisbandedPrestigeGuilds {
    override fun disbanded(guildId: UUID): DisbandedPrestigeGuild? =
        storage.connection.getFirstRow(
            "SELECT name, prestige_count, disbanded_at FROM ${PrestigeTombstoneSchema.TABLE} WHERE guild_id = ?",
            guildId.toString(),
        )?.let { row ->
            DisbandedPrestigeGuild(
                guildId = guildId,
                name = row.getString("name"),
                prestigeCount = (row.get<Any>("prestige_count") as Number).toInt(),
                disbandedAt = Instant.ofEpochMilli((row.get<Any>("disbanded_at") as Number).toLong()),
            )
        }
}
