package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

/** The admin-applied penalties a guild can receive once it reaches the strike threshold. */
enum class PenaltyType {
    LEVEL_REDUCTION,
    EXP_REDUCTION,
    GUILD_MUTE,
    DISBAND
}

/**
 * A penalty applied to a guild by staff, recorded for audit and public display.
 *
 * [amount] is interpreted per type: levels removed for LEVEL_REDUCTION, XP
 * removed for EXP_REDUCTION, mute duration in milliseconds for GUILD_MUTE.
 */
data class GuildPenalty(
    val id: Long = 0,
    val guildId: UUID,
    val type: PenaltyType,
    val amount: Long? = null,
    val reason: String? = null,
    val actorUuid: UUID,
    val actorName: String,
    val createdAt: Instant
) {
    /** True while a GUILD_MUTE penalty is still in force. */
    val isMuteActive: Boolean
        get() = type == PenaltyType.GUILD_MUTE && amount != null && amount > 0 && createdAt.plusMillis(amount).isAfter(Instant.now())
}
