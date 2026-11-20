package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.HallPedestalRepository
import net.lumalyte.lg.domain.rewards.HallPedestal
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.logging.Logger

/**
 * REQ-275: `/lumaguilds hall pedestal set|remove <n>`, `hall list` and `hall refresh`. Staff stand on
 * the spot, facing the way the statue should look, and set a numbered pedestal; slot 1 holds the
 * highest-prestige guild.
 */
internal class HallStaffCommand(
    private val pedestals: () -> HallPedestalRepository,
    private val refresh: () -> Unit,
    private val plans: () -> List<Pair<Int, String>>,
    private val lang: LangService,
    private val audit: (String) -> Unit = { Logger.getLogger("LumaGuilds").info(it) },
) {
    fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(lang.msg("admin.common.no_permission"))
            return
        }
        when (args.getOrNull(1)?.lowercase()) {
            "pedestal" -> pedestal(sender, args.getOrNull(2)?.lowercase(), args.getOrNull(3)?.toIntOrNull())
            "list" -> list(sender)
            "refresh" -> {
                refresh()
                sender.sendMessage(lang.msg("admin.hall.refreshing"))
            }
            else -> sender.sendMessage(lang.msg("admin.hall.usage"))
        }
    }

    private fun pedestal(sender: CommandSender, action: String?, slot: Int?) {
        if (action !in PEDESTAL_ACTIONS || slot == null || slot !in 1..MAX_PEDESTALS) {
            sender.sendMessage(lang.msg("admin.hall.usage"))
            return
        }
        when (action) {
            "set" -> {
                val player = sender as? Player ?: return sender.sendMessage(lang.msg("admin.hall.players_only"))
                val at = player.location
                val pedestal = HallPedestal(slot, at.world.name, at.blockX + 0.5, at.blockY.toDouble(), at.blockZ + 0.5, facing(at.yaw))
                pedestals().set(pedestal)
                audit("[Hall] ${sender.name} set pedestal $slot at ${pedestal.world} ${at.blockX} ${at.blockY} ${at.blockZ} yaw ${pedestal.yaw}")
                sender.sendMessage(lang.msg("admin.hall.set", "slot" to slot, "x" to at.blockX, "y" to at.blockY, "z" to at.blockZ))
            }
            "remove" -> {
                if (!pedestals().remove(slot)) return sender.sendMessage(lang.msg("admin.hall.missing", "slot" to slot))
                audit("[Hall] ${sender.name} removed pedestal $slot")
                sender.sendMessage(lang.msg("admin.hall.removed", "slot" to slot))
            }
        }
        refresh()
    }

    private fun list(sender: CommandSender) {
        val placed = pedestals().all()
        if (placed.isEmpty()) return sender.sendMessage(lang.msg("admin.hall.none"))
        val standing = plans().toMap()
        sender.sendMessage(lang.msg("admin.hall.list_header", "count" to placed.size))
        placed.forEach { p ->
            sender.sendMessage(lang.msg("admin.hall.list_line", "slot" to p.slot, "world" to p.world,
                "x" to p.x.toInt(), "y" to p.y.toInt(), "z" to p.z.toInt(), "guild" to (standing[p.slot] ?: "-")))
        }
    }

    companion object {
        const val PERMISSION = "lumaguilds.admin.hall"
        const val MAX_PEDESTALS = 24
        val SUBCOMMANDS = listOf("pedestal", "list", "refresh")
        val PEDESTAL_ACTIONS = listOf("set", "remove")

        /** Statues face one of the eight compass directions nearest to the way staff were looking. */
        fun facing(yaw: Float): Float {
            val snapped = Math.round(yaw / 45f) * 45f
            return ((snapped % 360f) + 540f) % 360f - 180f
        }

    }
}
