package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.values.PrestigeNumeral
import java.util.UUID

/**
 * REQ-162: what a guild's owner is told before a disband. Guilds that never
 * prestiged keep the old flow; a prestiged guild, or one whose tier cannot be
 * read, must go through the confirmation screen and see the warning.
 */
sealed interface DisbandPrestigeWarning {
    val needsConfirmation: Boolean

    data object None : DisbandPrestigeWarning { override val needsConfirmation = false }
    data class Tier(val tier: Int) : DisbandPrestigeWarning {
        override val needsConfirmation = true

        /** The tier as players see it on badges: I to VI. */
        val roman: String get() = PrestigeNumeral.of(tier)
    }
    data object Unknown : DisbandPrestigeWarning { override val needsConfirmation = true }

    companion object {
        fun of(standings: GuildPrestigeStandings, guildId: UUID): DisbandPrestigeWarning {
            val standing = runCatching { standings.standing(guildId) }.getOrNull() ?: return Unknown
            return if (standing.tier > 0) Tier(standing.tier) else None
        }
    }
}
