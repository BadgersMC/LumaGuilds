package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.ChatSettingsRepository
import net.lumalyte.lg.infrastructure.services.RealRoseChatAdapter
import net.lumalyte.lg.infrastructure.services.RoseChatAdapter
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/** Optional destination display owns only its own boss bar, never an action bar. */
internal class ChatDestinationIndicator(
    private val settings: ChatSettingsRepository,
    private val lang: LangService,
    private val chat: RoseChatAdapter = RealRoseChatAdapter(),
) : Listener, Runnable {
    private val bars = mutableMapOf<UUID, BossBar>()

    override fun run() {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (!settings.getVisibilitySettings(player.uniqueId).destinationIndicator) {
                bars.remove(player.uniqueId)?.let(player::hideBossBar)
            } else {
                val channel = chat.getCurrentChannel(player)
                val name = if (channel == null) lang.raw("community.chat.unknown") else when (channel.id) {
                    "guild" -> lang.raw("community.chat.guild")
                    "guild-ally" -> lang.raw("community.chat.ally")
                    "guild-modchat" -> lang.raw("community.chat.officer")
                    chat.getDefaultChannel()?.id -> lang.raw("community.chat.global")
                    else -> channel?.id ?: lang.raw("community.chat.unknown")
                }
                val title = lang.msg("community.chat.destination", "channel" to Component.text(name))
                val bar = bars.getOrPut(player.uniqueId) {
                    BossBar.bossBar(title, 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS).also(player::showBossBar)
                }
                bar.name(title)
            }
        }
    }

    @EventHandler fun onQuit(event: PlayerQuitEvent) { bars.remove(event.player.uniqueId)?.let(event.player::hideBossBar) }
    fun close() { bars.forEach { (id, bar) -> Bukkit.getPlayer(id)?.hideBossBar(bar) }; bars.clear() }
}
