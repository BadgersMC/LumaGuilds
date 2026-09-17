package net.lumalyte.lg.application.persistence

import java.util.UUID

data class BannerPurchase(val id: UUID, val guildId: UUID, val playerId: UUID,
    val amount: Long, val banner: String, val phase: String)

interface BannerPurchaseRepository {
    fun acquire(guildId: UUID, playerId: UUID, amount: Long, banner: String): BannerPurchase
    fun transition(id: UUID, expected: String, next: String): Boolean
}
