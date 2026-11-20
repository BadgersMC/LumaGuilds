package net.lumalyte.lg.domain.values

import net.lumalyte.lg.config.MemberCapacityConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-190: 10 members, +5 every 5 levels, up to 100; prestige and floors never shrink it. */
class GuildMemberCapacityTest {
    private val config = MemberCapacityConfig()
    private fun cap(level: Int, prestige: Int = 0, floor: Int? = null, ceiling: Int = 100) =
        GuildMemberCapacity.limit(level, prestige, floor, config, ceiling)

    @Test
    fun `starts at 10 and gains 5 every 5 levels`() {
        assertEquals(listOf(10, 10, 15, 15, 20, 55, 95), listOf(1, 4, 5, 9, 10, 45, 89).map { cap(it) })
    }

    @Test
    fun `reaches 100 at level 90 and stops there`() {
        assertEquals(100, cap(90))
        assertEquals(100, cap(100))
    }

    @Test
    fun `a prestiged guild keeps the level 100 cap`() {
        assertEquals(100, cap(level = 1, prestige = 1))
    }

    @Test
    fun `a protected floor keeps a grandfathered guild's room`() {
        assertEquals(42, cap(level = 3, floor = 42))
        assertEquals(60, cap(level = 50, floor = 12))
    }

    @Test
    fun `the server maximum is the absolute ceiling, floors included`() {
        assertEquals(50, cap(level = 100, ceiling = 50))
        assertEquals(50, cap(level = 1, floor = 70, ceiling = 50))
    }

    @Test
    fun `settings follow config`() {
        val custom = MemberCapacityConfig(base = 8, perStep = 2, stepLevels = 10, max = 20)
        assertEquals(12, GuildMemberCapacity.limit(25, 0, null, custom, 100))
        assertEquals(20, GuildMemberCapacity.limit(100, 0, null, custom, 100))
    }
}
