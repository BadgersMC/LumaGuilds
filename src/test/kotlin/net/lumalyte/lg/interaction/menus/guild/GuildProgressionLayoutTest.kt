package net.lumalyte.lg.interaction.menus.guild

import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-121: content may never conceal sidebar, header or navigation controls. */
class GuildProgressionLayoutTest {
    @Test fun `a full source page has unique slots clear of every control`() {
        val menu = GuildProgressionMenu(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk())
        @Suppress("UNCHECKED_CAST")
        val slots = GuildProgressionMenu::class.java.getDeclaredField("gridSlots")
            .apply { isAccessible = true }.get(menu) as List<Int>
        val pageSize = GuildProgressionMenu::class.java.getDeclaredField("itemsPerPage")
            .apply { isAccessible = true }.getInt(menu)
        val reserved = (0..8).toSet() + setOf(9, 18, 27, 36, 17) + (45..53).toSet()
        assertEquals(pageSize, slots.size, "Every source on a page needs its own visible slot")
        assertEquals(slots.size, slots.distinct().size, "Sources must not overwrite one another")
        assertTrue(slots.all { it in 0..53 }, "All source slots must fit the inventory")
        assertTrue(slots.none { it in reserved }, "Sources overlap controls at ${slots.filter { it in reserved }}")
        assertEquals(slots.sorted(), slots, "Source categories must read left-to-right, top-to-bottom")
    }
}
