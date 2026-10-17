package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.application.services.MemberService
import java.util.UUID

/** Enforces personal block policy while preserving guild memberships. */
internal class InviteBlockServiceImpl(
    private val blocks: InviteBlockRepository,
    private val members: MemberService,
    private val invitations: GuildInvitationRepository,
) : InviteBlockService {
    /** Reads a personal directional block through the persistence port cache. */
    override fun isBlocked(ownerId: UUID, targetId: UUID): Boolean = blocks.isBlocked(ownerId, targetId)

    /** Returns the persistence port detached snapshot of this owner blocks. */
    override fun blockedBy(ownerId: UUID): Set<UUID> = blocks.blockedBy(ownerId)

    /** Saves the personal block and removes pending invites from its target or their current guilds. */
    override fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean {
        if (!blocks.setBlocked(ownerId, targetId, blocked)) return false
        if (blocked) {
            // Pending invites the block now covers: sent by them, or from a guild they are in.
            val theirGuilds = members.getPlayerGuilds(targetId)
            invitations.getByPlayer(ownerId)
                .filter { it.inviterPlayerId == targetId || it.guildId in theirGuilds }
                .forEach { invitations.remove(ownerId, it.guildId) }
        }
        return true
    }

    /** Fails closed on unavailable storage and checks blocked inviter/current guild members by identity. */
    override fun refusesGuildInvite(invitedId: UUID, guildId: UUID, inviterId: UUID?): Boolean {
        val blocked = blocks.blockedBy(invitedId)
        return when {
            !blocks.isAvailable -> true
            blocked.isEmpty() -> false
            inviterId != null && inviterId in blocked -> true
            else -> blocked.any { members.isPlayerInGuild(it, guildId) }
        }
    }

    /** Filters only the sender identity personally blocked by this viewer. */
    override fun hidesChat(viewerId: UUID, senderId: UUID): Boolean = blocks.isBlocked(viewerId, senderId)
}
