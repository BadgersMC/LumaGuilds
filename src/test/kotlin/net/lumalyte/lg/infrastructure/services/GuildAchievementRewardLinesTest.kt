package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.achievements.GuildAchievement
import kotlin.test.Test
import kotlin.test.assertEquals

/** REQ-241: guild advancement tooltips name the guild XP reward. */
class GuildAchievementRewardLinesTest {
    private fun node(personal: Int, guild: Int) = GuildAchievement(
        id = "forge_of_industry", parent = "industrial_revolution", title = "Forge of Industry",
        description = listOf("Smelt or cook 100,000 items collectively"), requirement = "GUILD_COLLECTIVE_SMELT",
        target = null, goal = 100000, personalXp = personal, messages = emptyList(), category = "Cooperation",
        icon = "BLAST_FURNACE", frame = "CHALLENGE", x = 3f, y = 16.5f, guildXp = guild,
    )

    @Test
    fun `guild xp leads and personal xp is the members' extra`() {
        assertEquals(
            listOf("Reward: 10,000 guild XP", "Members also get 1,000 personal Minecraft XP"),
            rewardLines(node(1000, 10000)),
        )
    }

    @Test
    fun `only the rewards that are configured are listed`() {
        assertEquals(listOf("Reward: 500 guild XP"), rewardLines(node(0, 500)))
        assertEquals(listOf("Reward: 300 personal Minecraft XP"), rewardLines(node(300, 0)))
        assertEquals(emptyList(), rewardLines(node(0, 0)))
    }
}
