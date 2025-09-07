package net.lumalyte.lg.application.services

import java.util.*

interface PlayerLocaleService {
    fun getLocale(playerId: UUID): String
}
