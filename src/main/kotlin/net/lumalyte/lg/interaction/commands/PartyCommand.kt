package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyActions
import net.lumalyte.lg.application.services.PartyResult
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.values.ConfirmWindow
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.PartyInviteGuildMenu
import net.lumalyte.lg.utils.GuildResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * REQ-210 to REQ-215: /party. Parties are only for talking between guilds (REQ-207);
 * party chat itself stays on /pc, and /party chat hands over to it.
 */
@CommandAlias("party")
@CommandPermission("lumaguilds.party")
class PartyCommand(private val partyChat: PartyChatCommand) : BaseCommand(), KoinComponent {

    private val lang: LangService by inject()
    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: MenuFactory by inject()
    private val preferences: PlayerPartyPreferenceRepository by inject()

    private val actions by lazy {
        PartyActions(partyService, guildService) {
            Duration.ofHours(configService.loadConfig().party.defaultPartyDurationHours.toLong())
        }
    }
    private val confirmations = ConfirmWindow(CONFIRM_MS)

    @Default
    fun onMenu(player: Player) {
        val guild = ownGuild(player) ?: return
        val navigator = MenuNavigator(player)
        navigator.openMenu(menuFactory.createGuildPartyManagementMenu(navigator, player, guild))
    }

    @Subcommand("help")
    fun onHelp(player: Player) {
        player.sendMessage(lang.msg("party_command.help.header"))
        listOf(
            "/party" to lang.msg("party_command.help.menu"),
            "/party invite " to lang.msg("party_command.help.invite"),
            "/party accept " to lang.msg("party_command.help.accept"),
            "/party deny " to lang.msg("party_command.help.deny"),
            "/party cancel " to lang.msg("party_command.help.cancel"),
            "/party info" to lang.msg("party_command.help.info"),
            "/party list" to lang.msg("party_command.help.list"),
            "/party chat " to lang.msg("party_command.help.chat"),
            "/party rename " to lang.msg("party_command.help.rename"),
            "/party leave" to lang.msg("party_command.help.leave"),
            "/party disband" to lang.msg("party_command.help.disband"),
        ).forEach { (command, line) ->
            player.sendMessage(
                line.clickEvent(ClickEvent.suggestCommand(command))
                    .hoverEvent(HoverEvent.showText(Component.text(command))),
            )
        }
    }

    @Subcommand("invite")
    @CommandCompletion("@guilds")
    fun onInvite(player: Player, @Optional target: String?) {
        val guild = ownGuild(player) ?: return
        if (target.isNullOrBlank()) {
            openInviteMenu(player, guild)
            return
        }
        when (val result = actions.invite(player.uniqueId, target)) {
            is PartyResult.Done -> {
                val invited = result.guild ?: return
                if (result.created) player.sendMessage(lang.msg("party_command.invite.created"))
                player.sendMessage(lang.msg("party_command.invite.sent", "guild" to name(invited)))
                notifyInvited(guild, invited)
            }
            PartyResult.AlreadyInParty -> player.sendMessage(lang.msg("party_command.invite.already_in", "guild" to target))
            PartyResult.AlreadyInvited -> player.sendMessage(lang.msg("party_command.invite.already_invited", "guild" to target))
            PartyResult.OwnGuild -> player.sendMessage(lang.msg("party_command.invite.own_guild"))
            else -> common(player, result)
        }
    }

    @Subcommand("accept")
    @CommandCompletion("@guilds")
    fun onAccept(player: Player, @Optional from: String?) {
        if (from.isNullOrBlank()) {
            listRequests(player, incoming = true)
            return
        }
        when (val result = actions.accept(player.uniqueId, from)) {
            is PartyResult.Done -> player.sendMessage(lang.msg("party_command.accept.done", "guild" to from))
            else -> common(player, result)
        }
    }

    @Subcommand("deny")
    @CommandCompletion("@guilds")
    fun onDeny(player: Player, @Optional from: String?) {
        if (from.isNullOrBlank()) {
            listRequests(player, incoming = true)
            return
        }
        when (val result = actions.deny(player.uniqueId, from)) {
            is PartyResult.Done -> player.sendMessage(lang.msg("party_command.deny.done", "guild" to from))
            else -> common(player, result)
        }
    }

