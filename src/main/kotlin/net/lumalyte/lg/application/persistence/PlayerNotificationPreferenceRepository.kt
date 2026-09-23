package net.lumalyte.lg.application.persistence

import java.util.UUID

interface PlayerNotificationPreferenceRepository {
    fun isGuildLoginEnabled(playerId: UUID): Boolean

    fun guildLoginStates(playerIds: Set<UUID>): Map<UUID, Boolean> =
        playerIds.associateWith(::isGuildLoginEnabled)

    fun setGuildLoginEnabled(playerId: UUID, enabled: Boolean): Boolean
}
