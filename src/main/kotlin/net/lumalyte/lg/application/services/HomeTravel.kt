package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.values.HomeTravelTimes
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** REQ-179: home teleport countdown and cooldown for a player. */
interface HomeTravelPolicy {
    fun countdownSeconds(playerId: UUID): Int
    fun cooldownSeconds(playerId: UUID): Long

    /** No Quick Travel and no cooldown: used where no policy is wired (tests). */
    object Standard : HomeTravelPolicy {
        override fun countdownSeconds(playerId: UUID): Int = HomeTravelTimes.BASE_COUNTDOWN_SECONDS
        override fun cooldownSeconds(playerId: UUID): Long = 0
    }
}

/**
 * Reads the player's guild's best Quick Travel. Any failure reads as "no Quick
 * Travel", so a database hiccup never blocks or shortens a teleport.
 */
class HomeTravel(
    private val progression: ProgressionService,
    private val members: MemberService,
    private val config: ConfigService,
) : HomeTravelPolicy {
    override fun countdownSeconds(playerId: UUID): Int = HomeTravelTimes.countdownSeconds(multiplier(playerId))

    override fun cooldownSeconds(playerId: UUID): Long {
        val base = runCatching { config.loadConfig().guild.homeTeleportCooldownSeconds }.getOrDefault(0)
        return HomeTravelTimes.cooldownSeconds(base, multiplier(playerId))
    }

    private fun multiplier(playerId: UUID): Double = runCatching {
        members.getPlayerGuilds(playerId).firstOrNull()?.let(progression::getHomeCooldownMultiplier)
    }.getOrNull() ?: 1.0
}

/** Last home teleport per player, shared by commands and menus. */
class HomeTeleportCooldowns(private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val last = ConcurrentHashMap<UUID, Long>()

    fun markTeleported(playerId: UUID) {
        last[playerId] = nowMillis()
    }

    /** Whole seconds left, rounded up; 0 when the player may teleport. */
    fun remainingSeconds(playerId: UUID, cooldownSeconds: Long): Long {
        val stamp = last[playerId] ?: return 0
        val leftMillis = stamp + cooldownSeconds * MILLIS - nowMillis()
        return if (leftMillis <= 0) 0 else (leftMillis + MILLIS - 1) / MILLIS
    }

    private companion object {
        const val MILLIS = 1000L
    }
}
