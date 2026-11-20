package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildBanRepository
import net.lumalyte.lg.domain.entities.GuildBan
import net.lumalyte.lg.domain.entities.RankPermission
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * REQ-194. [rankPriority] is a player's rank priority in a guild (null when not a member);
 * [removeInvites] deletes the guild's pending invites to a player.
 *
 * The ban is saved before a member is removed, so a failed save never leaves a kicked player
 * free to rejoin; if the removal then fails the ban is taken back.
 */
class GuildBanServiceImpl(
    private val repository: GuildBanRepository,
    private val memberService: MemberService,
    private val rankPriority: (playerId: UUID, guildId: UUID) -> Int?,
    private val removeInvites: (playerId: UUID, guildId: UUID) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
) : GuildBanService {
    private val logger = LoggerFactory.getLogger(GuildBanServiceImpl::class.java)

    override fun isBanned(guildId: UUID, playerId: UUID): Boolean = try {
        repository.isBanned(guildId, playerId)
    } catch (error: Exception) {
        logger.error("Unable to read bans for guild $guildId; treating $playerId as banned", error)
        true
    }

    override fun bans(guildId: UUID): List<GuildBan> = try {
        repository.bans(guildId).sortedByDescending { it.bannedAt }
    } catch (error: Exception) {
        logger.error("Unable to list bans for guild $guildId", error)
        emptyList()
    }

    override fun check(actorId: UUID, guildId: UUID, targetId: UUID): BanResult {
        if (!memberService.hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) return BanResult.NoPermission
        if (actorId == targetId) return BanResult.Self
        val target = rankPriority(targetId, guildId)
        if (target == 0) return BanResult.Owner
        if (target != null) {
            val actor = rankPriority(actorId, guildId) ?: return BanResult.NoPermission
            if (actor >= target) return BanResult.NotOutranked
        }
        val already = try {
            repository.isBanned(guildId, targetId)
        } catch (error: Exception) {
            logger.error("Unable to read bans for guild $guildId", error)
            return BanResult.Failed
        }
        return if (already) BanResult.AlreadyBanned else BanResult.Banned(wasMember = target != null)
    }

    override fun ban(actorId: UUID, guildId: UUID, targetId: UUID): BanResult {
        val checked = check(actorId, guildId, targetId)
        if (checked !is BanResult.Banned) return checked
        val saved = try {
            repository.add(GuildBan(guildId, targetId, actorId, clock.instant()))
        } catch (error: Exception) {
            logger.error("Unable to save ban of $targetId from guild $guildId", error)
            return BanResult.Failed
        }
        if (!saved) return BanResult.AlreadyBanned
        if (checked.wasMember && !memberService.removeMember(targetId, guildId, actorId)) {
            runCatching { repository.remove(guildId, targetId) }
                .onFailure { logger.error("Unable to take back ban of $targetId from guild $guildId", it) }
            return BanResult.Failed
        }
        runCatching { removeInvites(targetId, guildId) }
            .onFailure { logger.warn("Unable to delete invites from guild $guildId to $targetId", it) }
        return checked
    }

    override fun unban(actorId: UUID, guildId: UUID, targetId: UUID): UnbanResult {
        if (!memberService.hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) return UnbanResult.NO_PERMISSION
        return try {
            if (repository.remove(guildId, targetId)) UnbanResult.UNBANNED else UnbanResult.NOT_BANNED
        } catch (error: Exception) {
            logger.error("Unable to unban $targetId from guild $guildId", error)
            UnbanResult.FAILED
        }
    }
}
