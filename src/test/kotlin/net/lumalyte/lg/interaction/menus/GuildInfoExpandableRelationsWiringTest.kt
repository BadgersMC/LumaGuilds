package net.lumalyte.lg.interaction.menus

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildInfoExpandableRelationsWiringTest {
    @Test
    fun `Java guild info exposes full ally and enemy browsers`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildInfoMenu.kt"
        ).readText()

        assertTrue(source.contains("createGuildRelationBrowserMenu"))
        assertTrue(source.contains("RelationType.ALLY"))
        assertTrue(source.contains("RelationType.ENEMY"))
        assertTrue(source.contains("menu.guild_info.relations.view_all"))
    }

    @Test
    fun `Bedrock guild info no longer ships the fake empty relations placeholder`() {
        val source = File(
            "src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildInfoMenu.kt"
        ).readText()

        assertTrue(source.contains("createGuildRelationBrowserMenu"))
        assertTrue(source.contains("bedrock.info.button.allies"))
        assertTrue(source.contains("bedrock.info.button.enemies"))
        assertFalse(source.contains("Placeholder for relations"))
    }
}
