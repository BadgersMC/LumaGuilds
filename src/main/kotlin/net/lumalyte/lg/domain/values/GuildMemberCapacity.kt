package net.lumalyte.lg.domain.values

import net.lumalyte.lg.config.MemberCapacityConfig

/**
 * REQ-190: a guild's member cap. Prestiged guilds use the level 100 cap, a
 * grandfathered guild never drops below its protected floor, and [ceiling]
 * (the server's `max_members_per_guild`) bounds everything.
 */
object GuildMemberCapacity {
    private const val PRESTIGED_LEVEL = 100

    fun forLevel(level: Int, config: MemberCapacityConfig): Int {
        val steps = level.coerceAtLeast(1) / config.stepLevels
        return minOf(config.max.toLong(), config.base.toLong() + config.perStep.toLong() * steps).toInt()
    }

    fun limit(level: Int, prestigeCount: Int, floor: Int?, config: MemberCapacityConfig, ceiling: Int): Int {
        val byLevel = forLevel(if (prestigeCount > 0) PRESTIGED_LEVEL else level, config)
        return minOf(ceiling, maxOf(byLevel, floor ?: 0)).coerceAtLeast(1)
    }
}
