package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.badgersmc.nexus.paper.commands.annotations.Suggests
import net.lumalyte.lg.interaction.commands.GuildCommandHandlers
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.ALLY_GUILDS
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.ALL_PLAYERS
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.GUILDS
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.GUILDS_OR_PLAYERS
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.GUILD_ACCESSIBLE_HOMES
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.GUILD_HOMES
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.GUILD_MEMBERS
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.MODES
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.ON_OFF
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.ON_OFF_TOGGLE
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.PENDING_INVITES
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.PLAYERS
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildSuggestionKeys.UNLOCKED_EMOJIS
import org.bukkit.entity.Player

/**
 * Inactive Nexus adapter for the complete `/guild` (`/g`) family — metadata only.
 *
 * Every route delegates to the single shared [GuildCommandHandlers] (also behind the ACF
 * [net.lumalyte.lg.interaction.commands.GuildCommand]), so permissions are route-scoped exactly as
 * in ACF, authorization/persistence stay in the handlers, and the home/ally-home cooldown is shared.
 *
 * Framework differences recorded for REQ-NEXUS-CUTOVER-1:
 * - Nexus `@Subcommand` has no `a|b` alias syntax: each ACF alias is its own delegating route.
 * - ACF lets the final `String` parameter consume the rest of the line; here that is `@Greedy`.
 *   Non-final strings (`create <name>`, `home <homeName>`, `allyhome <guildName>`, `truce <guildName>`)
 *   stay single words as in ACF.
 * - Arguments stay `String`, never Paper's online `Player` selector, so offline names reach handlers.
 *
 * INACTIVE: not registered until the all-at-once Nexus command cutover; ACF stays authoritative.
 */
@Command(name = "guild", aliases = ["g"])
internal class NexusGuildCommands(private val handlers: GuildCommandHandlers) {

    @Subcommand("")
    @Permission("lumaguilds.guild.menu")
    @PlayerOnly
    fun root(@Context player: Player) = handlers.onDefault(player)

    @Subcommand("create")
    @Permission("lumaguilds.guild.create")
    @PlayerOnly
    fun create(
        @Context player: Player,
        @Arg("name") name: String,
        @Arg("banner", required = false) @Greedy banner: String? = null,
    ) = handlers.onCreate(player, name, banner)

    @Subcommand("rename")
    @Permission("lumaguilds.guild.rename")
    @PlayerOnly
    fun rename(@Context player: Player, @Arg("newName") @Greedy newName: String) = handlers.onRename(player, newName)

    @Subcommand("sethome")
    @Permission("lumaguilds.guild.sethome")
    @PlayerOnly
    fun setHome(
        @Context player: Player,
        @Arg("homeName", required = false) homeName: String? = null,
        @Arg("confirm", required = false) @Greedy confirm: String? = null,
    ) = handlers.onSetHome(player, homeName, confirm)

    @Subcommand("home activate")
    @Permission("lumaguilds.guild.home")
    @PlayerOnly
    fun homeActivate(
        @Context player: Player,
        @Arg("homeName") @Greedy @Suggests(GUILD_HOMES) homeName: String,
    ) = handlers.onHomeActivate(player, homeName)

    @Subcommand("home")
    @Permission("lumaguilds.guild.home")
    @PlayerOnly
    fun home(
        @Context player: Player,
        @Arg("homeName", required = false) @Suggests(GUILD_ACCESSIBLE_HOMES) homeName: String? = null,
        @Arg("confirm", required = false) @Greedy confirm: String? = null,
    ) = handlers.onHome(player, homeName, confirm)

    @Subcommand("homes")
    @Permission("lumaguilds.guild.home")
    @PlayerOnly
    fun homes(@Context player: Player) = handlers.onHomes(player)

    @Subcommand("home unlock")
    @Permission("lumaguilds.guild.sethome")
    @PlayerOnly
    fun homeUnlock(@Context player: Player, @Arg("homeName") @Greedy homeName: String) =
        handlers.onHomeUnlock(player, homeName)

    @Subcommand("removehome")
    @Permission("lumaguilds.guild.sethome")
    @PlayerOnly
    fun removeHome(@Context player: Player, @Arg("homeName") @Greedy @Suggests(GUILD_HOMES) homeName: String) =
        handlers.onRemoveHome(player, homeName)

    @Subcommand("setallyhome")
    @Permission("lumaguilds.guild.sethome")
    @PlayerOnly
    fun setAllyHome(@Context player: Player) = handlers.onSetAllyHome(player)

    @Subcommand("removeallyhome")
    @Permission("lumaguilds.guild.sethome")
    @PlayerOnly
    fun removeAllyHome(@Context player: Player) = handlers.onRemoveAllyHome(player)

