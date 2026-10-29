package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Material
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class GuildPermissionReturnTest : GuildParentReturnFixture() {
    @Test fun `category Save returns committed permissions to the existing editor`() {
        assertCategoryReturn(slot = 1, saves = true, retainsDraft = true)
    }

    @Test fun `category Cancel discards only category changes and retains the parent icon draft`() {
        assertCategoryReturn(slot = 3, saves = false, retainsDraft = false)
    }

    @Test fun `category Back carries the permission draft without writing it`() {
        assertCategoryReturn(slot = 7, saves = false, retainsDraft = true)
    }

    @Test fun `failed category Save retains the existing retry draft and does not duplicate the parent`() {
        assertCategoryReturn(slot = 1, saves = true, retainsDraft = true, saveSucceeds = false)
    }

    @Test fun `revoked rank permission unwinds the category and editor before management Back`() {
        val rank = bindRank("Officer")
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val management = mockk<GuildRankManagementMenu>(relaxed = true)
        every { management.open() } answers { visits += "management"; Unit }
        val editor = traced(RankEditMenu(navigator, player, guild, rank), "editor", visits)
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(management)
        navigator.openMenu(editor)
        every { ranks.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RANKS) } returns false
        every { editor.open() } answers { callOriginal() }
        navigator.openMenu(PermissionCategoryMenu(navigator, player, guild, rank, "Banking", emptyList()))
        assertEquals("management", visits.last())
        navigator.goBack()
        assertEquals("dashboard", visits.last())
        verify(exactly = 0) { ranks.updateRank(any(), any()) }
    }

    private fun assertCategoryReturn(slot: Int, saves: Boolean, retainsDraft: Boolean, saveSucceeds: Boolean = true) {
        val rank = bindRank("Officer")
        every { ranks.updateRank(any(), player.uniqueId) } returns saveSucceeds
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val management = mockk<GuildRankManagementMenu>(relaxed = true)
        every { management.open() } answers { visits += "management"; Unit }
        val editor = traced(RankEditMenu(navigator, player, guild, rank), "editor", visits)
        setField(editor, "selectedIcon", Material.CHERRY_PLANKS)
        val category = traced(
            PermissionCategoryMenu(navigator, player, guild, rank, "Banking", listOf(RankPermission.DEPOSIT_TO_BANK)),
            "category", visits,
        )
        setField(category, "modifiedPermissions", mutableSetOf(RankPermission.DEPOSIT_TO_BANK))
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(management)
        navigator.openMenu(editor)
        navigator.openMenu(category)

        click(paneFor(category, "addActionButtons"), slot, 5)

        assertEquals("editor", visits.last())
        assertEquals(Material.CHERRY_PLANKS, field(editor, "selectedIcon"))
        val expected = if (retainsDraft) setOf(RankPermission.DEPOSIT_TO_BANK) else emptySet()
        assertEquals(expected, (field(editor, "rank") as Rank).permissions)
        verify(exactly = if (saves) 1 else 0) { ranks.updateRank(any(), player.uniqueId) }
        navigator.goBack()
        assertEquals("management", visits.last())
        navigator.goBack()
        assertEquals("dashboard", visits.last())
        assertEquals(1, visits.count { it == "category" })
    }
}
