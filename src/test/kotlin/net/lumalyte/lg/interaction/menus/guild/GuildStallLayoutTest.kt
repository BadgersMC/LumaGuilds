package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-258: stalls and members are centred. */
class GuildStallLayoutTest {
    @Test
    fun `one stall sits in the centre and two sit either side of it`() {
        assertEquals(listOf(13), GuildStallLayout.stalls(1))
        assertEquals(listOf(12, 14), GuildStallLayout.stalls(2))
        assertEquals(listOf(11, 13, 15), GuildStallLayout.stalls(3))
        assertEquals(listOf(11, 12, 13, 14, 15), GuildStallLayout.stalls(5))
        assertEquals(emptyList(), GuildStallLayout.stalls(0))
    }

    @Test
    fun `members are centred under the stall, seven to a row`() {
        assertEquals(listOf(13), GuildStallLayout.members(1))
        assertEquals(listOf(12, 13), GuildStallLayout.members(2))
        assertEquals(listOf(12, 13, 14), GuildStallLayout.members(3))
        assertEquals((10..16).toList() + listOf(22), GuildStallLayout.members(8))
        assertEquals(28, GuildStallLayout.members(40).size)
    }

    @Test
    fun `region ids read as names`() {
        assertEquals("Stall 18", GuildStallLayout.displayName("stall18"))
        assertEquals("North Stall 2", GuildStallLayout.displayName("north_stall_2"))
        assertEquals("Market", GuildStallLayout.displayName("market"))
    }
}
