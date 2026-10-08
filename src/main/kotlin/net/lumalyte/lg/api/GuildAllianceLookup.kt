@file:Suppress("LibraryEntitiesShouldNotBePublic")

package net.lumalyte.lg.api

import java.util.UUID

/** Optional public access-control contract; pending, ended and enemy relations never grant access. */
interface GuildAllianceLookup {
    /** True only for a currently active alliance; pending or absent relations return false. */
    fun areAllied(guildId: UUID, otherGuildId: UUID): Boolean
}
