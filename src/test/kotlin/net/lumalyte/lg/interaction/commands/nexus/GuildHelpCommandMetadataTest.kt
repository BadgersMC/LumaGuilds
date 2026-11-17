package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GuildHelpCommandMetadataTest {
    @Test
    fun `Nexus preserves the guild help command path permission and optional topic`() {
        val definitions = PaperCommandScanner().scanCommands(
            GuildHelpCommands::class.java.packageName,
            GuildHelpCommands::class.java.classLoader
        )
        val root = assertNotNull(definitions.singleOrNull { it.commandClass == GuildHelpCommands::class })
        assertEquals("guild", root.annotation.name)
        assertEquals(listOf("g"), root.annotation.aliases.toList())

        val help = assertNotNull(root.subcommands.singleOrNull { it.path == listOf("help") })
        assertEquals("lumaguilds.guild.help", help.permission)
        assertTrue(help.isPlayerOnly)
        assertEquals(1, help.parameters.count { it.isArg })
        val topic = help.parameters.single { it.isArg }
        assertEquals("topic", topic.name)
        assertTrue(topic.isOptional)
    }
}
