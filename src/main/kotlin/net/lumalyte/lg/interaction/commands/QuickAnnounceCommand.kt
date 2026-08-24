package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Guild announcement: `/ga <message>` sends a highlighted announcement to all
 * guild members. Only usable by members with SEND_ANNOUNCEMENTS permission.
 *
 * Color override: `/ga &<0-9> <message>` uses the chosen Minecraft color code.
 * Default color is 6 (gold).
 *
 * Colors: &0 black, &1 dark blue, &2 dark green, &3 dark aqua,
 * &4 dark red, &5 dark purple, &6 gold, &7 gray, &8 dark gray, &9 blue.
 */
@CommandAlias("ga")
internal class QuickAnnounceCommand : BaseCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val chatService: ChatService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    /** Shows usage help when `/ga` is typed without arguments. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onDefault(player: Player) {
        val guildId = resolveGuildForAnnounce(player) ?: return
        player.sendMessage(lang.msg("command.migrated.quick_announce.default.guild_announcements"))
        player.sendMessage(lang.msg("command.migrated.quick_announce.default.use_ga_message_to_announce_to_all"))
        player.sendMessage(lang.msg("command.migrated.quick_announce.default.add_a_color_code_ga_4_message"))
        player.sendMessage(lang.msg("command.migrated.quick_announce.default.colors_0_1_2_3_4_5"))
        player.sendMessage(lang.msg("command.migrated.quick_announce.default.default_is_6_gold_cooldown_5_minutes"))
    }

    /** Sends an announcement with optional color override. First arg may be `&` + digit. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onAnnounce(player: Player, vararg args: String) {
        val guildId = resolveGuildForAnnounce(player) ?: return
        val (colorDigit, message) = parseAnnouncementInput(args)
        if (message.isBlank()) {
            val msg =
                if (args.isEmpty()) {
                    lang.msg("command.migrated.quick_announce.announce.provide_a_message_usage_ga_color_message")
                } else {
                    lang.msg("command.migrated.quick_ally_chat.message.message_cannot_be_empty")
                }
            player.sendMessage(msg)
            return
        }
        val ok = chatService.sendGuildAnnouncement(guildId, player.uniqueId, message, colorDigit)
        if (!ok) {
            player.sendMessage(lang.msg("command.migrated.quick_announce.announce.failed_to_send_announcement"))
        }
    }

    private fun resolveGuildForAnnounce(player: Player): java.util.UUID? {
        val guilds = guildService.getPlayerGuilds(player.uniqueId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.guildchat.you_are_not_in_a_guild"))
            return null
        }
        val guildId = resolveAnnouncementGuild(player, guildService, memberService)
        if (guildId != null) return guildId

        val hasAny = guilds.any { guild ->
            memberService.hasPermission(
                player.uniqueId,
                guild.id,
                RankPermission.SEND_ANNOUNCEMENTS,
            )
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

    private data class AnnouncementInput(val colorDigit: Char, val message: String)

    private fun parseAnnouncementInput(args: Array<out String>): AnnouncementInput {
        val colorRegex = Regex("&[0-9]")
        return if (args.isNotEmpty() && args[0].matches(colorRegex)) {
            AnnouncementInput(args[0][1], args.drop(1).joinToString(" "))
        } else {
            AnnouncementInput('6', args.joinToString(" "))
        }
    }
}
