package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.values.PrestigeRoleAward
import java.util.UUID

/** REQ-298: members' earned prestige-role tiers. Awards are permanent and never duplicated. */
interface PrestigeRoleAwardRepository {
    /** Stores the awards not already held; returns how many were new. */
    fun record(awards: Collection<PrestigeRoleAward>): Int

    /** The member's highest earned tier, or null. */
    fun highestTier(playerId: UUID): Int?

    /** Every member holding at least one award. */
    fun players(): Set<UUID>
}
