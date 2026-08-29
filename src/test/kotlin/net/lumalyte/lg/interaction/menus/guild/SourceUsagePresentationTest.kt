package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.TextComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceUsagePresentationTest {
    @Test
    fun `shared allowance uses pool name rather than representative source`() {
        val label = sourcePoolDisplayName("ORE") as TextComponent

        assertEquals("Ore", label.content())
    }
}
