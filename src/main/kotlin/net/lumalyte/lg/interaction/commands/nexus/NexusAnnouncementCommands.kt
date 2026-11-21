package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Greedy
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.commands.resolveAnnouncementGuild
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Inactive Nexus equivalents of ACF /ga and /gfa.
 * One optional greedy-message handler per root preserves bare-command help.
 * Never register these alongside the live ACF roots.
 */
@Command(name = "ga")
class NexusQuickAnnounceCommand(
    private val lang: LangService,
    private val chatService: ChatService,
    private val guildService: GuildService,
    private val memberService: MemberService,
) {
    @Subcommand("")
    @Permission("lumaguilds.guild.chat")
    @PlayerOnly
    fun announce(
        @Context player: Player,
        @Arg(name = "message", required = false) @Greedy message: String? = null,
    ) {
        val guildId = resolveGuild(player) ?: return
        if (message == null) {
            player.sendMessage(lang.msg("command.migrated.quick_announce.default.guild_announcements"))
            player.sendMessage(lang.msg("command.migrated.quick_announce.default.use_ga_message_to_announce_to_all"))
            player.sendMessage(lang.msg("command.migrated.quick_announce.default.add_a_color_code_ga_4_message"))
            player.sendMessage(lang.msg("command.migrated.quick_announce.default.colors_0_1_2_3_4_5"))
            player.sendMessage(lang.msg("command.migrated.quick_announce.default.default_is_6_gold_cooldown_5_minutes"))
            return
        }

        // ACF passed an array of space-separated tokens; preserve its color-prefix rule.
        val tokens = message.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+")) ?: emptyList()
        val colorOverride = tokens.firstOrNull()?.takeIf { it.matches(Regex("&[0-9]")) }
        val colorDigit = colorOverride?.get(1) ?: '6'
        val announcement = (if (colorOverride != null) tokens.drop(1) else tokens).joinToString(" ")
        if (announcement.isBlank()) {
            player.sendMessage(
                lang.msg("command.migrated.quick_ally_chat.message.message_cannot_be_empty"),
            )
            return
        }
        if (!chatService.sendGuildAnnouncement(guildId, player.uniqueId, announcement, colorDigit)) {
            player.sendMessage(lang.msg("command.migrated.quick_announce.announce.failed_to_send_announcement"))
        }
    }

    private fun resolveGuild(player: Player): UUID? {
        val guilds = guildService.getPlayerGuilds(player.uniqueId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.guildchat.you_are_not_in_a_guild"))
            return null
        }
        resolveAnnouncementGuild(player, guildService, memberService)?.let { return it }
        val hasAny = guilds.any {
            memberService.hasPermission(player.uniqueId, it.id, RankPermission.SEND_ANNOUNCEMENTS)
        }
        player.sendMessage(
            if (hasAny) {
                lang.msg("command.migrated.quick_announce.resolveguildforannounce.you_have_announcement_permission_in_multiple_guilds")
            } else {
                lang.msg("command.migrated.quick_announce.resolveguildforannounce.you_don_t_have_permission_to_send")
            },
        )
        return null
    }
}

@Command(name = "gfa")
class NexusFullscreenAnnounceCommand(
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val chatService: ChatService,
    private val penalties: PenaltyService,
    private val lang: LangService,
) {
    @Subcommand("")
    @Permission("lumaguilds.guild.chat")
    @PlayerOnly
    fun announce(
        @Context player: Player,
        @Arg(name = "message", required = false) @Greedy message: String? = null,
    ) {
        val text = message?.trim().orEmpty()
        val guildId = resolveAnnouncementGuild(player, guildService, memberService)
        if (text.isBlank() || guildId == null) {
            player.sendMessage(lang.msg("community.chat.fullscreen_usage"))
            return
        }
        if (penalties.isGuildMuted(guildId)) {
            player.sendMessage(lang.msg("notification.guild_chat.guild_muted"))
            return
        }
        if (!chatService.sendGuildAnnouncement(guildId, player.uniqueId, text)) {
            player.sendMessage(lang.msg("command.migrated.quick_announce.announce.failed_to_send_announcement"))
            return
        }
        val title = Title.title(lang.msg("community.chat.fullscreen_title"), Component.text(text))
        chatService.getOnlineGuildMembers(guildId).forEach { Bukkit.getPlayer(it)?.showTitle(title) }
    }
}
