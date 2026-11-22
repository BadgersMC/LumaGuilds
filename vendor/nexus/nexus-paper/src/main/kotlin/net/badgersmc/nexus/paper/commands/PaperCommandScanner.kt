package net.badgersmc.nexus.paper.commands

import io.github.classgraph.ClassGraph
import net.badgersmc.nexus.commands.CommandException
import net.badgersmc.nexus.commands.CommandParameter
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Async
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

class PaperCommandScanner {
    private val logger = LoggerFactory.getLogger(PaperCommandScanner::class.java)

    fun scanCommands(basePackage: String, classLoader: ClassLoader): List<PaperCommandDefinition> {
        val commandClasses: List<KClass<*>> = ClassGraph()
            .acceptPackages(basePackage)
            .addClassLoader(classLoader)
            .enableAnnotationInfo()
            .scan()
            .use { result ->
                result.getClassesWithAnnotation(Command::class.java.name)
                    .filter { !it.isAbstract && !it.isInterface }
                    .mapNotNull { classInfo -> classInfo.loadClass().kotlin }
            }

        return commandClasses.map { processClass(it) }.also { list ->
            logger.info("Discovered {} Paper command classes in package '{}'", list.size, basePackage)
        }
    }

    // Multiple distinct validation failures intentionally use typed CommandExceptions.
    @Suppress("ThrowsCount")
    internal fun processClass(klass: KClass<*>): PaperCommandDefinition {
        val annotation = klass.findAnnotation<Command>()
            ?: throw CommandException("Class ${klass.simpleName} missing @Command")

        val subcommands = klass.functions
            .filter { it.hasAnnotation<Subcommand>() }
            .map { method ->
                val subAnnotation = method.findAnnotation<Subcommand>()!!
                // Empty @Subcommand("") is the root/default route.
                val path = subAnnotation.value.trim().split(" ").filter { it.isNotEmpty() }
                val permission = method.findAnnotation<Permission>()?.value
                    ?: annotation.permission.ifEmpty { null }
                val isPlayerOnly = method.hasAnnotation<PlayerOnly>()
                val isAsync = method.hasAnnotation<Async>() || method.isSuspend

                val suggestionsMap = mutableMapOf<String, String>()
                val greedyParameters = mutableSetOf<String>()
                val parameters = method.parameters.drop(1)
                    .mapIndexed { idx, param ->
                        val result = buildParameter(klass, method, param, idx, suggestionsMap)
                        if (param.hasAnnotation<Greedy>()) {
                            if (!result.isArg || result.type != String::class) {
                                throw CommandException("@Greedy requires a String @Arg in ${klass.simpleName}::${method.name}")
                            }
                            greedyParameters.add(result.name)
                        }
                        result
                    }
                val args = parameters.filter { it.isArg }
                if (args.dropLast(1).any { it.name in greedyParameters }) {
                    throw CommandException("@Greedy must be the final @Arg in ${klass.simpleName}::${method.name}")
                }
                if (args.dropLastWhile { it.isOptional }.any { it.isOptional }) {
                    throw CommandException("Required @Arg after optional @Arg in ${klass.simpleName}::${method.name}")
                }
                // Non-null optional parameters without a Kotlin default or annotation default
                // cannot be invoked when Brigadier omits that argument.
                parameters.forEachIndexed { index, parameter ->
                    val reflected = method.parameters[index + 1]
                    val hasKotlinDefault = reflected.isOptional || reflected.type.isMarkedNullable
                    val hasAnnotationDefault = !parameter.argAnnotation?.defaultValue.isNullOrEmpty()
                    if (parameter.isOptional && !hasKotlinDefault && !hasAnnotationDefault) {
                        throw CommandException("Optional '${parameter.name}' needs a Kotlin default, nullable type or @Arg defaultValue")
                    }
                }

                PaperSubcommandDefinition(
                    path, method, parameters, permission, isPlayerOnly, isAsync,
                    suggestionsMap, greedyParameters
                )
            }

        if (subcommands.isEmpty()) throw CommandException(
            "Command class ${klass.simpleName} has no @Subcommand methods"
        )

        return PaperCommandDefinition(klass, annotation, subcommands)
    }

    private fun buildParameter(
        klass: KClass<*>,
        method: kotlin.reflect.KFunction<*>,
        param: kotlin.reflect.KParameter,
        idx: Int,
        suggestionsMap: MutableMap<String, String>
    ): CommandParameter {
        val arg = param.findAnnotation<Arg>()
        val ctx = param.findAnnotation<Context>()
        val suggests = param.findAnnotation<Suggests>()
        if (arg == null && ctx == null) throw CommandException(
            "Method ${method.name} parameter '${param.name}' needs @Arg or @Context"
        )
        if (arg != null && !PaperArgumentResolvers.hasResolver(param.type.classifier as KClass<*>)) {
            throw CommandException(
                "No PaperArgumentResolver for type '${(param.type.classifier as KClass<*>).simpleName}' " +
                    "in ${klass.simpleName}::${method.name}"
            )
        }
        val name = arg?.name?.takeIf { it.isNotBlank() } ?: param.name ?: "param$idx"
        if (suggests != null && arg != null) {
            suggestionsMap[name] = suggests.value
        }
        return CommandParameter(
            name = name,
            type = param.type.classifier as KClass<*>,
            index = idx,
            argAnnotation = arg,
            contextAnnotation = ctx
        )
    }
}
