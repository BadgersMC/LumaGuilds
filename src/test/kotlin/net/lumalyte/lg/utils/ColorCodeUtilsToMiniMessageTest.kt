package net.lumalyte.lg.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure-Kotlin tests for [ColorCodeUtils.toMiniMessage], the read-side tag
 * normalizer used by %lumaguilds_guild_tag_minimessage% (Velocitab/proxy-safe).
 *
 * Expected values are the canonical MiniMessage form produced by the bundled
 * Adventure MiniMessage serializer: redundant closing tags are dropped
 * (`<red>Red`, not `<red>Red</red>`), hex colors are uppercase (`<#FF5733>`),
 * and literal `<` is escaped as `\<`. All outputs re-parse as valid MiniMessage.
 */
class ColorCodeUtilsToMiniMessageTest {

    @Test
    fun `converts legacy ampersand codes to minimessage`() {
        assertEquals("<red>Red", ColorCodeUtils.toMiniMessage("&cRed"))
        assertEquals("<bold><gold>Gold", ColorCodeUtils.toMiniMessage("&6&lGold"))
    }

    @Test
    fun `converts section codes to minimessage`() {
        assertEquals("<red>Red", ColorCodeUtils.toMiniMessage("§cRed"))
        assertEquals("<bold><gold>Gold", ColorCodeUtils.toMiniMessage("§6§lGold"))
    }

    @Test
    fun `converts legacy hex codes to minimessage`() {
        assertEquals("<#FF5733>Hex", ColorCodeUtils.toMiniMessage("&x&f&f&5&7&3&3Hex"))
        assertEquals("<#FF5733>Hex", ColorCodeUtils.toMiniMessage("§x§f§f§5§7§3§3Hex"))
    }

    @Test
    fun `keeps existing minimessage tags`() {
        assertEquals("<red>Red", ColorCodeUtils.toMiniMessage("<red>Red</red>"))
        assertEquals(
            "<gradient:#FF0000:#00FF00>G",
            ColorCodeUtils.toMiniMessage("<gradient:#ff0000:#00ff00>G</gradient>"),
        )
    }

    @Test
    fun `passes plain text through unchanged`() {
        assertEquals("Elite", ColorCodeUtils.toMiniMessage("Elite"))
        assertEquals("R\\<3", ColorCodeUtils.toMiniMessage("R<3"))
    }

    @Test
    fun `handles empty input`() {
        assertEquals("", ColorCodeUtils.toMiniMessage(""))
    }
}
