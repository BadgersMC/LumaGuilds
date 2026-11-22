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
import net.badgersmc.nexus.paper.commands.PaperCommandRegistry
import org.bukkit.plugin.java.JavaPlugin

private val claimDefinitions by lazy {
    scanGuildCommandDefinitions()
}

/** Builds the actual pinned Nexus command tree; only its private lifecycle adapter is bypassed. */
@Suppress("UNCHECKED_CAST")
internal fun claimDispatcher(
    handler: Any,
    suggestions: Map<String, SuggestionProvider<CommandSourceStack>> = emptyMap(),
): CommandDispatcher<CommandSourceStack> {
    val definitions = claimDefinitions.filter { it.commandClass.java == handler.javaClass }
    check(definitions.size == 1) { "Expected one command definition for ${handler.javaClass.name}" }
    val planner = Class.forName("net.badgersmc.nexus.paper.commands.PaperCommandPlanner")
    val plans = planner.getDeclaredMethod("plan", List::class.java).apply { isAccessible = true }
        .invoke(planner.getDeclaredField("INSTANCE").get(null), definitions) as List<*>
    val routeClass = Class.forName("net.badgersmc.nexus.paper.commands.PaperCommandRegistry\$Route")
    val routeConstructor = routeClass.declaredConstructors.single().apply { isAccessible = true }
    val routes = definitions.single().subcommands.map { routeConstructor.newInstance(it, handler) }
    val registry = PaperCommandRegistry(
        mockk<JavaPlugin>(), CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), mockk<BeanFactory>(), suggestions,
    )
    val tree = PaperCommandRegistry::class.java.declaredMethods.single { it.name == "buildCommandTree" }
        .apply { isAccessible = true }.invoke(registry, plans.single(), routes) as LiteralCommandNode<CommandSourceStack>
    return CommandDispatcher<CommandSourceStack>().apply { root.addChild(tree) }
}
