package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.values.ChatChannelIds
import net.lumalyte.lg.infrastructure.services.RoseChatQuickChat
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Quick ally chat: `/gac <message>` sends one message to ally chat without
 * changing the player's current chat channel (which `/g allychat` permanently
 * toggles).
 *
 * `/gac` alone shows help text.
 */
@CommandAlias("gac")
internal class QuickAllyChatCommand : BaseCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val guildService: GuildService by inject()

    /** Shows usage help when `/gac` is typed without arguments. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onDefault(player: Player) {
        if (!player.requireGuildMembership(guildService, lang)) return
        player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.quick_ally_chat"))
        player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.use_gac_message_to_send_a_single"))
        player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.your_chat_channel_won_t_change_you"))
        player.sendMessage(lang.msg("command.migrated.quick_ally_chat.default.to_toggle_permanent_ally_chat_mode_use"))
    }

    /** Sends a one-shot message to the RoseChat guild-ally channel via quickChat. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onMessage(player: Player, vararg message: String) {
        if (!player.requireGuildMembership(guildService, lang)) return

        val text = message.joinToString(" ")
        when (RoseChatQuickChat.send(player, ChatChannelIds.ALLY, text)) {
            RoseChatQuickChat.Result.Dispatched -> {} // routed via RoseChat — no echo needed
            RoseChatQuickChat.Result.EmptyMessage ->
                player.sendMessage(lang.msg("command.migrated.quick_ally_chat.message.message_cannot_be_empty"))
            RoseChatQuickChat.Result.ChannelMissing ->
                player.sendMessage(lang.msg("command.migrated.quick_ally_chat.message.ally_channel_is_not_configured_in_rosechat"))
        }
    }
}
