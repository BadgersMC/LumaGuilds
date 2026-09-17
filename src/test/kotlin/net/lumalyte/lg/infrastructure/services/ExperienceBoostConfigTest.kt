package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.ExperienceSource
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

class ExperienceBoostConfigTest {
    @Test fun `disabled boost requires no dates and reloaded settings are immutable`() {
        var yaml = YamlConfiguration()
        val config = ConfigServiceBukkit { yaml }
        assertNull(config.loadConfig().progression.xpBoost)
        yaml = validConfig()
        val first = config.loadConfig().progression.xpBoost!!
        assertFalse(ExperienceSource.ADMIN_BONUS in first.sources)
        assertTrue(ExperienceSource.WEEKLY_ACTIVITY in first.sources)
        yaml.set("progression.xp_boost.multiplier", 3.0)
        val next = config.loadConfig().progression.xpBoost!!
        assertEquals(2.0, first.multiplier)
        assertEquals(3.0, next.multiplier)
        yaml.set("progression.xp_boost.enabled", false)
        assertNull(config.loadConfig().progression.xpBoost)
    }

    @Test fun `invalid enabled boost is rejected and explicit source selection is respected`() {
        val yaml = validConfig()
        val config = ConfigServiceBukkit(yaml)
        yaml.set("progression.xp_boost.sources", listOf("MOB_KILL"))
        assertEquals(setOf(ExperienceSource.MOB_KILL), config.loadConfig().progression.xpBoost!!.sources)
        yaml.set("progression.xp_boost.sources", listOf("TYPO"))
        assertFailsWith<IllegalArgumentException> { config.loadConfig() }
        yaml.set("progression.xp_boost.sources", emptyList<String>())
        assertFailsWith<IllegalArgumentException> { config.loadConfig() }
        yaml.set("progression.xp_boost.sources", null)
        yaml.set("progression.xp_boost.ends_at", "2026-09-17T00:00:00Z")
        assertFailsWith<IllegalArgumentException> { config.loadConfig() }
    }

    private fun validConfig() = YamlConfiguration().apply {
        set("progression.xp_boost.enabled", true)
        set("progression.xp_boost.starts_at", "2026-09-18T00:00:00Z")
        set("progression.xp_boost.ends_at", "2026-09-21T00:00:00Z")
        set("progression.xp_boost.multiplier", 2.0)
    }
}
