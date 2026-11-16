package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BedrockGuildHomeNameTest {
    @Test
    fun `deleted numbered home is reused without overwriting another location`() {
        assertEquals("home2", nextAvailableGuildHomeName(listOf("home1", "home3")))
    }

    @Test
    fun `numbering skips occupied names case-insensitively and ignores custom names`() {
        assertEquals("home3", nextAvailableGuildHomeName(listOf("main", "HOME1", "home2", "farm")))
    }

    @Test
    fun `new guild starts at home one and consecutive entries advance`() {
        assertEquals("home1", nextAvailableGuildHomeName(emptyList()))
        assertEquals("home4", nextAvailableGuildHomeName(listOf("home1", "home2", "home3")))
    }
}