    @Subcommand("cancel")
    @CommandCompletion("@guilds")
    fun onCancel(player: Player, @Optional to: String?) {
        if (to.isNullOrBlank()) {
            listRequests(player, incoming = false)
            return
        }
        when (val result = actions.cancel(player.uniqueId, to)) {
            is PartyResult.Done -> player.sendMessage(lang.msg("party_command.cancel.done", "guild" to to))
            else -> common(player, result)
        }
    }

    @Subcommand("info")
    fun onInfo(player: Player) {
        val guild = ownGuild(player) ?: return
        val party = actions.partyOf(guild.id) ?: run {
            player.sendMessage(lang.msg("party_command.no_party"))
            return
        }
        val format = DateTimeFormatter.ofPattern(lang.raw("party_command.info.date_format")).withZone(ZoneId.systemDefault())
        player.sendMessage(lang.msg("party_command.info.header", "party" to partyName(party)))
        player.sendMessage(lang.msg("party_command.info.leader", "player" to (Bukkit.getOfflinePlayer(party.leaderId).name ?: "?")))
        player.sendMessage(lang.msg("party_command.info.guilds", "guilds" to guildNames(party)))
        player.sendMessage(lang.msg("party_command.info.online", "count" to partyService.getOnlinePartyMembers(party.id).size))
        party.expiresAt?.let { player.sendMessage(lang.msg("party_command.info.ends", "date" to format.format(it))) }
        if (party.guildIds.size < 2) player.sendMessage(lang.msg("party_command.info.waiting"))
    }

    @Subcommand("list")
    fun onList(player: Player) {
        val parties = actions.list()
        if (parties.isEmpty()) {
            player.sendMessage(lang.msg("party_command.list.empty"))
            return
        }
        player.sendMessage(lang.msg("party_command.list.header", "count" to parties.size))
        parties.forEach {
            player.sendMessage(lang.msg("party_command.list.entry", "party" to partyName(it), "guilds" to guildNames(it)))
        }
    }

    @Subcommand("leave")
    fun onLeave(player: Player) {
        val guild = ownGuild(player) ?: return
        val party = actions.partyOf(guild.id) ?: run {
            player.sendMessage(lang.msg("party_command.no_party"))
            return
        }
        if (!partyService.canManageParties(player.uniqueId, guild.id)) {
            common(player, PartyResult.NoPermission)
            return
        }
        if (!confirmations.confirm(player.uniqueId, "leave")) {
            player.sendMessage(confirmPrompt(lang.msg("party_command.leave.confirm", "party" to partyName(party)), "/party leave"))
            return
        }
        when (val result = actions.leave(player.uniqueId)) {
            is PartyResult.Done -> player.sendMessage(lang.msg("party_command.leave.done", "party" to partyName(party)))
            else -> common(player, result)
        }
    }

    @Subcommand("disband")
    fun onDisband(player: Player) {
        val guild = ownGuild(player) ?: return
        val party = actions.partyOf(guild.id) ?: run {
            player.sendMessage(lang.msg("party_command.no_party"))
            return
        }
        if (!confirmations.confirm(player.uniqueId, "disband")) {
            player.sendMessage(confirmPrompt(lang.msg("party_command.disband.confirm", "party" to partyName(party)), "/party disband"))
            return
        }
        when (val result = actions.disband(player.uniqueId)) {
            is PartyResult.Done -> player.sendMessage(lang.msg("party_command.disband.done", "party" to partyName(party)))
            PartyResult.NoPermission -> player.sendMessage(lang.msg("party_command.disband.not_leader"))
            else -> common(player, result)
        }
    }

    @Subcommand("chat")
    fun onChat(player: Player, @Optional message: String?) {
        if (!player.hasPermission("lumaguilds.partychat")) {
            player.sendMessage(lang.msg("party_command.no_permission"))
            return
        }
        val guild = ownGuild(player) ?: return
        val party = actions.partyOf(guild.id) ?: run {
            player.sendMessage(lang.msg("party_command.no_party"))
            return
        }
        if (!message.isNullOrBlank()) {
            partyChat.sendPartyMessage(player, message, party)
            return
        }
        if (preferences.getByPlayerId(player.uniqueId)?.partyId == party.id) {
            partyChat.partyChatOff(player)
        } else {
            partyChat.switchToParty(player, party)
        }
    }

