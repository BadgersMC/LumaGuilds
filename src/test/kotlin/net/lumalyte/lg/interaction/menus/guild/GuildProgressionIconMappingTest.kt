package net.lumalyte.lg.interaction.menus.guild

import io.mockk.mockk
import net.lumalyte.lg.domain.values.ExperienceSource
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GuildProgressionIconMappingTest {
    @Test
    fun `every experience source has intentional source artwork`() {
        val menu = GuildProgressionMenu(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk())
        val method = GuildProgressionMenu::class.java
            .getDeclaredMethod("sourceToIconId", ExperienceSource::class.java)
            .apply { isAccessible = true }

        val expected = mapOf(
            ExperienceSource.BANK_DEPOSIT to "lg_deposit",
            ExperienceSource.MEMBER_JOINED to "lg_qualified_recruit",
            ExperienceSource.WAR_WON to "lg_war_victory",
            ExperienceSource.WAR_LOST to "lg_war_defeat",
            ExperienceSource.QUALIFIED_RECRUIT to "lg_qualified_recruit",
            ExperienceSource.PRE_CAP_WAR_WIN to "lg_war_victory",
            ExperienceSource.PLAYER_KILL to "lg_combat",
            ExperienceSource.MOB_KILL to "lg_combat",
            ExperienceSource.CROP_BREAK to "lg_farming",
            ExperienceSource.BLOCK_BREAK to "lg_mining",
            ExperienceSource.BLOCK_PLACE to "lg_block_place",
            ExperienceSource.CRAFTING to "lg_crafting",
            ExperienceSource.SMELTING to "lg_smelting",
            ExperienceSource.FISHING to "lg_fishing",
            ExperienceSource.ENCHANTING to "lg_enchanting",
            ExperienceSource.BREWING to "lg_brewing",
            ExperienceSource.EXPLORATION_MILESTONE to "lg_exploration",
            ExperienceSource.COAL_ORE to "lg_ore",
            ExperienceSource.COPPER_ORE to "lg_ore",
            ExperienceSource.IRON_ORE to "lg_ore",
            ExperienceSource.LAPIS_ORE to "lg_ore",
            ExperienceSource.REDSTONE_ORE to "lg_ore",
            ExperienceSource.GOLD_ORE to "lg_ore",
            ExperienceSource.NETHER_QUARTZ_ORE to "lg_ore",
            ExperienceSource.DIAMOND_ORE to "lg_ore",
            ExperienceSource.EMERALD_ORE to "lg_ore",
            ExperienceSource.ANCIENT_DEBRIS to "lg_ore",
            ExperienceSource.CRAFT_COMMON to "lg_craft_common",
            ExperienceSource.CRAFT_UTILITY to "lg_craft_utility",
            ExperienceSource.CRAFT_EQUIPMENT to "lg_craft_equipment",
            ExperienceSource.CRAFT_RARE to "lg_craft_rare",
            ExperienceSource.ENDER_DRAGON_KILL to "lg_ender_dragon",
            ExperienceSource.WITHER_KILL to "lg_wither",
            ExperienceSource.ELDER_GUARDIAN_KILL to "lg_elder_guardian",
            ExperienceSource.WARDEN_KILL to "lg_warden",
            ExperienceSource.CLAIM_CREATED to "lg_claiming",
            ExperienceSource.CLAIM_DESTROYED to "lg_claim_removed",
            ExperienceSource.WEEKLY_ACTIVITY to "lg_weekly_activity",
            ExperienceSource.ADMIN_BONUS to "lg_admin_bonus",
        )

        assertEquals(ExperienceSource.entries.toSet(), expected.keys)
        expected.forEach { (source, icon) ->
            assertEquals(icon, method.invoke(menu, source), source.name)
        }
    }
    @Test
    fun `pooled presentation uses pool meaning instead of representative tier`() {
        val menu = GuildProgressionMenu(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk())
        val method = GuildProgressionMenu::class.java
            .getDeclaredMethod("sourceToPresentationIconId", ExperienceSource::class.java, String::class.java)
            .apply { isAccessible = true }

        assertEquals("lg_ore", method.invoke(menu, ExperienceSource.DIAMOND_ORE, "ORE"))
        assertEquals("lg_crafting", method.invoke(menu, ExperienceSource.CRAFT_RARE, "CRAFTING"))
        assertEquals("lg_brewing", method.invoke(menu, ExperienceSource.BREWING, "BREWING"))
    }

}
