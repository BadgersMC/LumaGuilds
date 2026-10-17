package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedrockLocalizationTruthfulnessContractTest {
    private val bedrockRoot = File("src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock")
    private val locale = File("src/main/resources/lang/en_US.yml").readText()

    @Test
    fun `audited Bedrock flows contain no stale implementation promises`() {
        val audited = listOf(
            "BedrockGuildPartyManagementMenu.kt",
            "BedrockLfgBrowserMenu.kt",
            "BedrockJoinRequirementsMenu.kt",
            "BedrockGuildWarDeclarationMenu.kt",
            "BedrockGuildWarManagementMenu.kt",
            "BedrockGuildRelationsMenu.kt",
            "BedrockPeaceAgreementMenu.kt"
        ).joinToString("\n") { File(bedrockRoot, it).readText() }
        listOf("coming soon", "not available in Bedrock", "Use the Java client")
            .forEach { phrase -> assertFalse(audited.contains(phrase, ignoreCase = true), phrase) }
        assertFalse(audited.contains("sendMessage(\""))
    }

    @Test
    fun `party and diplomacy unknown names are localized instead of hardcoded fallbacks`() {
        val party = File(bedrockRoot, "BedrockGuildPartyManagementMenu.kt").readText()
        val relations = File(bedrockRoot, "BedrockGuildRelationsMenu.kt").readText()
        assertFalse(party.contains("\"Unknown Guild\""))
        assertTrue(party.contains("bedrock.party.management.unknown_guild"))
        assertTrue(relations.contains("bedrock.relations_management.unknown_guild"))
    }

    @Test
    fun `description rendering preserves Discord invite urls as visible plain form text`() {
        val description = File(bedrockRoot, "BedrockDescriptionEditorMenu.kt").readText()
        assertTrue(description.contains("GuildDescriptionContent.plainText(description)"))
        assertTrue(description.contains("bedrock.description_editor.current"))
    }

    @Test
    fun `locale no longer advertises superseded Bedrock or party roadmap placeholders`() {
        listOf(
            "Bedrock support is on the roadmap",
            "Use the Java client to configure",
            "Party details menu coming soon",
            "Party list menu coming soon",
            "Send party request menu coming soon",
            "Create party menu coming soon",
            "Party access settings menu coming soon"
        ).forEach { phrase -> assertFalse(locale.contains(phrase, ignoreCase = true), phrase) }
    }
}
