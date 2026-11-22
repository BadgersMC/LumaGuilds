package net.badgersmc.nexus.paper.commands

import net.badgersmc.nexus.commands.CommandParameter
import net.badgersmc.nexus.commands.annotations.Command
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/** Full metadata for a command handler discovered via classpath scanning. */
data class PaperCommandDefinition(
    val commandClass: KClass<*>,
    val annotation: Command,
    val subcommands: List<PaperSubcommandDefinition>
)

/** Metadata for one @Subcommand method. An empty path is the root/default handler. */
data class PaperSubcommandDefinition(
    val path: List<String>,
    val method: KFunction<*>,
    val parameters: List<CommandParameter>,
    val permission: String?,
    val isPlayerOnly: Boolean,
    val isAsync: Boolean,
    val suggestions: Map<String, String> = emptyMap(),
    val greedyParameters: Set<String> = emptySet()
)
