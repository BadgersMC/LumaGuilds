package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.badgersmc.nexus.core.BeanFactory
import net.badgersmc.nexus.paper.commands.PaperCommandDefinition
import net.badgersmc.nexus.paper.commands.PaperCommandRegistry
import org.bukkit.plugin.java.JavaPlugin
import org.mockbukkit.mockbukkit.command.brigadier.PaperCommandsMock
import kotlin.reflect.KClass

/** All scanned Nexus definitions merged under the `/guild` root, exactly as the planner groups them. */
internal fun guildRootDefinitions(): List<PaperCommandDefinition> =
    scanGuildCommandDefinitions()
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

/** Runs the public Nexus registry callback with MockBukkit's Paper registrar and alias handling. */
internal fun guildDispatcher(
    beans: Map<KClass<*>, Any>,
    suggestions: Map<String, SuggestionProvider<CommandSourceStack>>,
): GuildDispatcher = commandDispatcher(setOf("guild"), beans, suggestions)

internal fun commandDispatcher(
    roots: Set<String>,
    beans: Map<KClass<*>, Any>,
    suggestions: Map<String, SuggestionProvider<CommandSourceStack>> = emptyMap(),
): GuildDispatcher {
    val definitions = scanGuildCommandDefinitions()
        .filter { it.annotation.name in roots }
    return registerCommandDefinitions(definitions, beans, suggestions)
}

internal fun registerCommandDefinitions(
    definitions: List<PaperCommandDefinition>,
    beans: Map<KClass<*>, Any>,
    suggestions: Map<String, SuggestionProvider<CommandSourceStack>> = emptyMap(),
): GuildDispatcher {
    val plugin = mockk<JavaPlugin>(relaxed = true)
    every { plugin.pluginMeta.name } returns "LumaGuilds"
    val registrar = PaperCommandsMock().apply {
        newDispatcher()
        setCurrentContext(plugin)
    }
    val event = mockk<ReloadableRegistrarEvent<Commands>>()
    every { event.registrar() } returns registrar
    every {
        plugin.lifecycleManager.registerEventHandler(
            LifecycleEvents.COMMANDS,
            any<LifecycleEventHandler<ReloadableRegistrarEvent<Commands>>>(),
        )
    } answers {
        secondArg<LifecycleEventHandler<ReloadableRegistrarEvent<Commands>>>().run(event)
    }
    val factory = mockk<BeanFactory>()
    val instances = mutableListOf<Any>()
    every { factory.createFactory(any<KClass<Any>>()) } answers {
        val type = firstArg<KClass<Any>>()
        val create = { (beans[type] ?: instantiate(type, beans)).also { instances += it } }
        create
    }
    PaperCommandRegistry(plugin, CoroutineScope(Dispatchers.Unconfined), factory, suggestions).registerAll(definitions)
    return GuildDispatcher(registrar.dispatcher, instances)
}

private fun instantiate(type: KClass<*>, beans: Map<KClass<*>, Any>): Any {
    val constructor = type.constructors.single()
    val arguments = constructor.parameters.associateWith { parameter ->
        val wanted = parameter.type.classifier as KClass<*>
        beans[wanted] ?: error("No test bean for ${wanted.qualifiedName} required by ${type.simpleName}")
    }
    return constructor.callBy(arguments)
}
