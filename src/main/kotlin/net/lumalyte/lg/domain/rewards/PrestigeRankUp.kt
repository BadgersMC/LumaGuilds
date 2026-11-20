package net.lumalyte.lg.domain.rewards

/**
 * REQ-251/252: timing of the prestige rank-up animation, which plays one resource-pack frame per tick.
 * Frames come from the Enthusia pack (docs/prestige-rankup.md in the pack repo): tier N, frame F is
 * character `U+E000 + N*0x100 + F` in the font [FONT].
 */
object PrestigeRankUp {
    const val FONT = "lumaguilds:prestige_rankup_v1"
    const val CONTRACT_ID = "guild_prestige_rankup_v1"
    const val CONTRACT_FONT = "lumaguilds:prestige_rankup_contract"
    const val FRAMES = 64

    /** Ticks the members' fanfare starts after the animation, so the chime lands as the new badge appears. */
    const val FANFARE_LEAD = 7

    /** Tick the previous badge shatters (tiers II to VI; Prestige I starts from a spark). */
    const val SHATTER_TICK = 11

    fun char(tier: Int, frame: Int): Char {
        require(tier in 1..6) { "Prestige tier must be 1 to 6" }
        require(frame in 0 until FRAMES) { "Frame must be 0 to ${FRAMES - 1}" }
        return (0xE000 + tier * 0x100 + frame).toChar()
    }

    /** First frame that shows the guild name under the title, once "PRESTIGE N" has wiped in. */
    fun subtitleFrame(tier: Int): Int = if (tier == 1) 26 else 36

    /** The members' fanfare with the animation: the same cues, later, plus the shatter for tiers II to VI. */
    fun memberCues(tier: Int): List<FanfareCue> {
        val fanfare = PrestigeFanfareScore.cues(tier, FanfareAudience.MEMBERS).map { it.copy(tick = it.tick + FANFARE_LEAD) }
        val shatter = if (tier > 1) listOf(FanfareCue(SHATTER_TICK, "block.amethyst_cluster.break", .8f, 1.2f)) else emptyList()
        return (shatter + fanfare).sortedBy { it.tick }
    }
}
