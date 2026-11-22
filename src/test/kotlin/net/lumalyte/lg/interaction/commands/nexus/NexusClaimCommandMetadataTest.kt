package net.lumalyte.lg.interaction.commands.nexus

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NexusClaimCommandMetadataTest {
    @Test
    fun `claim tool and claim menu preserve roots permissions and player-only defaults`() {
        val definitions = scanGuildCommandDefinitions()
        for ((type, rootName, permission) in listOf(
            Triple(NexusClaimToolCommand::class, "claim", "lumaguilds.command.claim"),
            Triple(NexusClaimMenuCommand::class, "claimmenu", "lumaguilds.command.claimmenu"),
        )) {
            val definition = assertNotNull(definitions.singleOrNull { it.commandClass == type })
            assertEquals(rootName, definition.annotation.name)
            val handler = assertNotNull(definition.subcommands.singleOrNull { it.path.isEmpty() })
            assertEquals(permission, handler.permission)
            assertTrue(handler.isPlayerOnly)
            assertTrue(handler.parameters.all { it.isContext })
        }
    }
}
