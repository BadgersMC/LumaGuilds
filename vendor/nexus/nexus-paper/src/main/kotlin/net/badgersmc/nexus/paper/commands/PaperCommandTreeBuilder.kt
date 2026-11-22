package net.badgersmc.nexus.paper.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers
import org.bukkit.entity.Player
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy

/**
 * Builds the Brigadier tree for one composed command root. Independent of the Paper
 * lifecycle/plugin so the produced tree can be exercised with a real CommandDispatcher.
 */
internal class PaperCommandTreeBuilder(
    private val nexusScope: CoroutineScope,
    private val suggestionProviders: Map<String, SuggestionProvider<CommandSourceStack>> = emptyMap()
) {
    private val logger = LoggerFactory.getLogger(PaperCommandRegistry::class.java)

    private data class Route(val metadata: PaperSubcommandDefinition, val bean: Any)
    private class LiteralNode(val name: String) {
        val children = linkedMapOf<String, LiteralNode>()
        var handler: Route? = null

        fun reachableRoutes(): List<Route> =
            listOfNotNull(handler) + children.values.flatMap { it.reachableRoutes() }
    }

    fun build(
        plan: PaperCommandPlanner.Root,
        beanFor: (PaperCommandDefinition) -> Any
    ): LiteralCommandNode<CommandSourceStack> {
        val routes = plan.definitions.flatMap { definition ->
            val bean = beanFor(definition)
            definition.subcommands.map { Route(it, bean) }
        }
        return buildCommandTree(plan, routes)
    }

    private fun buildCommandTree(
        plan: PaperCommandPlanner.Root,
        routes: List<Route>
    ): LiteralCommandNode<CommandSourceStack> {
        val root = Commands.literal(plan.name)
        if (plan.permission.isNotEmpty()) {
            root.requires { it.sender.hasPermission(plan.permission) }
        }

        val nodes = linkedMapOf<String, LiteralNode>()
        routes.forEach { route ->
            if (route.metadata.path.isEmpty()) {
                // Root/default handler uses the same argument path as literal handlers:
                // required/optional/greedy @Args plus per-route permission/player-only checks.
                attachArguments(root, route)
            } else {
                var current = nodes.getOrPut(route.metadata.path[0]) { LiteralNode(route.metadata.path[0]) }
                route.metadata.path.drop(1).forEach { literal ->
                    current = current.children.getOrPut(literal) { LiteralNode(literal) }
                }
                current.handler = route
            }
        }
        nodes.values.forEach { node -> root.then(buildLiteral(node)) }
        return root.build()
    }

    private fun buildLiteral(node: LiteralNode): LiteralArgumentBuilder<CommandSourceStack> {
        val builder = Commands.literal(node.name)
        // Shared intermediate nodes must remain reachable for any authorized descendant.
        // Individual handlers still verify their own permission before invocation.
        val routes = node.reachableRoutes()
        builder.requires { source -> routes.any { allowed(source, it.metadata) } }
        node.handler?.let { attachArguments(builder, it) }
        node.children.values.forEach { child -> builder.then(buildLiteral(child)) }
        return builder
    }

    private fun attachArguments(
        literal: LiteralArgumentBuilder<CommandSourceStack>,
        route: Route
    ) {
        val args = route.metadata.parameters.filter { it.isArg }
        val builders = args.map { param ->
            val resolver = PaperArgumentResolvers.get(param.type)
                ?: throw CommandException("No resolver for ${param.type.simpleName}")
            val type = if (param.name in route.metadata.greedyParameters) {
                StringArgumentType.greedyString()
            } else {
                resolver.argumentType()
            }
            @Suppress("UNCHECKED_CAST")
            val builder = Commands.argument(param.name, type as com.mojang.brigadier.arguments.ArgumentType<Any>)
            route.metadata.suggestions[param.name]?.let { providerName ->
                val provider = suggestionProviders[providerName]
                    ?: throw CommandException("No suggestion provider '$providerName' for '${param.name}'")
                builder.suggests(provider)
            }
            builder
        }

        if (builders.isEmpty()) {
            literal.executes { ctx -> runRoute(ctx, route) }
            return
        }

        // Optional trailing arguments add executable prefixes, including the literal
        // itself when every @Arg is optional.
        if (args.all { it.isOptional }) {
            literal.executes { ctx -> runRoute(ctx, route) }
        }
        for (i in builders.indices) {
            if (i == builders.lastIndex || args.drop(i + 1).all { it.isOptional }) {
                builders[i].executes { ctx -> runRoute(ctx, route) }
            }
        }
        for (i in builders.size - 2 downTo 0) {
            builders[i].then(builders[i + 1])
        }
        builders.first().requires { source -> allowed(source, route.metadata) }
        literal.then(builders.first())
    }

    private fun allowed(source: CommandSourceStack, sub: PaperSubcommandDefinition): Boolean =
        (sub.permission == null || source.sender.hasPermission(sub.permission)) &&
            (!sub.isPlayerOnly || source.sender is Player)

    private fun runRoute(ctx: CommandContext<CommandSourceStack>, route: Route): Int {
        if (!allowed(ctx.source, route.metadata)) {
            ctx.source.sender.sendMessage("You do not have permission to use this command.")
            return 0
        }

        val sub = route.metadata
        if (sub.isAsync) {
            nexusScope.launch {
                try {
                    invoke(ctx, route)
                } catch (e: Exception) {
                    logger.error("Async command '${sub.path}' failed", e)
                }
            }
        } else {
            try {
                // A suspend method is marked async by the scanner.
                invokeSync(ctx, route)
            } catch (e: Exception) {
                logger.error("Command '${sub.path}' failed", e)
                return 0
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun argumentValues(
        ctx: CommandContext<CommandSourceStack>,
        route: Route
    ): Map<KParameter, Any?> {
        val sub = route.metadata
        val method = sub.method
        val values = linkedMapOf<KParameter, Any?>(method.parameters.first() to route.bean)
        sub.parameters.forEachIndexed { index, param ->
            val reflected = method.parameters[index + 1]
            if (param.isContext) {
                values[reflected] = contextValue(ctx.source, param.type.simpleName)
            } else {
                when {
                    hasArgument(ctx, param.name) ->
                        values[reflected] = providedValue(ctx, param)
                    !param.argAnnotation?.defaultValue.isNullOrEmpty() ->
                        values[reflected] = parseDefault(param.type, param.argAnnotation!!.defaultValue)
                    reflected.isOptional -> Unit // Kotlin callBy applies the method default
                    reflected.type.isMarkedNullable -> values[reflected] = null
                    else -> throw CommandException("Missing required argument '${param.name}'")
                }
            }
        }
        return values
    }

    private fun contextValue(source: CommandSourceStack, typeName: String?): Any = when (typeName) {
        "CommandSourceStack" -> source
        "Player" -> source.sender as? Player
            ?: throw CommandException("Command requires a player sender")
        "CommandSender" -> source.sender
        "Server" -> source.sender.server
        else -> throw CommandException("Unsupported @Context type '$typeName'")
    }

    private fun providedValue(
        ctx: CommandContext<CommandSourceStack>,
        param: net.badgersmc.nexus.commands.CommandParameter
    ): Any {
        val resolver = PaperArgumentResolvers.get(param.type)
            ?: throw CommandException("No resolver for ${param.type.simpleName}")
        return resolver.extract(ctx, param.name)
    }

    private fun hasArgument(ctx: CommandContext<CommandSourceStack>, name: String): Boolean =
        try {
            ctx.getArgument(name, Any::class.java)
            true
        } catch (_: IllegalArgumentException) {
            false
        }

    private fun parseDefault(type: KClass<*>, value: String): Any = when (type) {
        String::class -> value
        Int::class -> value.toInt()
        Long::class -> value.toLong()
        Double::class -> value.toDouble()
        Float::class -> value.toFloat()
        Boolean::class -> value.toBooleanStrict()
        else -> throw CommandException("Unsupported default value type: ${type.simpleName}")
    }

    private fun invokeSync(ctx: CommandContext<CommandSourceStack>, route: Route) {
        route.metadata.method.callBy(argumentValues(ctx, route))
    }

    private suspend fun invoke(ctx: CommandContext<CommandSourceStack>, route: Route) {
        val values = argumentValues(ctx, route)
        if (route.metadata.method.isSuspend) route.metadata.method.callSuspendBy(values)
        else route.metadata.method.callBy(values)
    }
}
