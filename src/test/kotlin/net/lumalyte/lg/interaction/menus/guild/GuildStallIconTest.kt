package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.assertEquals
import org.bukkit.Material

class GuildStallIconTest {
    @AfterEach fun cleanup() { MockBukkit.unmock() }

    @Test fun `navigation and content keep useful vanilla icons when Nexo is unavailable`() {
        MockBukkit.mock()
        assertEquals(Material.OAK_SIGN, GuildStallIcon.STALL.item().type)
        assertEquals(Material.PLAYER_HEAD, GuildStallIcon.MEMBER.item().type)
        assertEquals(Material.ARROW, GuildStallIcon.BACK.item().type)
        assertEquals(Material.ARROW, GuildStallIcon.PREVIOUS.item().type)
        assertEquals(Material.COMPASS, GuildStallIcon.REFRESH.item().type)
        assertEquals(Material.ARROW, GuildStallIcon.NEXT.item().type)
        assertEquals(Material.WRITABLE_BOOK, GuildStallIcon.SALES.item().type)
        assertEquals(Material.TRIPWIRE_HOOK, GuildStallIcon.ACCESS.item().type)
    }
}