    @Subcommand("rename")
    fun onRename(player: Player, name: String) {
        when (val result = actions.rename(player.uniqueId, name)) {
            is PartyResult.Done -> player.sendMessage(lang.msg("party_command.rename.done", "party" to name))
            PartyResult.BadName -> player.sendMessage(lang.msg("party_command.rename.bad_name"))
            else -> common(player, result)
        }
    }

    private fun openInviteMenu(player: Player, guild: Guild) {
        if (!partyService.canManageParties(player.uniqueId, guild.id)) {
            common(player, PartyResult.NoPermission)
            return
        }
        val navigator = MenuNavigator(player)
        val party = actions.partyOf(guild.id)
        val menu = when {
            party == null -> menuFactory.createPartyCreationMenu(navigator, player, guild)
            menuFactory.shouldUseBedrockMenus(player) -> menuFactory.createGuildPartyManagementMenu(navigator, player, guild)
            else -> PartyInviteGuildMenu(navigator, player, guild, party.id)
        }
        navigator.openMenu(menu)
    }

    private fun listRequests(player: Player, incoming: Boolean) {
        val guild = ownGuild(player) ?: return
        val requests = if (incoming) partyService.getPendingRequestsForGuild(guild.id) else partyService.getPendingRequestsFromGuild(guild.id)
        if (requests.isEmpty()) {
            player.sendMessage(if (incoming) lang.msg("party_command.requests.none_incoming") else lang.msg("party_command.requests.none_outgoing"))
            return
        }
        player.sendMessage(if (incoming) lang.msg("party_command.requests.incoming_header") else lang.msg("party_command.requests.outgoing_header"))
        requests.forEach { request ->
            val other = guildService.getGuild(if (incoming) request.fromGuildId else request.toGuildId) ?: return@forEach
            val otherName = name(other)
            val line = lang.msg("party_command.requests.entry", "guild" to otherName)
            if (incoming) {
                player.sendMessage(
                    line.append(Component.space())
                        .append(button(lang.msg("party_command.requests.accept"), "/party accept $otherName"))
                        .append(Component.space())
                        .append(button(lang.msg("party_command.requests.deny"), "/party deny $otherName")),
                )
            } else {
                player.sendMessage(line.append(Component.space()).append(button(lang.msg("party_command.requests.cancel"), "/party cancel $otherName")))
            }
        }
    }

    private fun notifyInvited(from: Guild, invited: Guild) {
        val fromName = name(from)
        val message = lang.msg("party_command.invite.received", "guild" to fromName)
            .append(Component.space())
            .append(button(lang.msg("party_command.requests.accept"), "/party accept $fromName"))
            .append(Component.space())
            .append(button(lang.msg("party_command.requests.deny"), "/party deny $fromName"))
        memberService.getGuildMembers(invited.id).forEach { member ->
            Bukkit.getPlayer(member.playerId)?.sendMessage(message)
        }
    }

    private fun button(label: Component, command: String): Component =
        label.clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(Component.text(command)))

    private fun confirmPrompt(prompt: Component, command: String): Component =
        prompt.clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(command)))

    private fun common(player: Player, result: PartyResult) {
        player.sendMessage(
            when (result) {
                PartyResult.NoGuild -> lang.msg("party_command.no_guild")
                PartyResult.NoPermission -> lang.msg("party_command.no_permission")
                PartyResult.NoParty -> lang.msg("party_command.no_party")
                is PartyResult.UnknownGuild -> lang.msg("party_command.unknown_guild", "guild" to result.name)
                is PartyResult.NoRequest -> lang.msg("party_command.requests.no_request", "guild" to name(result.guild))
                else -> lang.msg("party_command.failed")
            },
        )
    }

    private fun ownGuild(player: Player): Guild? {
        if (!configService.loadConfig().partiesEnabled) {
            player.sendMessage(lang.msg("party_command.disabled"))
            return null
        }
        return actions.guildOf(player.uniqueId) ?: run {
            player.sendMessage(lang.msg("party_command.no_guild"))
            null
        }
    }

    private fun name(guild: Guild) = GuildResolver.displayName(guild)

    private fun partyName(party: Party) = party.name ?: lang.raw("party_command.unnamed")

    private fun guildNames(party: Party) =
        party.guildIds.mapNotNull { guildService.getGuild(it)?.let(::name) }.sorted().joinToString(", ")

    private companion object {
        const val CONFIRM_MS = 30_000L
    }
}
