package net.lumalyte.lg.infrastructure.services

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.GuildRecruitmentEligibility
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.bukkit.Bukkit
import java.util.UUID

/** Reads the reviewed invite-block table contract when present, without modifying that separate feature. */
internal class GuildRecruitmentEligibilityBukkit(
    private val storage: Storage<Database>,
    private val members: MemberService,
    private val banned: (UUID) -> Boolean = { Bukkit.getOfflinePlayer(it).isBanned },
) : GuildRecruitmentEligibility {
    /** Banned players and players refusing any member of a guild cannot enter through recruitment. */
    override fun allows(player: UUID, guild: UUID): Boolean {
        if (banned(player)) return false
        val blocked =
            storage.connection.connection.use { connection ->
                val exists =
                    connection.metaData.getTables(
                        connection.catalog,
                        null,
                        "player_invite_blocks",
                        arrayOf("TABLE"),
                    )
                        .use { it.next() }
                if (!exists) return@use emptySet<UUID>() // Legacy main has no invite-block feature yet.
                connection.prepareStatement("SELECT target_id FROM player_invite_blocks WHERE owner_id = ?").use {
                        statement ->
                    statement.setString(1, player.toString())
                    statement.executeQuery().use {
                            rows ->
                        buildSet { while (rows.next()) add(UUID.fromString(rows.getString(1))) }
                    }
                }
            }
        return members.getGuildMembers(guild).none { it.playerId in blocked }
    }
}
