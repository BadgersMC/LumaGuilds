package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildBan
import java.util.UUID

/** REQ-194: saved guild bans. Reads throw when the database cannot be read. */
interface GuildBanRepository {
    fun isBanned(guildId: UUID, playerId: UUID): Boolean

    fun bans(guildId: UUID): List<GuildBan>

    /** Saves [ban]; false when it was already saved. */
    fun add(ban: GuildBan): Boolean

    /** Deletes the ban; false when there was none. */
    fun remove(guildId: UUID, playerId: UUID): Boolean

    /** Deletes every ban of a disbanded guild. */
    fun removeGuild(guildId: UUID)
}
