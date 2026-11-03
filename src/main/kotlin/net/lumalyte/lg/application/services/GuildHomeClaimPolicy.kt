package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.Position3D
import java.util.UUID

/**
 * A guild home may only be placed in a claim belonging to that guild when claims are enabled.
 * Missing claim infrastructure, storage errors, and personal/foreign claims all fail closed.
 */
object GuildHomeClaimPolicy {
    /** Existing homes are grandfathered only when the exact persisted world and block position are unchanged. */
    fun allowsChange(
        guildId: UUID,
        proposedHome: GuildHome,
        persistedHome: GuildHome?,
        claimsEnabled: Boolean,
        lookup: (UUID, Position3D) -> GetClaimAtPositionResult?,
    ): Boolean {
        if (persistedHome != null &&
            persistedHome.worldId == proposedHome.worldId &&
            persistedHome.position == proposedHome.position
        ) return true
        return allows(guildId, proposedHome, claimsEnabled, lookup)
    }

    fun allows(
        guildId: UUID,
        home: GuildHome,
        claimsEnabled: Boolean,
        lookup: (UUID, Position3D) -> GetClaimAtPositionResult?,
    ): Boolean {
        if (!claimsEnabled) return true
        return try {
            val result = lookup(home.worldId, home.position)
            result is GetClaimAtPositionResult.Success && result.claim.teamId == guildId
        } catch (_: Exception) {
            false
        }
    }
}
