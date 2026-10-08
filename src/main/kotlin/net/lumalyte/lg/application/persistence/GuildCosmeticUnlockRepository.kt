package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildCosmeticUnlock
import java.util.UUID

/** Durable guild cosmetic ownership (REQ-121). */
@Suppress("LibraryEntitiesShouldNotBePublic")
interface GuildCosmeticUnlockRepository {
    fun getForGuild(guildId: UUID): List<GuildCosmeticUnlock>

    fun get(guildId: UUID, type: String, key: String): GuildCosmeticUnlock?

    /** Records [unlock] unless already owned. True when the guild owns it afterwards; false on persistence failure. */
    fun saveIfAbsent(unlock: GuildCosmeticUnlock): Boolean

    /** Removes ownership. True when the guild no longer owns it (including if it never did). */
    fun delete(guildId: UUID, type: String, key: String): Boolean
}
