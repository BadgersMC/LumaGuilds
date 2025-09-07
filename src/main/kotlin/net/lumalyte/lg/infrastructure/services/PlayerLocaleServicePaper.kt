package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.PlayerLocaleService
import org.bukkit.Bukkit
import java.util.*

class PlayerLocaleServicePaper: PlayerLocaleService {
    override fun getLocale(playerId: UUID): String {
        val player = Bukkit.getPlayer(playerId) ?: return ""
        return player.locale().toString()
    }
}
