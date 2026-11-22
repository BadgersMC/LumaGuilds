package net.lumalyte.lg.interaction.commands.nexus

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolver
import net.badgersmc.nexus.paper.commands.arguments.PaperArgumentResolvers
import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.Flag
import org.bukkit.entity.Player
import java.util.Locale

/** A single legacy-style token, including punctuation and the explicit vanished-player suffix. */
data class ClaimArgumentToken(val value: String)

/** Register once before constructing claim trees; never replace the global String or Player resolver. */
internal fun registerClaimCommandArguments() {
    PaperArgumentResolvers.register(object : PaperArgumentResolver<ClaimArgumentToken> {
        override val type = ClaimArgumentToken::class

        override fun argumentType(): ArgumentType<ClaimArgumentToken> =
            object : CustomArgumentType<ClaimArgumentToken, String> {
                override fun getNativeType(): ArgumentType<String> = StringArgumentType.string()

                override fun parse(reader: StringReader): ClaimArgumentToken {
                    if (reader.canRead() && reader.peek() == '"') return ClaimArgumentToken(reader.readString())
                    val start = reader.cursor
                    while (reader.canRead() && !reader.peek().isWhitespace()) reader.skip()
                    if (reader.cursor == start) {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
                    }
                    return ClaimArgumentToken(reader.string.substring(start, reader.cursor))
                }
            }

        override fun extract(context: CommandContext<*>, name: String): ClaimArgumentToken =
            context.getArgument(name, ClaimArgumentToken::class.java)
    })
}

private val enumSeparators = Regex("[^a-z0-9]")

/** Keeps the former enum resolver's case/punctuation normalization without accepting prefixes. */
internal fun normalizedClaimEnumName(value: String): String =
    value.lowercase(Locale.ENGLISH).replace(enumSeparators, "")

/**
 * Delegates exact/partial matching to the same server API as before. Multiple visible matches never
 * choose an arbitrary player; hidden matches require the existing privilege and explicit confirmation.
 * No selector is interpreted by this name-only argument.
 */
internal fun resolveClaimPlayer(actor: Player, token: ClaimArgumentToken, lang: LangService): Player? {
    val raw = token.value
    if (raw.isBlank()) return noClaimPlayer(actor, lang)
    val matches = actor.server.matchPlayer(raw.replace(":confirm", "")).filter { it.isOnline }
    val (visible, hidden) = matches.partition { actor.canSee(it) }
    if (hidden.isNotEmpty() && actor.hasPermission("acf.seevanish")) {
        if (raw.endsWith(":confirm")) return matches.singleOrNull() ?: noClaimPlayer(actor, lang)
        if (hidden.size > 1) return noClaimPlayer(actor, lang)
        if (visible.isEmpty()) {
            actor.sendMessage(lang.msg("command.claim.hidden_player_confirm", "player" to hidden.single().name))
            return null
        }
    }
    return visible.singleOrNull() ?: noClaimPlayer(actor, lang)
}

private fun noClaimPlayer(actor: Player, lang: LangService): Player? {
    actor.sendMessage(lang.msg("general.error"))
    return null
}

/** Passed to the command registry by the composition root; creation performs no server lookups. */
internal fun claimCommandSuggestionProviders(): Map<String, SuggestionProvider<CommandSourceStack>> = mapOf(
    "claim.flags.add" to claimSuggestions("addflag") { Flag.entries.map { it.name } },
    "claim.flags.remove" to claimSuggestions("removeflag") { Flag.entries.map { it.name } },
    "claim.permissions.trust" to claimSuggestions("trust") { ClaimPermission.entries.map { it.name } },
    "claim.permissions.untrust" to claimSuggestions("untrust") { ClaimPermission.entries.map { it.name } },
    "claim.permissions.trustall" to claimSuggestions("trustall") { ClaimPermission.entries.map { it.name } },
    "claim.permissions.untrustall" to claimSuggestions("untrustall") { ClaimPermission.entries.map { it.name } },
    "claim.players.trust" to claimSuggestions("trust", ::visibleClaimPlayerNames),
    "claim.players.untrust" to claimSuggestions("untrust", ::visibleClaimPlayerNames),
)

private fun visibleClaimPlayerNames(player: Player): List<String> =
    player.server.onlinePlayers.filter { player.canSee(it) }.map { it.name }

private fun claimSuggestions(
    operation: String,
    values: (Player) -> List<String>,
): SuggestionProvider<CommandSourceStack> = SuggestionProvider { context, builder ->
    val player = context.source.sender as? Player
    if (player != null && player.hasPermission("lumaguilds.command.claim.$operation")) {
        values(player).filter { it.startsWith(builder.remaining, ignoreCase = true) }.forEach(builder::suggest)
    }
    builder.buildFuture()
}
