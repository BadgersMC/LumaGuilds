package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.WarNotification
import java.util.UUID

interface WarNotificationRepository {
    /** Stores a notification once. Returns false when the deterministic id already exists. */
    fun add(notification: WarNotification): Boolean

    /** Returns undelivered notifications oldest-first. */
    fun getPending(playerId: UUID): List<WarNotification>

    /** Marks an unread notification delivered. */
    fun markDelivered(notificationId: UUID, deliveredAt: Long): Boolean
}
