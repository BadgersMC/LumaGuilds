package net.lumalyte.lg.infrastructure.services

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import kotlin.test.*

class GuildCreationCooldownConfigTest {
    @Test fun `defaults and live config use seven and fifteen day windows`() {
        val yaml = YamlConfiguration()
        val service = ConfigServiceBukkit(yaml)
        val original = service.loadConfig().guild.creationCooldown
        assertEquals(7, original.deletionWindowDays)
        assertEquals(15, original.cooldownDays)
        yaml.set("guild.create_then_delete_window_days", 3)
        yaml.set("guild.creation_cooldown_days", 9)
        val changed = service.loadConfig().guild.creationCooldown
        assertEquals(3, changed.deletionWindowDays)
        assertEquals(9, changed.cooldownDays)
        assertEquals(15, original.cooldownDays)
        yaml.set("guild.creation_cooldown_days", -1)
        assertFailsWith<IllegalArgumentException> { service.loadConfig() }
    }
}
