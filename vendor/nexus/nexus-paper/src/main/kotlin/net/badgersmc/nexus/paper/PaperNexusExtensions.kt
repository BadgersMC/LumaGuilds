package net.badgersmc.nexus.paper

import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CoroutineScope
import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.paper.commands.PaperCommandRegistry
import net.badgersmc.nexus.paper.commands.PaperCommandScanner
import org.bukkit.plugin.java.JavaPlugin

/**
 * Scan for @Command classes and register them with Paper's Brigadier system.
 * Call this from onEnable(), after creating your NexusContext.
 *
 * Example:
 * ```kotlin
 * nexus.registerPaperCommands(
 *     basePackage = "net.lumalyte.lumasg",
 *     classLoader = this::class.java.classLoader,
 *     plugin = this,
 *     suggestionProviders = mapOf(
 *         "arenaNames" to SuggestionProvider { _, builder ->
 *             arenaService.getAllArenas().forEach { builder.suggest(it.name) }
 *             builder.buildFuture()
 *         }
 *     )
 * )
 * ```
 *
 * @param suggestionProviders Named providers for @Suggests-annotated @Arg parameters.
 */
fun NexusContext.registerPaperCommands(
    basePackage: String,
    classLoader: ClassLoader,
    plugin: JavaPlugin,
    coroutineScope: CoroutineScope = this.scope
        ?: throw IllegalStateException("NexusContext has no coroutine scope — provide one explicitly"),
    suggestionProviders: Map<String, SuggestionProvider<CommandSourceStack>> = emptyMap()
) {
    val scanner = PaperCommandScanner()
    val definitions = scanner.scanCommands(basePackage, classLoader)
    val registry = PaperCommandRegistry(plugin, coroutineScope, this.getBeanFactory(), suggestionProviders)
    registry.registerAll(definitions)
}
