package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.LiteralCommandNode
import io.mockk.mockk
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.badgersmc.nexus.core.BeanFactory
import net.badgersmc.nexus.paper.commands.PaperCommandDefinition
import net.badgersmc.nexus.paper.commands.PaperCommandRegistry
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import org.bukkit.plugin.java.JavaPlugin
import kotlin.reflect.KClass

/** Package holding every inactive Nexus command class (claims, chat and guild families). */
internal const val NEXUS_COMMAND_PACKAGE = "net.lumalyte.lg.interaction.commands.nexus"

/** All scanned Nexus definitions merged under the `/guild` root, exactly as the planner groups them. */
internal fun guildRootDefinitions(): List<PaperCommandDefinition> =
    PaperCommandScanner().scanCommands(NEXUS_COMMAND_PACKAGE, NexusGuildFixtureAnchor::class.java.classLoader)
        .filter { it.annotation.name.equals("guild", ignoreCase = true) }

/** Invokes the pinned (internal) `PaperCommandPlanner.plan`, which rejects duplicate routes per root. */
internal fun planRoots(definitions: List<PaperCommandDefinition>): List<Any> {
    val planner = Class.forName("net.badgersmc.nexus.paper.commands.PaperCommandPlanner")
    return planner.getDeclaredMethod("plan", List::class.java).apply { isAccessible = true }
        .invoke(planner.getDeclaredField("INSTANCE").get(null), definitions) as List<Any>
}

/** Reads `aliases` from a planner Root. */
@Suppress("UNCHECKED_CAST")
internal fun planAliases(root: Any): List<String> =
    root.javaClass.getDeclaredMethod("getAliases").invoke(root) as List<String>

internal class GuildDispatcher(
    val dispatcher: CommandDispatcher<CommandSourceStack>,
    val beans: List<Any>,
)

/**
 * Builds the actual pinned Nexus Brigadier tree for the merged `/guild` root. Command beans are
 * constructed by constructor-parameter type from [beans]; only Paper's lifecycle registrar (which
 * also attaches root aliases such as `/g`) is bypassed.
 */
@Suppress("UNCHECKED_CAST")
internal fun guildDispatcher(
    beans: Map<KClass<*>, Any>,
    suggestions: Map<String, SuggestionProvider<CommandSourceStack>>,
): GuildDispatcher {
    val definitions = guildRootDefinitions()
    val plan = planRoots(definitions).single()
    val routeClass = Class.forName("net.badgersmc.nexus.paper.commands.PaperCommandRegistry\$Route")
    val routeConstructor = routeClass.declaredConstructors.single().apply { isAccessible = true }
    val instances = mutableListOf<Any>()
    val routes = definitions.flatMap { definition ->
        val bean = instantiate(definition.commandClass, beans).also { instances += it }
        definition.subcommands.map { routeConstructor.newInstance(it, bean) }
    }
    val registry = PaperCommandRegistry(
        mockk<JavaPlugin>(), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), mockk<BeanFactory>(), suggestions,
    )
    val tree = PaperCommandRegistry::class.java.declaredMethods.single { it.name == "buildCommandTree" }
        .apply { isAccessible = true }.invoke(registry, plan, routes) as LiteralCommandNode<CommandSourceStack>
    return GuildDispatcher(CommandDispatcher<CommandSourceStack>().apply { root.addChild(tree) }, instances)
}

private fun instantiate(type: KClass<*>, beans: Map<KClass<*>, Any>): Any {
    val constructor = type.constructors.single()
    val arguments = constructor.parameters.associateWith { parameter ->
        val wanted = parameter.type.classifier as KClass<*>
        beans[wanted] ?: error("No test bean for ${wanted.qualifiedName} required by ${type.simpleName}")
    }
    return constructor.callBy(arguments)
}

private object NexusGuildFixtureAnchor
