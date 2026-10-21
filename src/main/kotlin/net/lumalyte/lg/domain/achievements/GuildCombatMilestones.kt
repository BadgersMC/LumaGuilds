package net.lumalyte.lg.domain.achievements

/** Current-stat badges, not retained awards or personal XP achievements. */
data class GuildCombatMilestone(
    val key: String,
    val title: String,
    val condition: String,
    val progress: String,
    val achieved: Boolean,
)

object GuildCombatMilestones {
    fun evaluate(kills: Int, deaths: Int, netKills: Int, ratio: Double, wars: Int, wins: Int): List<GuildCombatMilestone> = listOf(
        GuildCombatMilestone("first_blood", "First Blood", "At least 1 total guild kill.", "$kills / 1 kills", kills >= 1),
        GuildCombatMilestone("centurion", "Centurion", "At least 100 total guild kills.", "$kills / 100 kills", kills >= 100),
        GuildCombatMilestone("warlord", "Warlord", "At least 1,000 total guild kills.", "$kills / 1,000 kills", kills >= 1000),
        GuildCombatMilestone("survivor", "Survivor", "Fight at least 10 wars in retained history.", "$wars / 10 wars", wars >= 10),
        GuildCombatMilestone("champion", "Champion", "Win at least 5 wars in retained history.", "$wins / 5 wins", wins >= 5),
        GuildCombatMilestone("veteran", "Veteran", "Fight at least 25 wars in retained history.", "$wars / 25 wars", wars >= 25),
        GuildCombatMilestone("net_positive", "Net Positive", "Have more guild kills than deaths (net kills above zero).", "$netKills net kills", netKills > 0),
        GuildCombatMilestone("well_rounded", "Well Rounded", "At least 1 kill, 1 death, a positive K/D ratio and 1 retained war.",
            "$kills kills · $deaths deaths · $ratio K/D · $wars wars", kills > 0 && deaths > 0 && ratio > 0.0 && wars > 0),
    )
}
