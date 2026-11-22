package net.badgersmc.nexus.paper.commands

import net.badgersmc.nexus.commands.CommandException

/**
 * Groups command classes by their shared Brigadier root, without changing the public
 * @Command API. An ACF-style split can therefore define /guild subcommands in
 * independent classes without registering competing root command trees.
 */
internal object PaperCommandPlanner {
    data class Root(
        val name: String,
        val description: String,
        val permission: String,
        val aliases: List<String>,
        val definitions: List<PaperCommandDefinition>
    )

    // Root validation rejects distinct configuration errors rather than masking conflicts.
    @Suppress("ThrowsCount")
    fun plan(definitions: List<PaperCommandDefinition>): List<Root> {
        val roots = definitions.groupBy { it.annotation.name.lowercase() }
        val occupiedNames = mutableMapOf<String, String>()
        return roots.map { (name, group) ->
            val permissions = group.map { it.annotation.permission }.distinct()
            if (permissions.size != 1) {
                throw CommandException("Conflicting root permissions for /$name: $permissions")
            }
            val descriptions = group.map { it.annotation.description }.filter { it.isNotBlank() }.distinct()
            if (descriptions.size > 1) {
                throw CommandException("Conflicting root descriptions for /$name: $descriptions")
            }
            val paths = mutableMapOf<String, String>()
            group.forEach { definition ->
                definition.subcommands.forEach { sub ->
                    val path = sub.path.joinToString(" ").lowercase()
                    val owner = "${definition.commandClass.simpleName}::${sub.method.name}"
                    val previous = paths.putIfAbsent(path, owner)
                    if (previous != null) {
                        throw CommandException("Duplicate /$name $path handlers: $previous and $owner")
                    }
                }
            }
            val aliases = group.flatMap { it.annotation.aliases.toList() }
                .filterNot { it.equals(name, ignoreCase = true) }
                .distinctBy { it.lowercase() }
            (listOf(name) + aliases).forEach { alias ->
                val previous = occupiedNames.putIfAbsent(alias.lowercase(), name)
                if (previous != null && previous != name) {
                    throw CommandException("Command alias '$alias' conflicts between /$previous and /$name")
                }
            }
            Root(name, descriptions.firstOrNull() ?: "", permissions.single(), aliases, group)
        }
    }
}
