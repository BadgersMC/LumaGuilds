package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack

private val claimDefinitions by lazy { scanGuildCommandDefinitions() }

internal fun claimDispatcher(
    handler: Any,
    suggestions: Map<String, SuggestionProvider<CommandSourceStack>> = emptyMap(),
): CommandDispatcher<CommandSourceStack> {
    val definition = claimDefinitions.single { it.commandClass == handler::class }
    return registerCommandDefinitions(listOf(definition), mapOf(handler::class to handler), suggestions).dispatcher
}
