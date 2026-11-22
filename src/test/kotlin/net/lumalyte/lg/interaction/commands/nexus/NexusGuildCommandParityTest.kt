package net.lumalyte.lg.interaction.commands.nexus

import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Single
import co.aikar.commands.annotation.Subcommand
import net.lumalyte.lg.interaction.commands.GuildCommand
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-1 command parity for the `/guild` family.
 *
 * The expected route table is derived reflectively from the authoritative ACF [GuildCommand]
 * annotations (alias expansion, `@Default`, permissions, player-only, `@Optional`, and ACF's
 * "final String parameter consumes the rest of the line" rule) and compared with what the pinned
 * Nexus scanner discovers under the merged `guild` root.
 */
class NexusGuildCommandParityTest {

    data class ArgSpec(
        val name: String,
        val type: String,
        val optional: Boolean,
        val greedy: Boolean,
        val suggestions: String?,
    )

    data class RouteSpec(
        val path: String,
        val permission: String?,
        val playerOnly: Boolean,
        val args: List<ArgSpec>,
    )

    @Test
    fun `ACF guild command exposes 61 handlers and 71 alias-expanded routes`() {
        val handlers = acfHandlers()
        assertEquals(61, handlers.size, "ACF handler count")
        assertEquals(71, acfRoutes().size, "ACF alias-expanded route count (including default)")
    }

    @Test
    fun `every ACF guild route exists in Nexus with identical permission player-only arguments greediness and suggestions`() {
        val expected = acfRoutes().associateBy { it.path }
        val actual = nexusRoutes()
        val actualByPath = actual.associateBy { it.path }

        val missing = expected.keys - actualByPath.keys
        val extra = actualByPath.keys - expected.keys
        assertTrue(missing.isEmpty() && extra.isEmpty(), "Missing Nexus routes: $missing; extra Nexus routes: $extra")
        expected.values.forEach { route ->
            assertEquals(route, actualByPath.getValue(route.path), "Route /guild ${route.path}")
        }
    }

    @Test
    fun `guild root keeps alias g and declares each route exactly once including help`() {
        val definitions = guildRootDefinitions()
        assertTrue(definitions.isNotEmpty())
        definitions.forEach { definition ->
            assertEquals(listOf("g"), definition.annotation.aliases.toList(), "${definition.commandClass.simpleName} aliases")
            assertEquals("", definition.annotation.permission, "root permission must stay route-scoped")
        }
        val paths = nexusRoutes().map { it.path }
        assertEquals(paths.distinct(), paths, "duplicate /guild routes")
        assertEquals(1, paths.count { it == "help" }, "exactly one /guild help route")
        // The pinned planner itself must accept the merged root (it throws on duplicates / conflicts).
        val root = planRoots(definitions).single()
        assertEquals(listOf("g"), planAliases(root))
    }

    @Test
    fun `guild arguments never use online-only Player resolvers so offline names reach handlers`() {
        nexusRoutes().flatMap { it.args }.forEach { arg ->
            assertTrue(arg.type in setOf("String", "Int"), "Argument ${arg.name} uses ${arg.type}")
        }
    }

    // ---- ACF side -------------------------------------------------------------------------

    private fun acfHandlers() = GuildCommand::class.declaredFunctions
        .filter { it.hasAnnotation<Subcommand>() || it.hasAnnotation<Default>() }

    private fun acfRoutes(): List<RouteSpec> = acfHandlers().flatMap { function ->
        val paths = function.findAnnotation<Subcommand>()?.value?.let(::expandAcfAliases) ?: listOf("")
        val parameters = function.parameters.drop(1) // receiver
        val playerOnly = parameters.firstOrNull()?.type?.classifier == Player::class
        val userParameters = if (playerOnly) parameters.drop(1) else parameters
        val completions = function.findAnnotation<CommandCompletion>()?.value?.split(" ") ?: emptyList()
        val args = userParameters.mapIndexed { index, parameter ->
            val type = (parameter.type.classifier as KClass<*>).simpleName!!
            ArgSpec(
                name = parameter.name!!,
                type = type,
                optional = parameter.hasAnnotation<Optional>(),
                greedy = index == userParameters.lastIndex && type == "String" && !parameter.hasAnnotation<Single>(),
                suggestions = completions.getOrNull(index)?.let(::nexusSuggestionName),
            )
        }
        val permission = function.findAnnotation<CommandPermission>()?.value
        paths.map { RouteSpec(it, permission, playerOnly, args) }
    }

    /** ACF `a|b c` means alternatives for each space-separated token. */
    private fun expandAcfAliases(value: String): List<String> =
        value.trim().split(" ").filter { it.isNotEmpty() }
            .fold(listOf(emptyList<String>())) { prefixes, token ->
                prefixes.flatMap { prefix -> token.split("|").map { prefix + it } }
            }
            .map { it.joinToString(" ") }

    // ---- Nexus side -----------------------------------------------------------------------

    private fun nexusRoutes(): List<RouteSpec> = guildRootDefinitions().flatMap { definition ->
        definition.subcommands.map { sub ->
            val contexts = sub.parameters.filter { it.isContext }
            assertTrue(contexts.all { it.type == Player::class }, "/guild ${sub.path} context types")
            RouteSpec(
                path = sub.path.joinToString(" "),
                permission = sub.permission,
                playerOnly = sub.isPlayerOnly && contexts.isNotEmpty(),
                args = sub.parameters.filter { it.isArg }.map { arg ->
                    ArgSpec(
                        name = arg.name,
                        type = arg.type.simpleName!!,
                        optional = arg.isOptional,
                        greedy = arg.name in sub.greedyParameters,
                        suggestions = sub.suggestions[arg.name],
                    )
                },
            )
        }
    }

    companion object {
        /** Every Nexus suggestion key the guild family may reference. */
        val suggestionKeys: List<String> = listOf(
            "allplayers", "guildmembers", "guilds", "pendinginvites", "guildsorplayers", "allyguilds",
            "guildhomes", "guildaccessiblehomes", "unlockedemojis", "players", "onoff", "onofftoggle", "modes",
        ).map { "guild.$it" }

        /** ACF completion id → Nexus suggestion provider key (guild-prefixed to avoid central-map collisions). */
        fun nexusSuggestionName(acfCompletion: String): String = when {
            acfCompletion.startsWith("@") -> "guild." + acfCompletion.removePrefix("@")
            acfCompletion == "on|off" -> "guild.onoff"
            acfCompletion == "on|off|toggle" -> "guild.onofftoggle"
            acfCompletion == "peaceful|hostile" -> "guild.modes"
            else -> error("Unmapped ACF completion '$acfCompletion'")
        }
    }
}
