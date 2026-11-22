package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** Fullscreen announcements share the existing permission, mute and rate-limit path. */
@CommandAlias("gfa")
internal class FullscreenAnnounceCommand(
    guilds: Lazy<GuildService>,
    members: Lazy<MemberService>,
    service: Lazy<ChatService>,
    penalties: Lazy<net.lumalyte.lg.application.services.PenaltyService>,
    lang: Lazy<LangService>,
) : BaseCommand() {
    private val guilds: GuildService by guilds
    private val members: MemberService by members
    private val service: ChatService by service
    private val penalties: net.lumalyte.lg.application.services.PenaltyService by penalties
    private val lang: LangService by lang

    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun announce(player: Player, vararg args: String) {
        val message = args.joinToString(" ").trim()
        val guildId = resolveAnnouncementGuild(player, guilds, members)
        if (message.isBlank() || guildId == null) {
            player.sendMessage(lang.msg("community.chat.fullscreen_usage"))
            return
        }
        if (!canSend(player, guildId, message)) return
        val title = Title.title(lang.msg("community.chat.fullscreen_title"), Component.text(message))
        service.getOnlineGuildMembers(guildId).forEach { Bukkit.getPlayer(it)?.showTitle(title) }
    }

    private fun canSend(player: Player, guildId: java.util.UUID, message: String): Boolean {
        if (penalties.isGuildMuted(guildId)) {
            player.sendMessage(lang.msg("notification.guild_chat.guild_muted"))
            return false
        }
        val sent = service.sendGuildAnnouncement(guildId, player.uniqueId, message)
        if (!sent) {
            player.sendMessage(lang.msg("command.migrated.quick_announce.announce.failed_to_send_announcement"))
        }
        return sent
    }
}
