package net.lumalyte.lg.interaction.commands

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Subcommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** REQ-210 to REQ-215: /party exists, every player can run it, and it has the agreed subcommands. */
internal class PartyCommandContractTest {
    private val methods = PartyCommand::class.java.declaredMethods

    @Test
    fun partyCommandHasTheAgreedSubcommands() {
        assertEquals("party", PartyCommand::class.java.getAnnotation(CommandAlias::class.java).value)
        assertNotNull(methods.singleOrNull { it.isAnnotationPresent(Default::class.java) }, "/party opens the menu")
        val subcommands = methods.mapNotNull { it.getAnnotation(Subcommand::class.java)?.value }.toSet()
        assertEquals(
            setOf("help", "invite", "accept", "deny", "cancel", "info", "list", "leave", "disband", "chat", "rename"),
            subcommands,
        )
        listOf("onInvite", "onAccept", "onDeny", "onCancel").forEach { name ->
            assertEquals("@guilds", methods.single { it.name == name }.getAnnotation(CommandCompletion::class.java).value, name)
        }
    }

    @Test
    fun partyIsRegisteredAndOpenToPlayers() {
        val pluginYml = File("src/main/resources/plugin.yml").readText()
        assertTrue(Regex("(?m)^  party:\\n").containsMatchIn(pluginYml), "plugin.yml declares /party")
        assertTrue(Regex("(?m)^  lumaguilds\\.party:\\n(?:    .*\\n)*?    default: true").containsMatchIn(pluginYml))
        val main = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        assertTrue(main.contains("registerCommand(net.lumalyte.lg.interaction.commands.PartyCommand(partyChatCommand))"))
    }

    @Test
    fun inviteWithoutAGuildOpensTheInvitePageOrCreationMenu() {
        val source = File("src/main/kotlin/net/lumalyte/lg/interaction/commands/PartyCommand.kt").readText()
        val body = source.substringAfter("private fun openInviteMenu").substringBefore("private fun listRequests")
        assertTrue(body.contains("party == null -> menuFactory.createPartyCreationMenu"))
        assertTrue(body.contains("PartyInviteGuildMenu(navigator, player, guild, party.id)"))
    }
}
