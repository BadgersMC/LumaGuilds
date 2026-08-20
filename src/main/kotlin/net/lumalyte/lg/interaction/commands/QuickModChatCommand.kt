package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.ChatChannelIds
import net.lumalyte.lg.infrastructure.services.RoseChatQuickChat
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Quick mod chat: `/gmc <message>` sends one message to guild moderators
 * without changing the player's current chat channel.
 *
 * `/gmc` alone shows help text. Only usable by guild moderators.
 */
@CommandAlias("gmc")
internal class QuickModChatCommand : BaseCommand(), KoinComponent {
    private val lang: LangService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    /** Shows usage help when `/gmc` is typed without arguments. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onDefault(player: Player) {
        if (!player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT,
                lang.msg("command.quick_mod_chat.only_moderators"),
                lang,
            )
        ) {
            return
        }
        player.sendMessage(lang.msg("command.migrated.quick_mod_chat.default.quick_mod_chat"))
        player.sendMessage(lang.msg("command.migrated.quick_mod_chat.default.use_gmc_message_to_send_a_message"))
        player.sendMessage(lang.msg("command.migrated.quick_mod_chat.default.only_guild_moderators_will_see_your_message"))
        player.sendMessage(lang.msg("command.migrated.quick_mod_chat.default.to_toggle_mod_chat_mode_use_g"))
    }

    /** Sends a one-shot message to the RoseChat guild-modchat channel via quickChat. */
    @Default
    @CommandPermission("lumaguilds.guild.chat")
    fun onMessage(player: Player, vararg message: String) {
        if (!player.requireGuildPermission(
                guildService, memberService, RankPermission.MODERATE_CHAT,
                lang.msg("command.quick_mod_chat.only_moderators"),
                lang,
            )
        ) {
            return
        }

        val text = message.joinToString(" ")
        when (RoseChatQuickChat.send(player, ChatChannelIds.MODCHAT, text)) {
            RoseChatQuickChat.Result.Dispatched -> {} // routed via RoseChat — no echo needed
            RoseChatQuickChat.Result.EmptyMessage ->
                player.sendMessage(lang.msg("command.migrated.quick_ally_chat.message.message_cannot_be_empty"))
            RoseChatQuickChat.Result.ChannelMissing ->
                player.sendMessage(lang.msg("command.migrated.quick_mod_chat.message.mod_chat_channel_is_not_configured_in"))
        }
    }
}
