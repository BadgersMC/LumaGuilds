package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/** Explicit lazy collaborators for [GuildCommand] (REQ-NEXUS-DI-1..3); each resolves on first use. */
internal data class GuildCommandDependencies(
    val lang: Lazy<LangService>,
    val guildService: Lazy<GuildService>,
    val lfgService: Lazy<net.lumalyte.lg.application.services.LfgService>,
    val guildRepository: Lazy<net.lumalyte.lg.application.persistence.GuildRepository>,
    val rankService: Lazy<RankService>,
    val chatRankSettings: Lazy<net.lumalyte.lg.application.services.GuildChatRankSettingsService>,
    val memberService: Lazy<MemberService>,
    val inviteBlocks: Lazy<net.lumalyte.lg.application.services.InviteBlockService>,
    val vaultService: Lazy<net.lumalyte.lg.application.services.GuildVaultService>,
    val warService: Lazy<net.lumalyte.lg.application.services.WarService>,
    val configService: Lazy<ConfigService>,
    val menuFactory: Lazy<MenuFactory>,
    val progressionService: Lazy<net.lumalyte.lg.application.services.ProgressionService>,
    val historyRepository: Lazy<MembershipHistoryRepository>,
    val guildChatListener: Lazy<net.lumalyte.lg.interaction.listeners.GuildChatListener>,
    val adminOverrideService: Lazy<net.lumalyte.lg.application.services.AdminOverrideService>,
    val teleportationService: Lazy<net.lumalyte.lg.infrastructure.services.TeleportationService>,
    val bannermanListeners: Lazy<net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners>,
    val strikeService: Lazy<net.lumalyte.lg.application.services.StrikeService>,
    val bankService: Lazy<net.lumalyte.lg.application.services.BankService>,
    val guildCostService: Lazy<net.lumalyte.lg.application.services.GuildCostService>,
    val guildHomeActivationService: Lazy<net.lumalyte.lg.application.services.GuildHomeActivationService>,
    val warBannerService: Lazy<net.lumalyte.lg.infrastructure.services.WarBannerServiceBukkit>,
    val guildLoginNotificationService: Lazy<net.lumalyte.lg.application.services.GuildLoginNotificationService>,
    /** Used by ally homes and the relation subcommands; resolved on first use. */
    val relationService: Lazy<net.lumalyte.lg.application.services.RelationService>,
    /** Used only by `/g emoji`; resolved on first use. */
    val nexoEmojiService: Lazy<net.lumalyte.lg.infrastructure.services.NexoEmojiService>,
    /** Used only by `/g join` open-recruitment admission; resolved on first use. */
    val guildRecruitmentService: Lazy<net.lumalyte.lg.application.services.GuildRecruitmentService>,
    /** Used only by `/g start dismiss`; resolved on first use. */
    val guildOnboardingRepository: Lazy<net.lumalyte.lg.application.persistence.GuildOnboardingRepository>,
)

@CommandAlias("guild|g")
/**
 * Transitional ACF adapter for `/guild`. Metadata only: every route delegates to the
 * shared framework-free [GuildCommandHandlers] (also used by the inactive Nexus adapter).
 */
