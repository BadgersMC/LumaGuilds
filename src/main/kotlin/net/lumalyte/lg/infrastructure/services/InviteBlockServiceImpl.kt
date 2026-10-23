package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.application.persistence.InviteBlockWriteOutcome
import net.lumalyte.lg.application.persistence.InviteBlockWriteResult
import net.lumalyte.lg.application.services.DurableInviteBlockService
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.application.services.MemberService
import org.slf4j.LoggerFactory
import java.util.UUID

/** Enforces personal block policy while preserving guild memberships. */
internal class InviteBlockServiceImpl(
    private val blocks: InviteBlockRepository,
    private val members: MemberService,
    private val invitations: GuildInvitationRepository,
) : InviteBlockService, DurableInviteBlockService {
    private var closed = false
    private val logger = LoggerFactory.getLogger(InviteBlockServiceImpl::class.java)

    /** Reads a personal directional block through the persistence port cache. */
    override fun isBlocked(ownerId: UUID, targetId: UUID): Boolean = blocks.isBlocked(ownerId, targetId)

    /** Returns the persistence port detached snapshot of this owner blocks. */
    override fun blockedBy(ownerId: UUID): Set<UUID> = blocks.blockedBy(ownerId)

    /** Saves the personal block and attempts cleanup of invites from its target or their current guilds. */
    @Synchronized
    override fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean {
        if (closed || !blocks.setBlocked(ownerId, targetId, blocked)) return false
        if (blocked) removeCoveredInvites(ownerId, targetId)
        return true
    }

    /** Holds the same lifecycle gate as writes, so a captured closed provider cannot access its former store. */
    @Synchronized
    override fun isBlockedDurable(ownerId: UUID, targetId: UUID): Boolean {
        check(!closed) { "Guild invite block provider is closed" }
        return blocks.isBlockedDurable(ownerId, targetId)
    }

    /** Returns only after SQL, cache reconciliation, and synchronous best-effort invitation cleanup settle. */
    @Synchronized
    override fun setBlockedDurable(ownerId: UUID, targetId: UUID, blocked: Boolean): InviteBlockWriteResult {
        if (closed) {
            return InviteBlockWriteResult(
                InviteBlockWriteOutcome.NOT_APPLIED,
                failure = IllegalStateException("Guild invite block provider is closed"),
            )
        }
        val result = blocks.setBlockedDurable(ownerId, targetId, blocked)
        if (blocked && result.outcome == InviteBlockWriteOutcome.COMMITTED) removeCoveredInvites(ownerId, targetId)
        return result
    }

    /** Acquires the operation monitor before closing, draining active calls without scheduling further work. */
    @Synchronized
    override fun close() {
        closed = true
    }

    private fun removeCoveredInvites(ownerId: UUID, targetId: UUID) {
        try {
            // Pending invites the block now covers: sent by them, or from a guild they are in.
            val theirGuilds = members.getPlayerGuilds(targetId)
            invitations.getByPlayer(ownerId)
                .filter { it.inviterPlayerId == targetId || it.guildId in theirGuilds }
                .forEach { invitation ->
                    if (!invitations.remove(ownerId, invitation.guildId)) {
                        logger.warn(
                            "Invite block is committed; pending invite removal is unconfirmed {} -> {}",
                            ownerId,
                            invitation.guildId,
                        )
                    }
                }
        } catch (cleanup: Exception) {
            logger.warn(
                "Invite block is committed; pending invitation cleanup failed {} -> {}",
                ownerId,
                targetId,
                cleanup,
            )
        }
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
