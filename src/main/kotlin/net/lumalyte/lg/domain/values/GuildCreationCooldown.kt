package net.lumalyte.lg.domain.values

import java.time.Instant
import java.time.Duration

data class GuildCreationCooldown(val deletionWindowDays: Int = 7, val cooldownDays: Int = 15) {
    init { require(deletionWindowDays >= 0 && cooldownDays >= 0) }

    fun expiresAt(createdAt: Instant, deletedAt: Instant): Instant? {
        require(deletedAt >= createdAt) { "Deletion cannot precede creation" }
        if (cooldownDays == 0 || Duration.between(createdAt, deletedAt) >= Duration.ofDays(deletionWindowDays.toLong())) return null
        return deletedAt.plus(Duration.ofDays(cooldownDays.toLong()))
    }
}
