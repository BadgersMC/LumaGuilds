package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-205/REQ-206. */
class PartyMenuContractTest {
    private val menu = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildPartyManagementMenu.kt").readText()
    private val page = listOf("GuildPartyPageMenu.kt", "PartyInviteGuildMenu.kt")
        .map { File("src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/$it") }
        .joinToString("\n") { if (it.exists()) it.readText() else "" }

    @Test
    fun `no placeholder buttons are left`() {
        assertFalse(menu.contains("coming_soon"))
        assertFalse(menu.contains("openSendPartyRequestMenu"))
        assertFalse(menu.contains("openPartyAccessSettingsMenu"))
        assertTrue(menu.contains("const val ROWS = 3"))
        assertTrue(menu.contains("CenteredRow.slots("))
        assertTrue(menu.contains("GuildPartyPageMenu("))
    }

    @Test
    fun `the party page invites, leaves and moderates with the right permissions`() {
        assertTrue(page.contains("partyService.inviteToParty("))
        assertTrue(page.contains("partyService.leaveParty("))
        assertTrue(page.contains("PartyModerationMenu("))
        assertTrue(page.contains("RankPermission.MANAGE_PARTIES"))
        assertTrue(page.contains("RankPermission.MANAGE_RELATIONS"))
    }
}
