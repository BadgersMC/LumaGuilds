package net.lumalyte.lg.domain.entities

import java.util.UUID

data class WarBannerState(
    val guildId: UUID,
    val bannerId: UUID,
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int,
    val placedBy: UUID,
    val transactionId: UUID,
    val placedAt: Long,
    val expiresAt: Long,
    val cooldownUntil: Long,
    val active: Boolean,
) {
    fun isActiveAt(now: Long): Boolean = active && expiresAt > now
}
