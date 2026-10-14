@file:Suppress("LibraryEntitiesShouldNotBePublic") // Used by public compatibility adapter constructors.

package net.lumalyte.lg.application.services

import java.util.UUID

/**
 * Personal invite blocks. A player who blocks someone gets no guild invites from them or from any guild they are in
 * (whoever sends it), and no longer sees their messages in guild, ally or party chat. Memberships are not changed.
 */
interface InviteBlockService {
    /** Checks a personal block without accessing SQL. */
    fun isBlocked(ownerId: UUID, targetId: UUID): Boolean

    /** Returns a detached snapshot of personally blocked identities. */
    fun blockedBy(ownerId: UUID): Set<UUID>

    /**
     * Blocks or unblocks [targetId] for [ownerId], saved durably; a new block also deletes the pending invites it
     * covers. False when it could not be saved. Idempotent.
     */
    fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean

    /**
     * Whether [invitedId] refuses an invite to [guildId] sent by [inviterId]: they blocked the inviter or a member of
     * the guild. Also true when the blocks could not be loaded (fail closed).
     */
    fun refusesGuildInvite(invitedId: UUID, guildId: UUID, inviterId: UUID?): Boolean

    /** Whether [viewerId] should not see [senderId]'s guild, ally and party chat messages. */
    fun hidesChat(viewerId: UUID, senderId: UUID): Boolean
}
