@file:Suppress("LibraryEntitiesShouldNotBePublic")

package net.lumalyte.lg.api

import net.lumalyte.lg.application.services.InviteBlockService
import java.util.UUID

/**
 * Cross-plugin access to personal invite blocks (EnthusiaFriends' Block Everywhere uses it). Registered in Bukkit's
 * ServicesManager; JDK types only, like [GuildLookup]. Synchronous: reads come from memory, [setBlocked] writes to the
 * database before returning, so call it off the main thread when possible.
 *
 * A block stops guild invites from the target and from any guild they are in, and hides their guild, ally and party
 * chat messages from the owner. It never changes memberships.
 */
interface GuildInviteBlocks {
    /** Version of this API; 1 for this surface. */
    fun apiVersion(): Int = 1

    /** Returns whether the owner personally blocked the target. */
    fun isBlocked(ownerId: UUID, targetId: UUID): Boolean

    /** True when the block is saved (or already in that state); false when it could not be saved. */
    fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean

    /** Returns a detached set of personally blocked player identities. */
    fun getBlocked(ownerId: UUID): Set<UUID>
}

/** Bridges the registered JDK-only API to the application service. */
internal class GuildInviteBlocksImpl(private val blocks: InviteBlockService) : GuildInviteBlocks {
    override fun isBlocked(ownerId: UUID, targetId: UUID): Boolean = blocks.isBlocked(ownerId, targetId)

    override fun setBlocked(ownerId: UUID, targetId: UUID, blocked: Boolean): Boolean =
        blocks.setBlocked(ownerId, targetId, blocked)

    override fun getBlocked(ownerId: UUID): Set<UUID> = blocks.blockedBy(ownerId)
}
