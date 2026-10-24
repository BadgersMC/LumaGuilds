package net.lumalyte.lg.interaction.menus.guild

import io.mockk.*
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.services.GUILD_DIALOG_CLOSE_LABEL
import net.lumalyte.lg.infrastructure.services.RecruitmentPresentation
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class GuildLeaderboardDialogExitTest : RecruitmentDialogFixture() {
    private fun leaderboard(nav: MenuNavigator): GuildLeaderboardDialogMenu {
        every { lang.msg(any(), *anyVararg()) } answers { net.kyori.adventure.text.Component.text(firstArg<String>()) }
        val guild = Guild(UUID.randomUUID(), "Rankings", createdAt = Instant.EPOCH)
        val actor = UUID.randomUUID()
        every { player.uniqueId } returns actor
        every { player.isOnline } returns true
        every { guilds.getGuild(guild.id) } returns guild
        every { guilds.getAllGuilds() } returns emptySet()
        val members = mockk<MemberService>()
        every { members.getMember(actor, guild.id) } returns mockk()
        return GuildLeaderboardDialogMenu(nav, player, guild, guilds, members, mockk<QuestService>(), lang, presenter)
    }

    @Test fun `direct entry labels its exit Close and cannot reopen a parent`() {
        mockkObject(RecruitmentPresentation)
        every { RecruitmentPresentation.supportsDialog(player) } returns true
        val nav = MenuNavigator(player)
        nav.openMenu(leaderboard(nav))
        assertEquals(GUILD_DIALOG_CLOSE_LABEL, renderedPage!!.back.label)
        renderedPage!!.back.action()
        verify(exactly = 1) { player.closeDialog() }
        verify(exactly = 1) { player.closeInventory() }
    }

    @Test fun `nested entry keeps Back and restores its actual parent once`() {
        val nav = MenuNavigator(player)
        val parent = mockk<Menu>(relaxed = true)
        nav.openMenu(parent)
        nav.openMenu(leaderboard(nav))
        assertNotEquals(GUILD_DIALOG_CLOSE_LABEL, renderedPage!!.back.label)
        renderedPage!!.back.action()
        verify(exactly = 2) { parent.open() }
        verify(exactly = 0) { player.closeDialog() }
    }
}
