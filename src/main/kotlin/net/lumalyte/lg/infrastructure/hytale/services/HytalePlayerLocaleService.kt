package net.lumalyte.lg.infrastructure.hytale.services

import com.hypixel.hytale.server.core.universe.Universe
import net.lumalyte.lg.application.services.PlayerLocaleService
import java.util.UUID

class HytalePlayerLocaleService : PlayerLocaleService {

    override fun getLocale(playerId: UUID): String {
        val playerRef = Universe.get().getPlayer(playerId)
        return playerRef?.getLanguage() ?: "en_US"
    }
}
