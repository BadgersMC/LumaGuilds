package net.lumalyte.lg.infrastructure.listeners

import dev.rosewood.rosechat.chat.channel.Channel
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildChatReconnectSettingsService
import net.lumalyte.lg.infrastructure.services.RealRoseChatAdapter
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin

/** Applies the guild's opt-in reconnect policy after RoseChat restores its channel. */
internal class GuildChatReconnectListener(
    private val plugin: Plugin,
    private val settings: GuildChatReconnectSettingsService,
    private val lang: LangService,
    private val chat: RoseChatAdapter = RealRoseChatAdapter(),
) : Listener {
    /** Runs on the next server tick, after the ordinary join handlers. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTask(plugin, Runnable { reset(event.player) })
    }

    /** Rechecks the live session, membership, preference and channel at execution time. */
    internal fun reset(player: Player) {
        if (!isCurrentSession(player)) return
        val global = resetDestination(player) ?: return
        chat.switchChannel(player, global)
        player.sendMessage(lang.msg("notification.guild_chat.moved_to_global"))
    }

    private fun isCurrentSession(player: Player): Boolean =
        player.isOnline && Bukkit.getPlayer(player.uniqueId) === player

    private fun resetDestination(player: Player): Channel? {
        val current = chat.getCurrentChannel(player)
        return if (current != null && shouldReset(player, current)) {
            chat.getDefaultChannel()?.takeIf { it.id != current.id }
        } else {
            null
        }
    }

    private fun shouldReset(player: Player, channel: Channel): Boolean =
        (channel.id == "guild" || channel.id == "guild-ally") && settings.shouldReset(player.uniqueId)
}
