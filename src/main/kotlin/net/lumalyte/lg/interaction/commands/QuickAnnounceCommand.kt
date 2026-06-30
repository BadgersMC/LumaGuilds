package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.lumalyte.lg.application.services.ChatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Guild announcement: `/ga <message>` sends a highlighted announcement to all
 * guild members. Only usable by members with the SEND_ANNOUNCEMENTS permission.
 *
 * Color override: `/ga &<0-9> <message>` uses the chosen Minecraft color code.
 * Default color is 6 (gold).
 *
 * Available colors: &0 black, &1 dark blue, &2 dark green, &3 dark aqua,
 * &4 dark red, &5 dark purple, &6 gold (default), &7 gray, &8 dark gray,
 * &9 blue.
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
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }
        val primaryGuildId = guilds.first().id
        if (!memberService.hasPermission(
                playerId, primaryGuildId, RankPermission.SEND_ANNOUNCEMENTS,
            )
        ) {
            player.sendMessage("§c❌ You don't have permission to send announcements!")
            return
        }
        player.sendMessage("§6=== Guild Announcements ===")
        player.sendMessage("§7Use §f/ga <message> §7to announce to all guild members.")
        player.sendMessage("§7Add a color code: §f/ga &c <message> §7for custom colors.")
        player.sendMessage("§7Colors: §0&0 §1&1 §2&2 §3&3 §4&4 §5&5 §6&6 §7&7 §8&8 §9&9")
        player.sendMessage("§7Default is §6&6 §7(gold). Cooldown: 5 minutes.")
    }

    /** Sends an announcement with optional color override. First arg may be `&` + digit. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onAnnounce(player: Player, vararg args: String) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }

        val primaryGuild = guilds.first()
        val guildId = primaryGuild.id

        if (!memberService.hasPermission(
                playerId, guildId, RankPermission.SEND_ANNOUNCEMENTS,
            )
        ) {
            player.sendMessage("§c❌ You don't have permission to send announcements!")
            return
        }

        if (args.isEmpty()) {
            player.sendMessage("§c❌ Provide a message. §7Usage: /ga [&color] <message>")
            return
        }

        // Parse optional color prefix: first arg may be "&" + single digit 0-9
        val colorDigit: Char
        val message: String
        if (args[0].matches(Regex("&[0-9]"))) {
            colorDigit = args[0][1]
            message = args.drop(1).joinToString(" ")
        } else {
            colorDigit = '6' // default gold
            message = args.joinToString(" ")
        }

        if (message.isBlank()) {
            player.sendMessage("§c❌ Message cannot be empty.")
            return
        }

        val success = chatService.sendGuildAnnouncement(
            guildId, playerId, message, colorDigit,
        )
        if (!success) {
            player.sendMessage("§c❌ Failed to send announcement.")
        }
        // No success echo — the announcement is broadcast to all guild members
    }
}
