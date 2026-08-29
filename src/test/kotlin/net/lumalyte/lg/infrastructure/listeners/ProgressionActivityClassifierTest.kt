package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.config.ChapterTwoTargetPools
import net.lumalyte.lg.domain.values.ExperienceSource
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.EntityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressionActivityClassifierTest {
    private val classifier = ProgressionActivityClassifier(
        ChapterTwoTargetPools.defaultMaterials(),
        ChapterTwoTargetPools.defaultEntities()
    )

    @Test
    fun `creative and spectator activity is ineligible`() {
        assertFalse(classifier.isEligible(GameMode.CREATIVE))
        assertFalse(classifier.isEligible(GameMode.SPECTATOR))
        assertTrue(classifier.isEligible(GameMode.SURVIVAL))
        assertTrue(classifier.isEligible(GameMode.ADVENTURE))
    }

    @Test
    fun `natural ores use their tier source while placed ores earn nothing`() {
        assertEquals(ExperienceSource.DIAMOND_ORE, classifier.sourceForBreak(Material.DIAMOND_ORE, false, false))
        assertEquals(ExperienceSource.COPPER_ORE, classifier.sourceForBreak(Material.DEEPSLATE_COPPER_ORE, false, false))
        assertNull(classifier.sourceForBreak(Material.DIAMOND_ORE, true, false))
    }

    @Test
    fun `only mature crops and configured common blocks qualify`() {
        assertEquals(ExperienceSource.CROP_BREAK, classifier.sourceForBreak(Material.WHEAT, false, true))
        assertNull(classifier.sourceForBreak(Material.WHEAT, false, false))
        assertEquals(ExperienceSource.BLOCK_BREAK, classifier.sourceForBreak(Material.STONE, false, false))
        assertNull(classifier.sourceForBreak(Material.CUT_COPPER, false, false))
    }

    @Test
    fun `bosses use dedicated sources without location conditions`() {
        assertEquals(ExperienceSource.ENDER_DRAGON_KILL, classifier.sourceForKill(EntityType.ENDER_DRAGON))
        assertEquals(ExperienceSource.WITHER_KILL, classifier.sourceForKill(EntityType.WITHER))
        assertEquals(ExperienceSource.ELDER_GUARDIAN_KILL, classifier.sourceForKill(EntityType.ELDER_GUARDIAN))
        assertEquals(ExperienceSource.WARDEN_KILL, classifier.sourceForKill(EntityType.WARDEN))
        assertEquals(ExperienceSource.MOB_KILL, classifier.sourceForKill(EntityType.ZOMBIE))
        assertNull(classifier.sourceForKill(EntityType.COW))
    }

    @Test
    fun `craft output chooses configured shared-pool tier`() {
        assertEquals(ExperienceSource.CRAFT_COMMON, classifier.sourceForCraft(Material.TORCH))
        assertEquals(ExperienceSource.CRAFT_UTILITY, classifier.sourceForCraft(Material.BUCKET))
        assertEquals(ExperienceSource.CRAFT_EQUIPMENT, classifier.sourceForCraft(Material.DIAMOND_SWORD))
        assertEquals(ExperienceSource.CRAFT_RARE, classifier.sourceForCraft(Material.BEACON))
        assertNull(classifier.sourceForCraft(Material.WARPED_FUNGUS_ON_A_STICK))
    }

    @Test
    fun `brewed potions and vanilla adventure advancements are eligible milestones`() {
        assertTrue(classifier.isBrewedPotion(Material.POTION))
        assertTrue(classifier.isBrewedPotion(Material.SPLASH_POTION))
        assertFalse(classifier.isBrewedPotion(Material.GLASS_BOTTLE))
        assertTrue(classifier.isExplorationMilestone("minecraft", "adventure/adventuring_time"))
        assertFalse(classifier.isExplorationMilestone("minecraft", "story/mine_stone"))
        assertFalse(classifier.isExplorationMilestone("custom", "adventure/example"))
    }
}
