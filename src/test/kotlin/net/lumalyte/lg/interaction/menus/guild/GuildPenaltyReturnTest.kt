package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.PenaltyType
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Material
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import kotlin.test.assertEquals

internal class GuildPenaltyReturnTest : GuildParentReturnFixture() {
    @Test fun `cancelling a penalty returns to existing options without applying it or duplicating history`() {
        assertReturn(Material.GREEN_WOOL, attempts = 0)
    }

    @Test fun `failed penalty confirmation returns to existing options without revisiting confirmation`() {
        assertReturn(Material.RED_WOOL, attempts = 1)
    }

    private fun assertReturn(button: Material, attempts: Int) {
        val penalties = mockk<PenaltyService>()
        every { penalties.applyLevelReduction(guild, player.uniqueId, player.name) } returns
            PenaltyService.PenaltyResult.Failure(PenaltyService.PenaltyFeedback.LEVEL_DISABLED)
        loadKoinModules(module { single { penalties } })
        val navigator = MenuNavigator(player)
        val visits = mutableListOf<String>()
        val options = mockk<GuildStrikePenaltyMenu>(relaxed = true)
        every { options.open() } answers { visits += "options"; Unit }
        val confirmation = traced(
            GuildStrikePenaltyConfirmMenu(navigator, player, guild, PenaltyType.LEVEL_REDUCTION, StrikesConfig()),
            "confirmation", visits,
        )
        navigator.openMenu(dashboard(visits))
        navigator.openMenu(options)
        navigator.openMenu(confirmation)
        confirmation.createGui().panes.flatMap { it.items }.single { it.item.type == button }
            .callAction(mockk(relaxed = true))
        assertEquals("options", visits.last())
        navigator.goBack()
        assertEquals("dashboard", visits.last())
        assertEquals(1, visits.count { it == "confirmation" })
        verify(exactly = attempts) { penalties.applyLevelReduction(guild, player.uniqueId, player.name) }
    }
}
