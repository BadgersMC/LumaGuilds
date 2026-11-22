package net.badgersmc.nexus.paper.commands

import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineScope
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.core.BeanFactory
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory

class PaperCommandRegistry(
    private val plugin: JavaPlugin,
    nexusScope: CoroutineScope,
    private val beanFactory: BeanFactory,
    suggestionProviders: Map<String, SuggestionProvider<CommandSourceStack>> = emptyMap()
) {
    private val logger = LoggerFactory.getLogger(PaperCommandRegistry::class.java)
    private val treeBuilder = PaperCommandTreeBuilder(nexusScope, suggestionProviders)

    fun registerAll(definitions: List<PaperCommandDefinition>) {
        val plans = PaperCommandPlanner.plan(definitions) // fail before registering the lifecycle callback
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            for (plan in plans) {
                try {
                    val tree = treeBuilder.build(plan) { definition ->
                        beanFactory.createFactory(definition.commandClass).invoke()
                    }
                    event.registrar().register(tree, plan.description, plan.aliases)
                    logger.debug(
                        "Registered Paper command '/{}' with {} handlers",
                        plan.name,
                        plan.definitions.sumOf { it.subcommands.size }
                    )
                } catch (e: Exception) {
                    logger.error("Failed to register command '${plan.name}'", e)
                    throw CommandException("Command registration failed: ${e.message}", e)
                }
            }
            logger.info("Registered {} Paper command roots from {} classes", plans.size, definitions.size)
        }
    }
}
