package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.GuildPrestigeNotifier
import net.lumalyte.lg.application.services.PrestigeCreditDispatcher
import net.lumalyte.lg.application.services.PrestigeMemberCredits
import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory

/** Console dispatch for REQ-154; refuses off-thread so the credit is not recorded and retries. */
class PrestigeCreditConsoleDispatcher : PrestigeCreditDispatcher {
    override fun dispatch(command: String) {
        check(Bukkit.isPrimaryThread()) { "Prestige credits must dispatch on the server thread" }
        check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) { "Unknown prestige credit command: $command" }
    }
}

/** Credits online members right after a newly committed prestige (REQ-154). */
class PrestigeCreditNotifier(private val credits: PrestigeMemberCredits) : GuildPrestigeNotifier {
    override fun prestiged(completion: PrestigeCompletion) =
        credits.deliverToOnlineMembers(completion.guildId) { id -> Bukkit.getPlayer(id)?.name }
}

/** Credits anything owed a few seconds after join, once other plugins have loaded the player. */
class PrestigeCreditJoinListener(
    private val plugin: Plugin,
    private val credits: PrestigeMemberCredits,
    private val delayTicks: Long = 100L,
) : Listener {
    private val logger = LoggerFactory.getLogger(PrestigeCreditJoinListener::class.java)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val id = event.player.uniqueId
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val player = Bukkit.getPlayer(id) ?: return@Runnable
            runCatching { credits.deliver(id, player.name) }
                .onFailure { logger.warn("Prestige credit delivery failed for $id", it) }
        }, delayTicks)
    }
}
