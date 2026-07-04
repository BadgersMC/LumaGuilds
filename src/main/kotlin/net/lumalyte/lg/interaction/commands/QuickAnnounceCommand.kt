package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
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
    private val chatService: ChatService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    /** Shows usage help when `/ga` is typed without arguments. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onDefault(player: Player) {
        val guildId = requireAnnouncementGuild(player) ?: return
        player.sendMessage("§6=== Guild Announcements ===")
        player.sendMessage("§7Use §f/ga <message> §7to announce to all guild members.")
        player.sendMessage("§7Add a color code: §f/ga &4 <message> §7for custom colors.")
        player.sendMessage("§7Colors: §0&0 §1&1 §2&2 §3&3 §4&4 §5&5 §6&6 §7&7 §8&8 §9&9")
        player.sendMessage("§7Default is §6&6 §7(gold). Cooldown: 5 minutes.")
    }

    /** Sends an announcement with optional color override. First arg may be `&` + digit. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onAnnounce(player: Player, vararg args: String) {
        val guildId = requireAnnouncementGuild(player) ?: return
        val (colorDigit, message) = parseAnnouncementInput(args)
        if (message.isBlank()) {
            val msg =
                if (args.isEmpty()) {
                    "§c❌ Provide a message. Usage: /ga [&color] <message>"
                } else {
                    "§c❌ Message cannot be empty."
                }
            player.sendMessage(msg)
            return
        }
        val ok = chatService.sendGuildAnnouncement(guildId, player.uniqueId, message, colorDigit)
        if (!ok) {
            player.sendMessage("§c❌ Failed to send announcement.")
        }
    }

    /**
     * Resolves the guild for announcements. Sends an error to [player] and
     * returns `null` if zero or multiple guilds have SEND_ANNOUNCEMENTS.
     */
    private fun requireAnnouncementGuild(player: Player): java.util.UUID? {
        val guilds = guildService.getPlayerGuilds(player.uniqueId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return null
        }
        val guildId = resolveAnnouncementGuild(player, guildService, memberService)
        return when {
            guildId != null -> guildId
            else -> {
                // Distinguish zero-perm from ambiguity by checking whether ANY guild has it
                val hasAny = guilds.any { guild ->
                    memberService.hasPermission(
                        player.uniqueId, guild.id,
                        net.lumalyte.lg.domain.entities.RankPermission.SEND_ANNOUNCEMENTS,
                    )
                }
                if (hasAny) {
                    player.sendMessage(
                        "§c❌ You have announcement permission in multiple guilds. " +
                            "Please leave extra guilds or ask an admin for help.",
                    )
                } else {
                    player.sendMessage(
                        "§c❌ You don't have permission to send announcements!",
                    )
                }
                null
            }
        }
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
