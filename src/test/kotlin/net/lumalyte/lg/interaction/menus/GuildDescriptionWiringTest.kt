package net.lumalyte.lg.interaction.menus

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GuildDescriptionWiringTest {
    @Test
    fun `service enforces shared policy and description permission`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt"
        ).readText()

        assertTrue(source.contains("RankPermission.MANAGE_DESCRIPTION"))
        assertTrue(source.contains("GuildDescriptionContent.validationFailure(description)"))
        assertFalse(
            source.substringAfter("override fun setDescription")
                .substringBefore("override fun getDescription")
                .contains("RankPermission.MANAGE_EMOJI")
        )
    }

    @Test
    fun `all description editors delegate validation to shared content policy`() {
        val java = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/DescriptionEditorMenu.kt"
        ).readText()
        val bedrock = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockDescriptionEditorMenu.kt"
        ).readText()

        assertTrue(java.contains("GuildDescriptionContent.validationFailure(description)"))
        assertTrue(bedrock.contains("GuildDescriptionContent.validationFailure(description)"))
        assertTrue(java.contains("GuildDescriptionContent.render(desc)"))
        assertTrue(bedrock.contains("GuildDescriptionContent.plainText(description)"))

        val bedrockSettings = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildSettingsMenu.kt"
        ).readText()
        assertTrue(bedrockSettings.contains("GuildDescriptionContent.validationFailure(description)"))
        assertTrue(bedrockSettings.contains("GuildDescriptionContent.MAX_LENGTH"))
        assertTrue(bedrockSettings.contains("normalizedDescription = newDescription.ifEmpty { null }"))
    }

    @Test
    fun `public info uses clickable Java renderer and plain Bedrock renderer`() {
        val java = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildInfoMenu.kt"
        ).readText()
        val bedrock = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildInfoMenu.kt"
        ).readText()

        assertTrue(java.contains("GuildDescriptionContent.render(rawDescription)"))
        assertTrue(java.contains("GuildDescriptionContent.discordInvites(rawDescription)"))
        assertTrue(java.contains("player.sendMessage("))
        assertTrue(bedrock.contains("GuildDescriptionContent::plainText"))
    }

    @Test
    fun `settings renderer no longer parses raw MiniMessage independently`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildSettingsMenu.kt"
        ).readText()

        assertTrue(source.contains("GuildDescriptionContent.render(currentDescription)"))
        assertFalse(source.contains("parseMiniMessageForDisplay"))
    }
}