    @Subcommand("allyhome")
    @Permission("lumaguilds.guild.allyhome")
    @PlayerOnly
    fun allyHome(
        @Context player: Player,
        @Arg("guildName") @Suggests(ALLY_GUILDS) guildName: String,
        @Arg("confirm", required = false) @Greedy confirm: String? = null,
    ) = handlers.onAllyHome(player, guildName, confirm)

    @Subcommand("ranks")
    @Permission("lumaguilds.guild.ranks")
    @PlayerOnly
    fun ranks(@Context player: Player) = handlers.onRanks(player)

    @Subcommand("ranks chat")
    @Permission("lumaguilds.guild.ranks")
    @PlayerOnly
    fun ranksChat(@Context player: Player, @Arg("state") @Greedy @Suggests(ON_OFF) state: String) =
        handlers.onRanksChat(player, state)

    @Subcommand("emoji")
    @Permission("lumaguilds.guild.emoji")
    @PlayerOnly
    fun emoji(
        @Context player: Player,
        @Arg("emoji", required = false) @Greedy @Suggests(UNLOCKED_EMOJIS) emoji: String? = null,
    ) = handlers.onEmoji(player, emoji)

    @Subcommand("mode")
    @Permission("lumaguilds.guild.mode")
    @PlayerOnly
    fun mode(@Context player: Player, @Arg("mode") @Greedy @Suggests(MODES) mode: String) = handlers.onMode(player, mode)

    @Subcommand("history")
    @Permission("lumaguilds.guild.history")
    @PlayerOnly
    fun history(@Context player: Player, @Arg("targetPlayerName") @Greedy @Suggests(PLAYERS) targetPlayerName: String) =
        handlers.onHistory(player, targetPlayerName)

    @Subcommand("chatsettings")
    @Permission("lumaguilds.guild.chat")
    @PlayerOnly
    fun chatSettings(@Context player: Player) = handlers.onChatSettings(player)

    @Subcommand("chat")
    @Permission("lumaguilds.guild.chat")
    @PlayerOnly
    fun chat(@Context player: Player) = handlers.onGuildChat(player)

    // ACF declares no permission on notifications/info/strikes; Nexus keeps them unrestricted.
    @Subcommand("notifications")
    @PlayerOnly
    fun notifications(
        @Context player: Player,
        @Arg("state", required = false) @Greedy @Suggests(ON_OFF_TOGGLE) state: String? = null,
    ) = handlers.onNotifications(player, state)

    @Subcommand("notify")
    @PlayerOnly
    fun notifyAlias(
        @Context player: Player,
        @Arg("state", required = false) @Greedy @Suggests(ON_OFF_TOGGLE) state: String? = null,
    ) = handlers.onNotifications(player, state)

    @Subcommand("allychat")
    @Permission("lumaguilds.guild.chat")
    @PlayerOnly
    fun allyChat(@Context player: Player) = handlers.onAllyChat(player)

    @Subcommand("modchat")
    @Permission("lumaguilds.guild.chat")
    @PlayerOnly
    fun modChat(@Context player: Player) = handlers.onModChat(player)

    @Subcommand("info")
    @PlayerOnly
    fun info(
        @Context player: Player,
        @Arg("targetGuild", required = false) @Greedy @Suggests(GUILDS_OR_PLAYERS) targetGuild: String? = null,
    ) = handlers.onInfo(player, targetGuild)

    @Subcommand("strikes")
    @PlayerOnly
    fun strikes(
        @Context player: Player,
        @Arg("target", required = false) @Greedy @Suggests(GUILDS) target: String? = null,
    ) = handlers.onStrikes(player, target)

    @Subcommand("strikes punish")
    @Permission("lumaguilds.admin.strikes")
    @PlayerOnly
    fun strikesPunish(@Context player: Player, @Arg("target") @Greedy @Suggests(GUILDS) target: String) =
        handlers.onStrikesPunish(player, target)

    @Subcommand("disband")
    @Permission("lumaguilds.guild.disband")
    @PlayerOnly
    fun disband(@Context player: Player) = handlers.onDisband(player)

    @Subcommand("bank")
    @Permission("lumaguilds.guild.menu")
    @PlayerOnly
    fun bank(@Context player: Player) = handlers.onBank(player)

    @Subcommand("stall")
    @Permission("lumaguilds.guild.menu")
    @PlayerOnly
    fun stall(@Context player: Player) = handlers.onStall(player)

    @Subcommand("stalls")
    @Permission("lumaguilds.guild.menu")
    @PlayerOnly
    fun stalls(@Context player: Player) = handlers.onStall(player)

    @Subcommand("quests")
    @Permission("lumaguilds.guild.quests")
    @PlayerOnly
    fun quests(@Context player: Player) = handlers.onQuests(player)

