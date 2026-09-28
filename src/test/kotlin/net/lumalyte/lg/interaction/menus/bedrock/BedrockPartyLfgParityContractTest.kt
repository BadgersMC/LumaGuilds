package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockPartyLfgParityContractTest {
    private val root = File("src/main/kotlin/net/lumalyte/lg")
    private val bedrock = File(root, "interaction/menus/bedrock")
    private val factory = File(root, "interaction/menus/MenuFactory.kt").readText()

    @Test
    fun `LFG browser is platform native and service backed`() {
        val browserFile = File(bedrock, "BedrockLfgBrowserMenu.kt")
        assertTrue(browserFile.exists())
        val browser = browserFile.readText()
        assertTrue(factory.contains("BedrockLfgBrowserMenu"))
        assertTrue(factory.contains("shouldUseBedrockMenus(player)"))
        assertTrue(browser.contains("LfgService"))
        assertTrue(browser.contains("getAvailableGuilds()"))
        assertTrue(browser.contains("createJoinRequirementsMenu"))
        assertFalse(browser.contains("interaction.menus.guild.LfgBrowserMenu"))
    }

    @Test
    fun `join requirements returns through Bedrock factory navigation`() {
        val join = File(bedrock, "BedrockJoinRequirementsMenu.kt").readText()
        assertTrue(join.contains("createLfgBrowserMenu"))
        assertTrue(join.contains("lfgService.joinGuild"))
        assertFalse(join.contains("returnToLfgBrowser"))
        assertFalse(join.contains("interaction.menus.guild.JoinRequirementsMenu"))
    }

    @Test
    fun `party management does not expose fake unavailable permission actions`() {
        val party = File(bedrock, "BedrockGuildPartyManagementMenu.kt").readText()
        assertFalse(party.contains("Party permissions configuration is not available in Bedrock"))
        assertFalse(party.contains("Party details are not available in Bedrock"))
        assertFalse(party.contains("openPartyPermissionsMenu"))
        assertTrue(party.contains("partyService.getActivePartiesForGuild"))
        assertTrue(party.contains("partyService.getPendingRequestsForGuild"))
        assertTrue(party.contains("partyService.getPendingRequestsFromGuild"))
    }

    @Test
    fun `party creation rechecks manage permission at mutation boundary`() {
        val creation = File(bedrock, "BedrockPartyCreationMenu.kt").readText()
        assertTrue(
            creation.contains("partyService.canManageParties") ||
                creation.contains("RankPermission.MANAGE_PARTIES")
        )
        assertTrue(creation.contains("partyService.createParty"))
    }
}
