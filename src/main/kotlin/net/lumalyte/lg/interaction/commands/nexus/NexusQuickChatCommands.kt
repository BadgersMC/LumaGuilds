package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ChatChannelIds
import net.lumalyte.lg.infrastructure.services.RoseChatQuickChat
import net.lumalyte.lg.interaction.commands.requireGuildMembership
import net.lumalyte.lg.interaction.commands.requireGuildPermission
import org.bukkit.entity.Player

/*
 * ACF-free equivalents of the legacy /gc, /gac and /gmc quick-chat commands
 * (QuickGuildChatCommand, QuickAllyChatCommand, QuickModChatCommand).
 *
 * ACF exposed two @Default handlers per root (no-args usage and vararg message).
 * Nexus gets exactly ONE default route per root with an optional greedy message:
 * null/blank shows the legacy usage text, anything else dispatches via RoseChat.
 */

private const val QUICK_CHAT_PERMISSION = "lumaguilds.guild.chat"

/** `/gc [message]` — one-shot guild chat without changing the player's channel. */
@Command(name = "gc")
class NexusQuickGuildChatCommand(
    private val lang: LangService,
    private val guildService: GuildService,
    private val penaltyService: PenaltyService,
) {
    @Subcommand("")
    @Permission(QUICK_CHAT_PERMISSION)
    @PlayerOnly
    fun execute(
        @Context player: Player,
        @Arg(name = "message", required = false) @Greedy message: String? = null,
    ) {
        if (!player.requireGuildMembership(guildService, lang)) return

        if (message.isNullOrBlank()) {
            player.sendMessage(lang.msg("command.migrated.quick_guild_chat.default.quick_guild_chat"))
            player.sendMessage(lang.msg("command.migrated.quick_guild_chat.default.use_gc_message_to_send_a_single"))
            player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.your_chat_channel_won_t_change_you"))
            player.sendMessage(lang.msg("command.migrated.quick_guild_chat.default.to_toggle_permanent_guild_chat_mode_use"))
            return
        }

        val guildId = guildService.getPlayerGuilds(player.uniqueId).firstOrNull()?.id
        if (guildId != null && penaltyService.isGuildMuted(guildId)) {
            player.sendMessage(lang.msg("command.migrated.quick_guild_chat.message.your_guild_is_muted_guild_chat_is"))
            return
        }

        when (RoseChatQuickChat.send(player, ChatChannelIds.GUILD, message)) {
            RoseChatQuickChat.Result.Dispatched -> {} // routed via RoseChat — no echo needed
            RoseChatQuickChat.Result.EmptyMessage ->
                player.sendMessage(lang.msg("command.migrated.quick_ally_chat.message.message_cannot_be_empty"))
            RoseChatQuickChat.Result.ChannelMissing ->
                player.sendMessage(lang.msg("command.migrated.quick_guild_chat.message.guild_channel_is_not_configured_in_rosechat"))
        }
    }
}

/** `/gac [message]` — one-shot ally chat without changing the player's channel. */
@Command(name = "gac")
class NexusQuickAllyChatCommand(
    private val lang: LangService,
    private val guildService: GuildService,
) {
    @Subcommand("")
    @Permission(QUICK_CHAT_PERMISSION)
    @PlayerOnly
    fun execute(
        @Context player: Player,
        @Arg(name = "message", required = false) @Greedy message: String? = null,
    ) {
        if (!player.requireGuildMembership(guildService, lang)) return

        if (message.isNullOrBlank()) {
            player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.quick_ally_chat"))
            player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.use_gac_message_to_send_a_single"))
            player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.your_chat_channel_won_t_change_you"))
            player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.to_toggle_permanent_ally_chat_mode_use"))
            return
        }

        when (RoseChatQuickChat.send(player, ChatChannelIds.ALLY, message)) {
            RoseChatQuickChat.Result.Dispatched -> {} // routed via RoseChat — no echo needed
            RoseChatQuickChat.Result.EmptyMessage ->
                player.sendMessage(lang.msg("command.migrated.quick_ally_chat.message.message_cannot_be_empty"))
            RoseChatQuickChat.Result.ChannelMissing ->
                player.sendMessage(lang.msg("command.migrated.quick_ally_chat.message.ally_channel_is_not_configured_in_rosechat"))
        }
    }
}

/** `/gmc [message]` — one-shot guild moderator chat; MODERATE_CHAT rank permission required. */
@Command(name = "gmc")
class NexusQuickModChatCommand(
    private val lang: LangService,
    private val guildService: GuildService,
    private val memberService: MemberService,
) {
    @Subcommand("")
    @Permission(QUICK_CHAT_PERMISSION)
    @PlayerOnly
    fun execute(
        @Context player: Player,
        @Arg(name = "message", required = false) @Greedy message: String? = null,
    ) {
        if (!player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT,
                lang.msg("command.quick_mod_chat.only_moderators"),
                lang,
            )
        ) {
            return
        }

        if (message.isNullOrBlank()) {
            player.sendMessage(lang.msg("command.migrated.quick_mod_chat.default.quick_mod_chat"))
            player.sendMessage(lang.msg("command.migrated.quick_mod_chat.default.use_gmc_message_to_send_a_message"))
            player.sendMessage(lang.msg("command.migrated.quick_mod_chat.default.only_guild_moderators_will_see_your_message"))
            player.sendMessage(lang.msg("command.migrated.quick_mod_chat.default.to_toggle_mod_chat_mode_use_g"))
            return
        }

        when (RoseChatQuickChat.send(player, ChatChannelIds.MODCHAT, message)) {
            RoseChatQuickChat.Result.Dispatched -> {} // routed via RoseChat — no echo needed
            RoseChatQuickChat.Result.EmptyMessage ->
                player.sendMessage(lang.msg("command.migrated.quick_ally_chat.message.message_cannot_be_empty"))
            RoseChatQuickChat.Result.ChannelMissing ->
                player.sendMessage(lang.msg("command.migrated.quick_mod_chat.message.mod_chat_channel_is_not_configured_in"))
        }
    }
}
