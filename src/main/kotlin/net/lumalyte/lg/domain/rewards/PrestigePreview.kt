package net.lumalyte.lg.domain.rewards

/**
 * REQ-249: everything one prestige would give a guild, worked out before it confirms, so the
 * confirmation can list exactly what it earns. Nullable fields are left out of the summary.
 */
data class PrestigePreview(
    val tier: Int,
    val finalTier: Boolean,
    val homeSlotsBefore: Int,
    val homeSlotsAfter: Int,
    val perks: List<PrestigePerk>,
    /** XP carried into the next climb and the level it starts at; null when carry-over is off. */
    val carry: Carry?,
    /** The member limit kept on the first prestige; null when level-based limits are off or not the first prestige. */
    val memberLimitKept: Int?,
    /** The Discord role minimum level waived on the first prestige; null when not applicable. */
    val discordRoleMinimumLevel: Int?,
    /** Tenure hours members need for the tier advancement; null when member advancements are off. */
    val memberCreditTenureHours: Long?,
    val firstOnServer: Boolean,
    val fanfare: Boolean,
    val discordPost: Boolean,
    val fee: Long,
) {
    data class Carry(val experience: Int, val startLevel: Int)
}

/** One line of the confirmation summary, in display order. */
sealed interface PrestigeSummaryLine {
    data class Heading(val tier: Int) : PrestigeSummaryLine
    data object FinalPrestige : PrestigeSummaryLine
    data object Gap : PrestigeSummaryLine
    data object EarnHeading : PrestigeSummaryLine
    data class Badge(val tier: Int) : PrestigeSummaryLine
    data object RetainedReward : PrestigeSummaryLine
    data object RetainedEffect : PrestigeSummaryLine
    data class HomeSlot(val before: Int, val after: Int) : PrestigeSummaryLine
    /** REQ-260: the Hall theme chosen for this prestige and how many the guild will own. */
    data class HallThemeEarned(val theme: HallTheme, val ownedAfter: Int) : PrestigeSummaryLine
    data class Perk(val perk: PrestigePerk) : PrestigeSummaryLine
    data class MemberLimit(val limit: Int) : PrestigeSummaryLine
    data class DiscordRole(val minimumLevel: Int) : PrestigeSummaryLine
    data class CarriedXp(val experience: Int, val level: Int) : PrestigeSummaryLine
    data class MemberAdvancement(val tier: Int, val hours: Long) : PrestigeSummaryLine
    data class FirstOnServer(val tier: Int) : PrestigeSummaryLine
    data class Announced(val fanfare: Boolean, val discord: Boolean) : PrestigeSummaryLine
    data object CostHeading : PrestigeSummaryLine
    data class Fee(val fee: Long) : PrestigeSummaryLine
    data class LevelReset(val xpKept: Boolean) : PrestigeSummaryLine
    data object RewardsReset : PrestigeSummaryLine
    data object KeepHeading : PrestigeSummaryLine
    data object Kept : PrestigeSummaryLine
    data object Overflow : PrestigeSummaryLine
}

object PrestigeSummary {
    /** REQ-249: earned, cost and kept, each line only when it is true for this guild and tier. */
    fun lines(
        preview: PrestigePreview,
        hasRetainedEffect: Boolean,
        hallTheme: PrestigeSummaryLine.HallThemeEarned? = null,
    ): List<PrestigeSummaryLine> = buildList {
        add(PrestigeSummaryLine.Heading(preview.tier))
        if (preview.finalTier) add(PrestigeSummaryLine.FinalPrestige)
        add(PrestigeSummaryLine.Gap)
        add(PrestigeSummaryLine.EarnHeading)
        add(PrestigeSummaryLine.Badge(preview.tier))
        add(PrestigeSummaryLine.RetainedReward)
        if (hasRetainedEffect) add(PrestigeSummaryLine.RetainedEffect)
        add(PrestigeSummaryLine.HomeSlot(preview.homeSlotsBefore, preview.homeSlotsAfter))
        hallTheme?.let(::add)
        preview.perks.forEach { add(PrestigeSummaryLine.Perk(it)) }
        preview.memberLimitKept?.let { add(PrestigeSummaryLine.MemberLimit(it)) }
        preview.discordRoleMinimumLevel?.let { add(PrestigeSummaryLine.DiscordRole(it)) }
        preview.carry?.takeIf { it.experience > 0 }?.let { add(PrestigeSummaryLine.CarriedXp(it.experience, it.startLevel)) }
        preview.memberCreditTenureHours?.let { add(PrestigeSummaryLine.MemberAdvancement(preview.tier, it)) }
        if (preview.firstOnServer) add(PrestigeSummaryLine.FirstOnServer(preview.tier))
        add(PrestigeSummaryLine.Announced(preview.fanfare, preview.discordPost))
        add(PrestigeSummaryLine.Gap)
        add(PrestigeSummaryLine.CostHeading)
        add(PrestigeSummaryLine.Fee(preview.fee))
        add(PrestigeSummaryLine.LevelReset(xpKept = (preview.carry?.experience ?: 0) > 0))
        add(PrestigeSummaryLine.RewardsReset)
        add(PrestigeSummaryLine.Gap)
        add(PrestigeSummaryLine.KeepHeading)
        add(PrestigeSummaryLine.Kept)
        add(PrestigeSummaryLine.Overflow)
    }
}
