package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import net.lumalyte.lg.interaction.commands.PartyChatHandler
import org.bukkit.entity.Player
import java.util.Locale

/*
 * ACF-free equivalent of the legacy /pc|pchat|partychat command (PartyChatCommand).
 *
 * ACF exposed three @Default overloads (none / one word / vararg) and two `switch` overloads.
 * Nexus gets exactly one default route with an optional greedy message and one `switch` route
 * with an optional target; all behavior lives in the shared PartyChatHandler.
 *
 * INACTIVE: not registered until the all-at-once Nexus command cutover; the legacy ACF command
 * remains authoritative. The default greedy route additionally depends on the Nexus root-argument
 * fix (pinned 5ae8b642 attaches no argument nodes to `@Subcommand("")` routes).
 */

private const val PARTY_CHAT_PERMISSION = "lumaguilds.partychat"

/** `/pc [message|party]`, `/pc switch [target]`, `/pc toggle`, `/pc help`, `/pc clear`. */
@Command(name = "pc", aliases = ["pchat", "partychat"])
class NexusPartyChatCommand(
    private val handler: PartyChatHandler,
) {
    @Subcommand("")
    @Permission(PARTY_CHAT_PERMISSION)
    @PlayerOnly
    fun execute(
        @Context player: Player,
        @Arg(name = "message", required = false) @Suggests(NexusPartyChatSuggestions.KEY) @Greedy message: String? = null,
    ) = handler.defaultRoute(player, message)

    @Subcommand("switch")
    @Permission(PARTY_CHAT_PERMISSION)
    @PlayerOnly
    fun switch(
        @Context player: Player,
        @Arg(name = "target", required = false) @Suggests(NexusPartyChatSuggestions.KEY) target: String? = null,
    ) = handler.switchRoute(player, target)

    @Subcommand("toggle")
    @Permission(PARTY_CHAT_PERMISSION)
    @PlayerOnly
    fun toggle(@Context player: Player) = handler.toggle(player)

    @Subcommand("help")
    @Permission(PARTY_CHAT_PERMISSION)
    @PlayerOnly
    fun help(@Context player: Player) = handler.help(player)

    @Subcommand("clear")
    @Permission(PARTY_CHAT_PERMISSION)
    @PlayerOnly
    fun clear(@Context player: Player) = handler.clear(player)
}

/**
 * Nexus equivalent of the legacy ACF `@parties` async completion. Central registration passes
 * `mapOf(NexusPartyChatSuggestions.KEY to NexusPartyChatSuggestions.provider { handler })` to
 * `PaperCommandRegistry`. The provider uses the same sender and permission as execution.
 */
object NexusPartyChatSuggestions {
    const val KEY = "parties"

    fun provider(handler: () -> PartyChatHandler): SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { context, builder ->
            val source = context.source
            val player = source.sender as? Player ?: return@SuggestionProvider builder.buildFuture()
            if (!player.hasPermission(PARTY_CHAT_PERMISSION)) return@SuggestionProvider builder.buildFuture()
            val typed = builder.remainingLowerCase
            handler().partySuggestions(player.uniqueId)
                .filter { it.lowercase(Locale.ROOT).startsWith(typed) }
                .forEach(builder::suggest)
            builder.buildFuture()
        }
}
