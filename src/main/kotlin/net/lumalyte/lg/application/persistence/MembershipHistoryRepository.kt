package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.DepartureReason
import net.lumalyte.lg.domain.entities.MembershipHistory
import java.util.UUID

interface MembershipHistoryRepository {

    /**
     * Opens a new history stint when a player joins a guild.
     * departedAt and departureReason are left null (open stint).
     */
    fun openStint(playerId: UUID, guildId: UUID): Boolean

    /**
     * Closes the most recent open stint for a player in a guild.
     * Sets departedAt = now and departureReason = reason.
     * Safe to call even if no open stint exists (returns false).
     */
    fun closeStint(playerId: UUID, guildId: UUID, reason: DepartureReason): Boolean

    /**
     * Returns all history entries for a player, ordered by joinedAt ASC
     * (oldest first, so index numbers match "total guilds joined").
     */
    fun getByPlayer(playerId: UUID): List<MembershipHistory>
}
