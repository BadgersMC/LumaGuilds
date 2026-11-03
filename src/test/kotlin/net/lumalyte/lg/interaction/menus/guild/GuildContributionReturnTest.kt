package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class GuildContributionReturnTest : GuildParentReturnFixture() {
    @Test fun `contributions return to the retained history before bank and dashboard`() {
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val bank = mockk<GuildBankMenu>(relaxed = true)
        val history = mockk<GuildBankTransactionHistoryMenu>(relaxed = true)
        every { bank.open() } answers { visits += "bank"; Unit }
        every { history.open() } answers { visits += "history"; Unit }
        every { factory.createGuildBankTransactionHistoryMenu(navigator, player, guild) } returns history
        val contributions = traced(GuildMemberContributionsMenu(navigator, player, guild), "contributions", visits)
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(bank)
        navigator.openMenu(history)
        navigator.openMenu(contributions)
        click(field(contributions, "mainPane") as StaticPane, 0, 0)
        assertEquals("history", visits.last())
        navigator.goBack()
        assertEquals("bank", visits.last())
        navigator.goBack()
        assertEquals("dashboard", visits.last())
        verify(exactly = 0) { factory.createGuildBankTransactionHistoryMenu(navigator, player, guild) }
    }
}
