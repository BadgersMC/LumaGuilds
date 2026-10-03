package net.lumalyte.lg.utils

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import kotlin.test.*

class RankNameContentTest {
    @Test
    fun colorsDoNotCountTowardsVisibleLimit() {
        assertTrue(RankNameContent.valid("&a&l" + "A".repeat(24)))
        assertTrue(RankNameContent.valid("&#55ff99Founder"))
        assertTrue(RankNameContent.valid("§aCo-Owner"))
        assertFalse(RankNameContent.valid("&a" + "A".repeat(25)))
        assertFalse(RankNameContent.valid("&a&l"))
        assertFalse(RankNameContent.valid("&#12345ZFounder"))
        assertFalse(RankNameContent.valid("Founder\n"))
        assertFalse(RankNameContent.valid("<click:run_command:/op>Founder"))
    }
    @Test
    fun colorsRenderAsComponents() {
        assertEquals("Founder", PlainTextComponentSerializer.plainText().serialize(RankNameContent.component("&aFounder")))
        assertEquals("§aFounder", RankNameContent.legacy("&aFounder"))
        assertEquals(NamedTextColor.GREEN, RankNameContent.component("&aFounder").color())
        assertEquals("Founder", RankNameContent.plain("&#55ff99&lFounder"))
    }
}
