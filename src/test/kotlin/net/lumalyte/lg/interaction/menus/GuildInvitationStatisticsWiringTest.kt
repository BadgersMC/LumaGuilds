package net.lumalyte.lg.interaction.menus

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GuildInvitationStatisticsWiringTest {
    @Test
    fun javaStatisticsMenuExposesInvitationLeaderboardCardAndDetail() {
        val source = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildStatisticsMenu.kt").readText()
        assertTrue(source.contains("addTopInvitersButton(pane, 4, 1)"))
        assertTrue(source.contains("invitationStatisticsService.getLeaderboard(guild.id, 3)"))
        assertTrue(source.contains("invitationStatisticsService.getLeaderboardPage(guild.id, page, INVITERS_PER_PAGE)"))
        assertTrue(source.contains("menu.statistics.item.top_inviters.name"))
        assertTrue(source.contains("menu.statistics.detail.top_inviters.name"))
        assertTrue(source.contains("menu.statistics.item.previous_page.name"))
        assertTrue(source.contains("menu.statistics.item.next_page.name"))
        assertTrue(source.contains("menu.statistics.common.page_info"))
    }

    @Test
    fun bedrockStatisticsMenuExposesSameInvitationStatisticsSource() {
        val source = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildStatisticsMenu.kt").readText()
        assertTrue(source.contains("bedrock.statistics.header.invitations"))
        assertTrue(source.contains("invitationStatisticsService.getTotalInvitations(guild.id)"))
        assertTrue(source.contains("invitationStatisticsService.getLeaderboardPage(guild.id, invitationPage, INVITERS_PER_PAGE)"))
        assertTrue(source.contains(".dropdown("))
        assertTrue(source.contains("response.asDropdown(INVITATION_PAGE_DROPDOWN_INDEX)"))
    }

    @Test
    fun inviteConfirmationsFailClosedWhenDurablePersistenceFails() {
        val java = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildInviteConfirmationMenu.kt").readText()
        val bedrock = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildInviteConfirmationMenu.kt").readText()
        assertTrue(java.contains("if (!invitationStored)"))
        assertTrue(java.contains("menu.guild_confirmation.invite.feedback.failed"))
        assertTrue(bedrock.contains("if (!invitationStored)"))
        assertTrue(bedrock.contains("bedrock.invite_confirmation.feedback.failed"))
    }
}