    @Subcommand("quest")
    @Permission("lumaguilds.guild.quests")
    @PlayerOnly
    fun quest(@Context player: Player) = handlers.onQuests(player)

    @Subcommand("quests menu")
    @Permission("lumaguilds.guild.quests")
    @PlayerOnly
    fun questsMenu(@Context player: Player) = handlers.onQuestMenu(player)

    @Subcommand("quest menu")
    @Permission("lumaguilds.guild.quests")
    @PlayerOnly
    fun questMenu(@Context player: Player) = handlers.onQuestMenu(player)

    @Subcommand("leaderboard")
    @Permission("lumaguilds.guild.leaderboard")
    @PlayerOnly
    fun leaderboard(@Context player: Player) = handlers.onLeaderboard(player)

    @Subcommand("menu")
    @Permission("lumaguilds.guild.menu")
    @PlayerOnly
    fun menu(@Context player: Player) = handlers.onMenu(player)

    @Subcommand("invite")
    @Permission("lumaguilds.guild.invite")
    @PlayerOnly
    fun invite(@Context player: Player, @Arg("targetPlayerName") @Greedy @Suggests(ALL_PLAYERS) targetPlayerName: String) =
        handlers.onInvite(player, targetPlayerName)

    @Subcommand("join")
    @Permission("lumaguilds.guild.join")
    @PlayerOnly
    fun join(@Context player: Player, @Arg("guildName") @Greedy @Suggests(GUILDS) guildName: String) =
        handlers.onJoin(player, guildName)

    @Subcommand("accept")
    @Permission("lumaguilds.guild.join")
    @PlayerOnly
    fun accept(@Context player: Player, @Arg("guildName") @Greedy @Suggests(GUILDS) guildName: String) =
        handlers.onJoin(player, guildName)

    @Subcommand("list")
    @Permission("lumaguilds.guild.list")
    @PlayerOnly
    fun list(@Context player: Player) = handlers.onList(player)

    @Subcommand("lfg")
    @Permission("lumaguilds.guild.lfg")
    @PlayerOnly
    fun lfg(@Context player: Player) = handlers.onLfg(player)

    @Subcommand("browse")
    @Permission("lumaguilds.guild.lfg")
    @PlayerOnly
    fun browse(@Context player: Player) = handlers.onLfg(player)

    @Subcommand("recruitment")
    @Permission("lumaguilds.guild.lfg")
    @PlayerOnly
    fun recruitment(@Context player: Player) = handlers.onLfg(player)

    @Subcommand("decline")
    @Permission("lumaguilds.guild.decline")
    @PlayerOnly
    fun decline(@Context player: Player, @Arg("guildName") @Greedy @Suggests(PENDING_INVITES) guildName: String) =
        handlers.onDecline(player, guildName)

    @Subcommand("block")
    @Permission("lumaguilds.guild.block")
    @PlayerOnly
    fun block(@Context player: Player, @Arg("targetPlayerName") @Greedy @Suggests(ALL_PLAYERS) targetPlayerName: String) =
        handlers.onBlock(player, targetPlayerName)

    @Subcommand("unblock")
    @Permission("lumaguilds.guild.block")
    @PlayerOnly
    fun unblock(@Context player: Player, @Arg("targetPlayerName") @Greedy @Suggests(ALL_PLAYERS) targetPlayerName: String) =
        handlers.onUnblock(player, targetPlayerName)

    @Subcommand("blocked")
    @Permission("lumaguilds.guild.block")
    @PlayerOnly
    fun blocked(@Context player: Player) = handlers.onBlocked(player)

    @Subcommand("invites")
    @Permission("lumaguilds.guild.invites")
    @PlayerOnly
    fun invites(@Context player: Player) = handlers.onInvites(player)

    @Subcommand("kick")
    @Permission("lumaguilds.guild.kick")
    @PlayerOnly
    fun kick(@Context player: Player, @Arg("targetPlayerName") @Greedy @Suggests(GUILD_MEMBERS) targetPlayerName: String) =
        handlers.onKick(player, targetPlayerName)

    @Subcommand("leave")
    @Permission("lumaguilds.guild.leave")
    @PlayerOnly
    fun leave(@Context player: Player) = handlers.onLeave(player)

    @Subcommand("transfer")
    @Permission("lumaguilds.guild.transfer")
    @PlayerOnly
    fun transfer(@Context player: Player, @Arg("targetPlayerName") @Greedy @Suggests(GUILD_MEMBERS) targetPlayerName: String) =
        handlers.onTransfer(player, targetPlayerName)

    @Subcommand("tag")
    @Permission("lumaguilds.guild.tag")
    @PlayerOnly
    fun tag(@Context player: Player, @Arg("tag", required = false) @Greedy tag: String? = null) =
        handlers.onTag(player, tag)

