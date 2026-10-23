package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.junit.jupiter.api.Test
import kotlin.test.*

internal class GuildMenuLifecycleTest {
    private val player = mockk<Player>(relaxed = true)

    @Test fun `GUI handoff waits a tick and closes inventory before showing dialog`() {
        every { player.isOnline } returns true
        every { player.openInventory.topInventory.type } returns InventoryType.CHEST
        val tasks = mutableListOf<() -> Unit>()
        var rendered = false
        assertTrue(GuildDialogTransitions.present(player, { true }, {
            verify(exactly = 1) { player.closeInventory(InventoryCloseEvent.Reason.OPEN_NEW) }
            rendered = true; true
        }, {}, tasks::add))
        assertFalse(rendered)
        verify(exactly = 0) { player.closeInventory(any()) }
        tasks.single()()
        assertTrue(rendered)
    }

    @Test fun `closing while handoff is queued cannot reopen a dialog`() {
        every { player.isOnline } returns true
        every { player.openInventory.topInventory.type } returns InventoryType.CHEST
        val tasks = mutableListOf<() -> Unit>()
        var renders = 0
        GuildDialogTransitions.present(player, { true }, { renders++; true }, {}, tasks::add)
        GuildMenuLifecycle.dismiss(player)
        tasks.single()()
        assertEquals(0, renders)
    }

    @Test fun `gameplay close invalidates every navigator for this player only`() {
        val nav = net.lumalyte.lg.interaction.menus.MenuNavigator(player)
        val other = net.lumalyte.lg.interaction.menus.MenuNavigator(mockk(relaxed = true))
        val token = nav.currentNavigationToken()
        val otherToken = other.currentNavigationToken()
        GuildMenuLifecycle.dismiss(player)
        assertFalse(nav.isNavigationCurrent(token))
        assertTrue(other.isNavigationCurrent(otherToken))
        verify { player.closeDialog(); player.closeInventory() }
    }

    @Test fun `revoked handoff is handled without opening a fallback or showing private data`() {
        every { player.isOnline } returns true
        assertTrue(GuildDialogTransitions.present(player, { false }, { error("must not render") },
            { error("must not fallback") }, { error("must not schedule") }))
    }

    @Test fun `manual inventory close cancels queued work but plugin chat input handoff does not`() {
        val epoch = GuildMenuLifecycle.generation(player)
        val token = epoch.get()
        GuildMenuLifecycle.inventoryClosed(player, InventoryCloseEvent.Reason.PLUGIN)
        GuildMenuLifecycle.inventoryClosed(player, InventoryCloseEvent.Reason.OPEN_NEW)
        assertEquals(token, epoch.get())
        GuildMenuLifecycle.inventoryClosed(player, InventoryCloseEvent.Reason.PLAYER)
        assertNotEquals(token, epoch.get())
    }

    @Test fun `failed queued rendering invokes fallback once`() {
        every { player.isOnline } returns true
        every { player.openInventory.topInventory.type } returns InventoryType.CHEST
        val tasks = mutableListOf<() -> Unit>()
        var failures = 0
        GuildDialogTransitions.present(player, { true }, { false }, { failures++ }, tasks::add)
        assertEquals(0, failures)
        tasks.single()()
        assertEquals(1, failures)
    }

    @Test fun `opening another inventory cancels a queued handoff without closing the new container`() {
        every { player.isOnline } returns true
        every { player.openInventory.topInventory.type } returns InventoryType.CHEST
        val tasks = mutableListOf<() -> Unit>()
        GuildDialogTransitions.present(player, { true }, { error("stale render") },
            { error("stale fallback") }, tasks::add)
        every { player.openInventory.topInventory } returns mockk(relaxed = true)
        tasks.single()()
        verify(exactly = 0) { player.closeInventory(any()) }
    }
}
