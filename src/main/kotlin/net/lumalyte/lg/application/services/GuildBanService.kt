package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.GuildBan
import java.util.UUID

/** REQ-194: outcome of a ban. */
sealed interface BanResult {
    data class Banned(val wasMember: Boolean) : BanResult
    data object NoPermission : BanResult
    data object Self : BanResult
    data object Owner : BanResult
    data object NotOutranked : BanResult
    data object AlreadyBanned : BanResult
    data object Failed : BanResult
}

/** REQ-194: outcome of an unban. */
enum class UnbanResult { UNBANNED, NO_PERMISSION, NOT_BANNED, FAILED }

/** REQ-194: guild bans. */
interface GuildBanService {
    /** True when banned, and also when the ban list cannot be read (fails closed). */
    fun isBanned(guildId: UUID, playerId: UUID): Boolean

    fun bans(guildId: UUID): List<GuildBan>

    /** What [ban] would return, without changing anything. */
    fun check(actorId: UUID, guildId: UUID, targetId: UUID): BanResult

    fun ban(actorId: UUID, guildId: UUID, targetId: UUID): BanResult

    fun unban(actorId: UUID, guildId: UUID, targetId: UUID): UnbanResult
}
