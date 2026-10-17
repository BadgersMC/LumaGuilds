package net.lumalyte.lg.application.services

import java.util.UUID

interface GuildLoginNotificationService {
    fun onPlayerJoin(playerId: UUID)
    fun isEnabled(playerId: UUID): Boolean
    fun setEnabled(playerId: UUID, enabled: Boolean): Boolean
}
