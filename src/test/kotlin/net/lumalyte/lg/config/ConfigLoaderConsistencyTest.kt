package net.lumalyte.lg.config

import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Config loader consistency: every documented key must actually be read by
 * ConfigServiceBukkit, and shipped defaults must be production-usable.
 *
 * REQ-004 (vault), REQ-005 (bedrock icons), REQ-018 (brewingXp),
 * REQ-019 (modeSwitchingEnabled), REQ-020 (nameFilter), REQ-021
 * (banner_copy_physical_cost), REQ-029 (parties_enabled).
 */
class ConfigLoaderConsistencyTest {

    private fun load(cfg: YamlConfiguration) = ConfigServiceBukkit(cfg).loadConfig()

    @Test
    fun `vault section is loaded`() {
        val cfg = YamlConfiguration().apply {
            set("vault.bank_mode", "PHYSICAL")
            set("vault.use_physical_currency", true)
            set("vault.compressable_blocks", listOf("DIAMOND_BLOCK:DIAMOND:9"))
        }
        val vault = load(cfg).vault
        assertEquals("PHYSICAL", vault.bankMode)
        assertTrue(vault.usePhysicalCurrency)
        assertEquals(listOf("DIAMOND_BLOCK:DIAMOND:9"), vault.compressableBlocks)
    }

    @Test
    fun `bedrock section is loaded`() {
        val cfg = YamlConfiguration().apply {
            set("bedrock.bedrock_menus_enabled", false)
            set("bedrock.default_button_image_url", "https://cdn.example.com/icon.png")
            set("bedrock.debug_bedrock_menus", true)
        }
        val bedrock = load(cfg).bedrock
        assertEquals(false, bedrock.bedrockMenusEnabled)
        assertEquals("https://cdn.example.com/icon.png", bedrock.defaultButtonImageUrl)
        assertTrue(bedrock.debugBedrockMenus)
    }

    @Test
    fun `brewing xp is loaded`() {
        val cfg = YamlConfiguration().apply { set("progression.brewing_xp", 42) }
        assertEquals(42, load(cfg).progression.brewingXp)
    }

    @Test
    fun `mode switching enabled is loaded`() {
        val cfg = YamlConfiguration().apply { set("guild.mode_switching_enabled", false) }
        assertEquals(false, load(cfg).guild.modeSwitchingEnabled)
    }

    @Test
    fun `name filter is loaded`() {
        val cfg = YamlConfiguration().apply {
            set("guild.name_filter.enabled", true)
            set("guild.name_filter.blocked_patterns", listOf("\\bfoo\\b"))
            set("guild.name_filter.normalization.leet_map", false)
        }
        val filter = load(cfg).guild.nameFilter
        assertTrue(filter.enabled)
        assertEquals(listOf("\\bfoo\\b"), filter.blockedPatterns)
        assertEquals(false, filter.normalization.leetMap)
    }

    @Test
    fun `banner copy physical cost is loaded`() {
        val cfg = YamlConfiguration().apply { set("guild.banner_copy_physical_cost", 99) }
        assertEquals(99, load(cfg).guild.bannerCopyPhysicalCost)
    }

    @Test
    fun `shipped config yml documents all newly-wired keys`() {
        val yml = File("src/main/resources/config.yml").readText()
        listOf(
            "parties_enabled", "brewing_xp", "mode_switching_enabled",
            "name_filter:", "banner_copy_physical_cost",
        ).forEach { key ->
            assertTrue(yml.contains(key), "config.yml must document '$key'")
        }
    }

    @Test
    fun `shipped config yml has no placeholder-hosted icons`() {
        val yml = File("src/main/resources/config.yml").readText()
        assertTrue(!yml.contains(Regex("https?://via\\.placeholder\\.com")),
            "config.yml must not reference dead placeholder-hosted images")
    }

    @Test
    fun `bedrock icon defaults are empty when unset`() {
        val bedrock = load(YamlConfiguration()).bedrock
        assertEquals("", bedrock.defaultButtonImageUrl)
        assertEquals("", bedrock.guildMembersIconUrl)
        assertEquals("", bedrock.confirmIconUrl)
    }
}
