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
     * Blocks or unblocks [targetId] for [ownerId]. True confirms the SQL state and cache; false can also mean an
     * unknown write outcome and never promises rollback. A saved block attempts synchronous pending-invite cleanup,
     * with cleanup failures diagnosed separately. Idempotent, including when the cache already matches.
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
