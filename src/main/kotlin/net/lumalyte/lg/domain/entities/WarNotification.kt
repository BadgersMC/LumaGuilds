package net.lumalyte.lg.domain.entities

import java.util.UUID

enum class WarNotificationKind {
    DECLARATION_RECEIVED,
    DECLARATION_SENT,
    WAR_ACCEPTED,
    VICTORY,
    DEFEAT,
}

data class WarNotification(
    val id: UUID,
    val playerId: UUID,
    val kind: WarNotificationKind,
    val eventId: UUID,
    val ownGuildId: UUID,
    val opponentGuildId: UUID,
    val opponentName: String,
    val opponentBanner: String? = null,
    val durationSeconds: Long = 0,
    val objectiveCount: Int = 0,
    val objectiveDescription: String? = null,
    val wagerAmount: Int = 0,
    val terms: String? = null,
    val expiresAt: Long? = null,
    val ownKills: Int? = null,
    val opponentKills: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val deliveredAt: Long? = null,
) {
    init {
        require(opponentName.isNotBlank()) { "Opponent guild name cannot be blank" }
        require(durationSeconds >= 0) { "Duration cannot be negative" }
        require(objectiveCount >= 0) { "Objective count cannot be negative" }
        require(wagerAmount >= 0) { "Wager cannot be negative" }
    }
}
