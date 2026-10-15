package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.audience.Audience
import net.lumalyte.lg.common.PluginKeys
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/** Both body links and buttons use the same server-thread session boundary. */
internal class GuildDialogCallbacks(
    val player: Player,
    private val session: GuildDialogSession,
    private val revision: Int,
    val failure: () -> Unit,
    private val schedule: (() -> Unit) -> Unit = { task ->
        Bukkit.getScheduler().runTask(PluginKeys.getPlugin(), Runnable { task() })
    },
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun submit(
        audience: Audience,
        action: () -> Unit,
    ) {
        if (audience is Player && audience.uniqueId == player.uniqueId) {
            schedule { dispatch(audience, action) }
        }
    }

    @Suppress("TooGenericExceptionCaught") // Callback boundary closes private screens on adapter failure.
    private fun dispatch(actor: Player, action: () -> Unit) {
        try {
            if (session.consume(actor.uniqueId, revision, now())) action()
        } catch (error: Exception) {
            session.close()
            actor.closeDialog()
            org.slf4j.LoggerFactory.getLogger(
                javaClass,
            ).warn("Guild dialog action failed for {}", actor.uniqueId, error)
            failure()
        }
    }
}