class GuildCommand internal constructor(
    dependencies: GuildCommandDependencies,
    /** Pass the runtime's single shared instance so ACF and Nexus routes share cooldown state. */
    private val handlers: GuildCommandHandlers = GuildCommandHandlers(dependencies),
) : BaseCommand() {

    internal fun checkHomeSafety(
        player: Player, target: Location, confirmCommand: String,
        guildId: UUID? = null, homeName: String? = null,
    ): Boolean = handlers.checkHomeSafety(player, target, confirmCommand, guildId, homeName)

    @Subcommand("create")
    @CommandPermission("lumaguilds.guild.create")
    fun onCreate(player: Player, name: String, @Optional banner: String?) = handlers.onCreate(player, name, banner)

    @Subcommand("rename")
    @CommandPermission("lumaguilds.guild.rename")
    fun onRename(player: Player, newName: String) = handlers.onRename(player, newName)

    @Subcommand("sethome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onSetHome(player: Player, @Optional homeName: String?, @Optional confirm: String?) = handlers.onSetHome(player, homeName, confirm)

    @Subcommand("home activate")
    @CommandPermission("lumaguilds.guild.home")
    @CommandCompletion("@guildhomes")
    fun onHomeActivate(player: Player, homeName: String) = handlers.onHomeActivate(player, homeName)

    @Subcommand("home")
    @CommandPermission("lumaguilds.guild.home")
    @CommandCompletion("@guildaccessiblehomes")
    fun onHome(player: Player, @Optional homeName: String?, @Optional confirm: String?) = handlers.onHome(player, homeName, confirm)

    @Subcommand("homes")
    @CommandPermission("lumaguilds.guild.home")
    fun onHomes(player: Player) = handlers.onHomes(player)

    @Subcommand("home unlock")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onHomeUnlock(player: Player, homeName: String) = handlers.onHomeUnlock(player, homeName)

    @Subcommand("removehome")
    @CommandPermission("lumaguilds.guild.sethome")
    @CommandCompletion("@guildhomes")
    fun onRemoveHome(player: Player, homeName: String) = handlers.onRemoveHome(player, homeName)

    @Subcommand("setallyhome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onSetAllyHome(player: Player) = handlers.onSetAllyHome(player)

    @Subcommand("removeallyhome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onRemoveAllyHome(player: Player) = handlers.onRemoveAllyHome(player)

    @Subcommand("allyhome")
    @CommandPermission("lumaguilds.guild.allyhome")
    @CommandCompletion("@allyguilds")
    @Syntax("<guildName> [confirm]")
    fun onAllyHome(player: Player, guildName: String, @Optional confirm: String?) = handlers.onAllyHome(player, guildName, confirm)

    @Subcommand("ranks")
    @CommandPermission("lumaguilds.guild.ranks")
    fun onRanks(player: Player) = handlers.onRanks(player)

    @Subcommand("ranks chat")
    @CommandPermission("lumaguilds.guild.ranks")
    @CommandCompletion("on|off")
    fun onRanksChat(player: Player, state: String) = handlers.onRanksChat(player, state)

    @Subcommand("emoji")
    @CommandPermission("lumaguilds.guild.emoji")
    @CommandCompletion("@unlockedemojis")
    fun onEmoji(player: Player, @Optional emoji: String?) = handlers.onEmoji(player, emoji)

    @Subcommand("mode")
    @CommandPermission("lumaguilds.guild.mode")
    @CommandCompletion("peaceful|hostile")
    fun onMode(player: Player, mode: String) = handlers.onMode(player, mode)

    @Subcommand("history")
    @CommandPermission("lumaguilds.guild.history")
    @CommandCompletion("@players")
    fun onHistory(player: Player, targetPlayerName: String) = handlers.onHistory(player, targetPlayerName)

    @Subcommand("chatsettings")
    @CommandPermission("lumaguilds.guild.chat")
    fun onChatSettings(player: Player) = handlers.onChatSettings(player)

    @Subcommand("chat")
    @CommandPermission("lumaguilds.guild.chat")
    fun onGuildChat(player: Player) = handlers.onGuildChat(player)

    @Subcommand("notifications|notify")
    @CommandCompletion("on|off|toggle")
    fun onNotifications(player: Player, @Optional state: String?) = handlers.onNotifications(player, state)

    @Subcommand("allychat")
    @CommandPermission("lumaguilds.guild.chat")
    fun onAllyChat(player: Player) = handlers.onAllyChat(player)

    @Subcommand("modchat")
    @CommandPermission("lumaguilds.guild.chat")
    fun onModChat(player: Player) = handlers.onModChat(player)

    @Subcommand("info")
    @CommandCompletion("@guildsorplayers")
    fun onInfo(player: Player, @Optional targetGuild: String?) = handlers.onInfo(player, targetGuild)

    @Subcommand("strikes")
    @CommandCompletion("@guilds")
    fun onStrikes(player: Player, @Optional target: String?) = handlers.onStrikes(player, target)

    @Subcommand("strikes punish")
    @CommandPermission("lumaguilds.admin.strikes")
    @CommandCompletion("@guilds")
    fun onStrikesPunish(player: Player, target: String) = handlers.onStrikesPunish(player, target)

    @Subcommand("disband")
    @CommandPermission("lumaguilds.guild.disband")
    fun onDisband(player: Player) = handlers.onDisband(player)

    @Subcommand("bank")
    @CommandPermission("lumaguilds.guild.menu")
    fun onBank(player: Player) = handlers.onBank(player)

    @Subcommand("stall|stalls")
    @CommandPermission("lumaguilds.guild.menu")
    fun onStall(player: Player) = handlers.onStall(player)

    @Subcommand("quests|quest")
    @CommandPermission("lumaguilds.guild.quests")
    fun onQuests(player: Player) = handlers.onQuests(player)

    @Subcommand("quests|quest menu")
    @CommandPermission("lumaguilds.guild.quests")
    fun onQuestMenu(player: Player) = handlers.onQuestMenu(player)

    @Subcommand("leaderboard")
    @CommandPermission("lumaguilds.guild.leaderboard")
    fun onLeaderboard(player: Player) = handlers.onLeaderboard(player)

    @Default
    @CommandPermission("lumaguilds.guild.menu")
    fun onDefault(player: Player) = handlers.onDefault(player)

    @Subcommand("menu")
    @CommandPermission("lumaguilds.guild.menu")
    fun onMenu(player: Player) = handlers.onMenu(player)

    @Subcommand("invite")
    @CommandPermission("lumaguilds.guild.invite")
    @CommandCompletion("@allplayers")
    fun onInvite(player: Player, targetPlayerName: String) = handlers.onInvite(player, targetPlayerName)

    @Subcommand("join|accept")
    @CommandPermission("lumaguilds.guild.join")
    @CommandCompletion("@guilds")
    fun onJoin(player: Player, guildName: String) = handlers.onJoin(player, guildName)

    @Subcommand("list")
    @CommandPermission("lumaguilds.guild.list")
    fun onList(player: Player) = handlers.onList(player)

    @Subcommand("lfg|browse|recruitment")
    @CommandPermission("lumaguilds.guild.lfg")
    fun onLfg(player: Player) = handlers.onLfg(player)

    @Subcommand("decline")
    @CommandPermission("lumaguilds.guild.decline")
    @CommandCompletion("@pendinginvites")
    fun onDecline(player: Player, guildName: String) = handlers.onDecline(player, guildName)

    @Subcommand("block")
    @CommandPermission("lumaguilds.guild.block")
    @CommandCompletion("@allplayers")
    fun onBlock(player: Player, targetPlayerName: String) = handlers.onBlock(player, targetPlayerName)

    @Subcommand("unblock")
    @CommandPermission("lumaguilds.guild.block")
    @CommandCompletion("@allplayers")
    fun onUnblock(player: Player, targetPlayerName: String) = handlers.onUnblock(player, targetPlayerName)

    @Subcommand("blocked")
    @CommandPermission("lumaguilds.guild.block")
    fun onBlocked(player: Player) = handlers.onBlocked(player)

    @Subcommand("invites")
    @CommandPermission("lumaguilds.guild.invites")
    fun onInvites(player: Player) = handlers.onInvites(player)

    @Subcommand("kick")
    @CommandPermission("lumaguilds.guild.kick")
    @CommandCompletion("@guildmembers")
    fun onKick(player: Player, targetPlayerName: String) = handlers.onKick(player, targetPlayerName)

    @Subcommand("leave")
    @CommandPermission("lumaguilds.guild.leave")
    fun onLeave(player: Player) = handlers.onLeave(player)

    @Subcommand("transfer")
    @CommandPermission("lumaguilds.guild.transfer")
    @CommandCompletion("@guildmembers")
    fun onTransfer(player: Player, targetPlayerName: String) = handlers.onTransfer(player, targetPlayerName)

    @Subcommand("tag")
    @CommandPermission("lumaguilds.guild.tag")
    fun onTag(player: Player, @Optional tag: String?) = handlers.onTag(player, tag)

    @Subcommand("bannerman")
    @CommandPermission("lumaguilds.guild.bannerman")
    fun onBannerman(player: Player) = handlers.onBannerman(player)

    @Subcommand("description|desc")
    @CommandPermission("lumaguilds.guild.description")
    fun onDescription(player: Player, @Optional description: String?) = handlers.onDescription(player, description)

    @Subcommand("warbanner")
    @CommandPermission("lumaguilds.guild.warbanner")
    fun onWarBanner(player: Player) = handlers.onWarBanner(player)

    @Subcommand("warbanner tp")
    @CommandPermission("lumaguilds.guild.warbanner")
    fun onWarBannerTeleport(player: Player) = handlers.onWarBannerTeleport(player)

    @Subcommand("war")
    @CommandPermission("lumaguilds.guild.war")
    fun onWar(player: Player) = handlers.onWar(player)

    @Subcommand("getvault")
    @CommandPermission("lumaguilds.guild.getvault")
    fun onVaultGet(player: Player) = handlers.onVaultGet(player)

    @Subcommand("vault")
    @CommandPermission("lumaguilds.guild.vault")
    fun onVault(player: Player) = handlers.onVault(player)

    @Subcommand("start|gettingstarted")
    @CommandPermission("lumaguilds.guild.help")
    fun onGettingStarted(player: Player, @Optional guildId: String?) = handlers.onGettingStarted(player, guildId)

    @Subcommand("start dismiss")
    @CommandPermission("lumaguilds.guild.help")
    fun onDismissGettingStarted(player: Player) = handlers.onDismissGettingStarted(player)

    @Subcommand("help")
    @CommandPermission("lumaguilds.guild.help")
    fun onHelp(player: Player, @Optional topic: String?) = handlers.onHelp(player, topic)

    @Subcommand("ally")
    @CommandPermission("lumaguilds.guild.ally")
    @CommandCompletion("@guilds")
    fun onAlly(player: Player, guildName: String) = handlers.onAlly(player, guildName)

    @Subcommand("enemy")
    @CommandPermission("lumaguilds.guild.enemy")
    @CommandCompletion("@guilds")
    fun onEnemy(player: Player, guildName: String) = handlers.onEnemy(player, guildName)

    @Subcommand("truce")
    @CommandPermission("lumaguilds.guild.truce")
    @CommandCompletion("@guilds")
    fun onTruce(player: Player, guildName: String, @Optional durationDays: Int?) = handlers.onTruce(player, guildName, durationDays)

    @Subcommand("neutral")
    @CommandPermission("lumaguilds.guild.neutral")
    @CommandCompletion("@guilds")
    fun onNeutral(player: Player, guildName: String) = handlers.onNeutral(player, guildName)

    @Subcommand("balance|bal")
    @CommandPermission("lumaguilds.guild.bal")
    @CommandCompletion("@guilds")
    fun onBalance(player: Player, @Optional guildName: String?) = handlers.onBalance(player, guildName)

    @Subcommand("baltop")
    @CommandPermission("lumaguilds.guild.baltop")
    fun onBaltop(player: Player) = handlers.onBaltop(player)
}