    @Subcommand("bannerman")
    @Permission("lumaguilds.guild.bannerman")
    @PlayerOnly
    fun bannerman(@Context player: Player) = handlers.onBannerman(player)

    @Subcommand("description")
    @Permission("lumaguilds.guild.description")
    @PlayerOnly
    fun description(@Context player: Player, @Arg("description", required = false) @Greedy description: String? = null) =
        handlers.onDescription(player, description)

    @Subcommand("desc")
    @Permission("lumaguilds.guild.description")
    @PlayerOnly
    fun desc(@Context player: Player, @Arg("description", required = false) @Greedy description: String? = null) =
        handlers.onDescription(player, description)

    @Subcommand("warbanner")
    @Permission("lumaguilds.guild.warbanner")
    @PlayerOnly
    fun warBanner(@Context player: Player) = handlers.onWarBanner(player)

    @Subcommand("warbanner tp")
    @Permission("lumaguilds.guild.warbanner")
    @PlayerOnly
    fun warBannerTeleport(@Context player: Player) = handlers.onWarBannerTeleport(player)

    @Subcommand("war")
    @Permission("lumaguilds.guild.war")
    @PlayerOnly
    fun war(@Context player: Player) = handlers.onWar(player)

    @Subcommand("getvault")
    @Permission("lumaguilds.guild.getvault")
    @PlayerOnly
    fun getVault(@Context player: Player) = handlers.onVaultGet(player)

    @Subcommand("vault")
    @Permission("lumaguilds.guild.vault")
    @PlayerOnly
    fun vault(@Context player: Player) = handlers.onVault(player)

    @Subcommand("start")
    @Permission("lumaguilds.guild.help")
    @PlayerOnly
    fun start(@Context player: Player, @Arg("guildId", required = false) @Greedy guildId: String? = null) =
        handlers.onGettingStarted(player, guildId)

    @Subcommand("gettingstarted")
    @Permission("lumaguilds.guild.help")
    @PlayerOnly
    fun gettingStarted(@Context player: Player, @Arg("guildId", required = false) @Greedy guildId: String? = null) =
        handlers.onGettingStarted(player, guildId)

    @Subcommand("start dismiss")
    @Permission("lumaguilds.guild.help")
    @PlayerOnly
    fun startDismiss(@Context player: Player) = handlers.onDismissGettingStarted(player)

    /** The only `/guild help` route (the former standalone GuildHelpCommands duplicate was folded in). */
    @Subcommand("help")
    @Permission("lumaguilds.guild.help")
    @PlayerOnly
    fun help(@Context player: Player, @Arg("topic", required = false) @Greedy topic: String? = null) =
        handlers.onHelp(player, topic)

    @Subcommand("ally")
    @Permission("lumaguilds.guild.ally")
    @PlayerOnly
    fun ally(@Context player: Player, @Arg("guildName") @Greedy @Suggests(GUILDS) guildName: String) =
        handlers.onAlly(player, guildName)

    @Subcommand("enemy")
    @Permission("lumaguilds.guild.enemy")
    @PlayerOnly
    fun enemy(@Context player: Player, @Arg("guildName") @Greedy @Suggests(GUILDS) guildName: String) =
        handlers.onEnemy(player, guildName)

    @Subcommand("truce")
    @Permission("lumaguilds.guild.truce")
    @PlayerOnly
    fun truce(
        @Context player: Player,
        @Arg("guildName") @Suggests(GUILDS) guildName: String,
        @Arg("durationDays", required = false) durationDays: Int? = null,
    ) = handlers.onTruce(player, guildName, durationDays)

    @Subcommand("neutral")
    @Permission("lumaguilds.guild.neutral")
    @PlayerOnly
    fun neutral(@Context player: Player, @Arg("guildName") @Greedy @Suggests(GUILDS) guildName: String) =
        handlers.onNeutral(player, guildName)

    @Subcommand("balance")
    @Permission("lumaguilds.guild.bal")
    @PlayerOnly
    fun balance(@Context player: Player, @Arg("guildName", required = false) @Greedy @Suggests(GUILDS) guildName: String? = null) =
        handlers.onBalance(player, guildName)

    @Subcommand("bal")
    @Permission("lumaguilds.guild.bal")
    @PlayerOnly
    fun bal(@Context player: Player, @Arg("guildName", required = false) @Greedy @Suggests(GUILDS) guildName: String? = null) =
        handlers.onBalance(player, guildName)

    @Subcommand("baltop")
    @Permission("lumaguilds.guild.baltop")
    @PlayerOnly
    fun baltop(@Context player: Player) = handlers.onBaltop(player)
}
