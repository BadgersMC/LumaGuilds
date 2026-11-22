package net.lumalyte.lg.interaction.commands.nexus

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Subcommand as AcfSubcommand
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import net.lumalyte.lg.interaction.commands.PartyChatCommand
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-1: the inactive Nexus /pc tree must expose every legacy ACF party-chat route
 * with the same root, aliases, permission, player restriction, argument cardinality and completion
 * source; the transitional ACF adapter must keep its legacy metadata untouched.
 */
class NexusPartyChatCommandMetadataTest {
    private val permission = "lumaguilds.partychat"

    private val definition by lazy {
        val definitions = scanGuildCommandDefinitions()
        assertNotNull(definitions.singleOrNull { it.commandClass == NexusPartyChatCommand::class })
    }

    @Test
    fun `pc root keeps legacy name and aliases`() {
        assertEquals("pc", definition.annotation.name)
        assertEquals(listOf("pchat", "partychat"), definition.annotation.aliases.toList())
    }

    @Test
    fun `pc exposes exactly default switch toggle help and clear routes`() {
        assertEquals(
            setOf(emptyList(), listOf("switch"), listOf("toggle"), listOf("help"), listOf("clear")),
            definition.subcommands.map { it.path }.toSet(),
        )
        assertEquals(5, definition.subcommands.size, "one route per legacy path")
        for (route in definition.subcommands) {
            assertEquals(permission, route.permission, "${route.path} permission")
            assertTrue(route.isPlayerOnly, "${route.path} must stay player-only")
            assertEquals(1, route.parameters.count { it.isContext }, "${route.path} player context")
        }
    }

    @Test
    fun `default route takes one optional greedy message with party suggestions`() {
        val route = definition.subcommands.single { it.path.isEmpty() }
        val message = route.parameters.single { it.isArg }
        assertEquals("message", message.name)
        assertTrue(message.isOptional)
        assertEquals(setOf("message"), route.greedyParameters)
        assertEquals(mapOf("message" to NexusPartyChatSuggestions.KEY), route.suggestions)

        val handler = NexusPartyChatCommand::class.java.declaredMethods.single { it.name == "execute" }
        assertEquals(listOf(Player::class.java, String::class.java), handler.parameterTypes.toList())
        assertTrue(handler.parameterAnnotations.last().any { it is Greedy })
        assertEquals(
            NexusPartyChatSuggestions.KEY,
            handler.parameterAnnotations.last().filterIsInstance<Suggests>().single().value,
        )
    }

    @Test
    fun `switch route takes one optional non-greedy target with party suggestions`() {
        val route = definition.subcommands.single { it.path == listOf("switch") }
        val target = route.parameters.single { it.isArg }
        assertEquals("target", target.name)
        assertTrue(target.isOptional, "bare /pc switch lists parties")
        assertTrue(route.greedyParameters.isEmpty(), "legacy switch target is a single word")
        assertEquals(mapOf("target" to NexusPartyChatSuggestions.KEY), route.suggestions)
    }

    @Test
    fun `toggle help and clear take no arguments`() {
        for (path in listOf("toggle", "help", "clear")) {
            val route = definition.subcommands.single { it.path == listOf(path) }
            assertEquals(0, route.parameters.count { it.isArg }, path)
            assertTrue(route.suggestions.isEmpty(), path)
        }
    }

    @Test
    fun `suggestion key matches the legacy ACF completion id`() {
        assertEquals("parties", NexusPartyChatSuggestions.KEY)
    }

    @Test
    fun `transitional ACF adapter keeps legacy metadata`() {
        val type = PartyChatCommand::class.java
        assertEquals("pc|pchat|partychat", type.getAnnotation(CommandAlias::class.java).value)

        val methods = type.declaredMethods.filter { it.getAnnotation(CommandPermission::class.java) != null }
        assertTrue(methods.all { it.getAnnotation(CommandPermission::class.java).value == permission })

        val defaults = methods.filter { it.getAnnotation(Default::class.java) != null }
        assertEquals(setOf(1, 2, 3), defaults.map { it.parameterCount }.toSet(), "no-arg, single and vararg defaults")
        assertEquals(
            "@parties switch help toggle clear",
            defaults.single { it.parameterCount == 2 }.getAnnotation(CommandCompletion::class.java).value,
        )

        val subcommands = methods.filter { it.getAnnotation(AcfSubcommand::class.java) != null }
        assertEquals(
            listOf("clear", "help", "switch", "switch", "toggle"),
            subcommands.map { it.getAnnotation(AcfSubcommand::class.java).value }.sorted(),
        )
        val switchWithTarget = subcommands.single {
            it.getAnnotation(AcfSubcommand::class.java).value == "switch" && it.parameterCount == 2
        }
        assertEquals("GLOBAL|@parties", switchWithTarget.getAnnotation(CommandCompletion::class.java).value)
        assertNull(defaults.single { it.parameterCount == 1 }.getAnnotation(CommandCompletion::class.java))
    }
}
