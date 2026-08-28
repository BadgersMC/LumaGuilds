package net.lumalyte.lg.config

import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File

/** Shipped balance and reload validation contract for REQ-049/REQ-089. */
class ProgressionSourceConfigTest {

    @Test
    fun `default policies match chapter two balance model`() {
        val policies = ConfigServiceBukkit(YamlConfiguration()).loadConfig().progression.sourcePolicies

        assertPolicy(policies, ExperienceSource.MOB_KILL, 2, 6_000, CapPeriod.DAILY, "MOB_KILL")
        assertPolicy(policies, ExperienceSource.PLAYER_KILL, 100, 6_000, CapPeriod.DAILY, "PLAYER_KILL")
        assertPolicy(policies, ExperienceSource.BLOCK_BREAK, 2, 12_000, CapPeriod.DAILY, "BLOCK_BREAK")
        assertPolicy(policies, ExperienceSource.DIAMOND_ORE, 20, 18_000, CapPeriod.DAILY, "ORE")
        assertPolicy(policies, ExperienceSource.ANCIENT_DEBRIS, 40, 18_000, CapPeriod.DAILY, "ORE")
        assertPolicy(policies, ExperienceSource.CRAFT_RARE, 20, 12_000, CapPeriod.DAILY, "CRAFTING")
        assertPolicy(policies, ExperienceSource.ENDER_DRAGON_KILL, 1_200, 12_000, CapPeriod.WEEKLY, "ENDER_DRAGON_KILL")
        assertPolicy(policies, ExperienceSource.QUALIFIED_RECRUIT, 1_000, 5_000, CapPeriod.WEEKLY, "QUALIFIED_RECRUIT")
        assertPolicy(policies, ExperienceSource.PRE_CAP_WAR_WIN, 10_000, 20_000, CapPeriod.WEEKLY, "PRE_CAP_WAR_WIN")
        assertEquals(CapPeriod.UNLIMITED, policies.getValue(ExperienceSource.WEEKLY_ACTIVITY).period)
        assertEquals(ExperienceSource.entries.toSet(), policies.keys)
    }

    @Test
    fun `operator can override one source without changing another`() {
        val yaml = YamlConfiguration().apply {
            set("progression.sources.mob_kill.award_xp", 4)
            set("progression.sources.mob_kill.cap_xp", 4_000)
            set("progression.sources.mob_kill.period", "WEEKLY")
            set("progression.sources.mob_kill.pool", "HOSTILE_MOBS")
        }

        val policies = ConfigServiceBukkit(yaml).loadConfig().progression.sourcePolicies

        assertPolicy(policies, ExperienceSource.MOB_KILL, 4, 4_000, CapPeriod.WEEKLY, "HOSTILE_MOBS")
        assertPolicy(policies, ExperienceSource.PLAYER_KILL, 100, 6_000, CapPeriod.DAILY, "PLAYER_KILL")
    }

    @Test
    fun `invalid period and cap reject the config snapshot`() {
        val invalidPeriod = YamlConfiguration().apply {
            set("progression.sources.mob_kill.period", "HOURLY")
        }
        val invalidCap = YamlConfiguration().apply {
            set("progression.sources.mob_kill.cap_xp", -1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ConfigServiceBukkit(invalidPeriod).loadConfig()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConfigServiceBukkit(invalidCap).loadConfig()
        }
    }

    @Test
    fun `vanilla material and entity target pools are normalized and validated`() {
        val valid = YamlConfiguration().apply {
            set("progression.targets.materials.common_break", listOf("minecraft:stone", "DIRT"))
            set("progression.targets.entities.normal_mobs", listOf("minecraft:zombie", "SKELETON"))
        }
        val progression = ConfigServiceBukkit(valid).loadConfig().progression

        assertEquals(setOf("STONE", "DIRT"), progression.materialPools.getValue("common_break"))
        assertEquals(setOf("ZOMBIE", "SKELETON"), progression.entityPools.getValue("normal_mobs"))

        val invalidMaterial = YamlConfiguration().apply {
            set("progression.targets.materials.common_break", listOf("NOT_A_MATERIAL"))
        }
        val invalidEntity = YamlConfiguration().apply {
            set("progression.targets.entities.normal_mobs", listOf("NOT_A_MOB"))
        }
        assertThrows(IllegalArgumentException::class.java) { ConfigServiceBukkit(invalidMaterial).loadConfig() }
        assertThrows(IllegalArgumentException::class.java) { ConfigServiceBukkit(invalidEntity).loadConfig() }
    }

    @Test
    fun `bundled config explicitly documents every enabled source`() {
        val yaml = YamlConfiguration.loadConfiguration(File("src/main/resources/config.yml"))
        val enabledSources = ChapterTwoExperiencePolicies.defaults().values.filter { it.enabled }.map { it.source }

        enabledSources.forEach { source ->
            val path = "progression.sources.${source.name.lowercase()}"
            assertEquals(true, yaml.isConfigurationSection(path), "$path must be explicit in config.yml")
        }
    }

    private fun assertPolicy(
        policies: Map<ExperienceSource, net.lumalyte.lg.domain.values.ExperiencePolicy>,
        source: ExperienceSource,
        award: Int,
        cap: Int,
        period: CapPeriod,
        pool: String,
    ) {
        val policy = policies.getValue(source)
        assertEquals(award, policy.awardXp, "$source award")
        assertEquals(cap, policy.capXp, "$source cap")
        assertEquals(period, policy.period, "$source period")
        assertEquals(pool, policy.pool, "$source pool")
    }
}
