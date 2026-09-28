package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockSeason2ParityContractTest {
    private val bedrockRoot = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock")

    @Test
    fun `control panel exposes every Season 2 dashboard domain`() {
        val source = File(bedrockRoot, "BedrockGuildControlPanelMenu.kt").readText()
        listOf(
            "members", "information", "ranks", "quests", "bank",
            "settings", "progression", "relations", "wars", "statistics"
        ).forEach { key ->
            assertTrue(source.contains("bedrock.control_panel.button.$key"), "missing Bedrock dashboard domain: $key")
        }
        assertTrue(source.contains("createGuildQuestsMenu"))
    }

    @Test
    fun `confirmation flows never route into Java guild menus`() {
        listOf(
            "BedrockGuildInviteConfirmationMenu.kt",
            "BedrockGuildKickConfirmationMenu.kt",
            "BedrockGuildMemberRankConfirmationMenu.kt",
            "BedrockGuildDisbandConfirmationMenu.kt",
            "BedrockGuildLeaveConfirmationMenu.kt",
        ).forEach { name ->
            val source = File(bedrockRoot, name).readText()
            assertFalse(
                source.contains("interaction.menus.guild."),
                "$name directly depends on a Java guild menu"
            )
            assertFalse(
                source.contains("Class.forName(\"net.lumalyte.lg.interaction.menus.guild."),
                "$name reflectively opens a Java guild menu"
            )
        }
    }

    @Test
    fun `multi step navigation persists and restores form state`() {
        val source = File(bedrockRoot, "BedrockMenuNavigator.kt").readText()
        assertTrue(source.contains("currentMenu.getCurrentFormState()"))
        assertTrue(source.contains("FormStateManager.saveState"))
        assertTrue(source.contains("FormStateManager.restoreState"))
        assertTrue(source.contains("passData"))
    }

    @Test
    fun `mutable bank settings and rank forms use shared Bedrock authorization`() {
        listOf(
            "BedrockGuildBankMenu.kt",
            "BedrockGuildBankAutomationMenu.kt",
            "BedrockGuildBankBudgetMenu.kt",
            "BedrockGuildBankSecurityMenu.kt",
            "BedrockGuildSettingsMenu.kt",
            "BedrockGuildRankManagementMenu.kt",
        ).forEach { name ->
            val source = File(bedrockRoot, name).readText()
            assertTrue(
                source.contains("BedrockGuildAuthorization"),
                "$name must use the shared Bedrock authorization guard"
            )
        }
    }
}