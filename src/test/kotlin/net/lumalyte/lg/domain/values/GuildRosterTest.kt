package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-191: roster order and paging on the combined guild page. */
class GuildRosterTest {
    private fun e(name: String, priority: Int, online: Boolean = false) = RosterEntry(UUID.randomUUID(), name, priority, online)

    private val leader = e("zed", 0)
    private val officerB = e("Bravo", 2, online = true)
    private val officerA = e("alpha", 2)
    private val member = e("Charlie", 5, online = true)
    private val all = listOf(member, officerB, leader, officerA)

    @Test
    fun `rank order is highest rank first, then name ignoring case`() {
        assertEquals(listOf(leader, officerA, officerB, member), GuildRoster.order(all, RosterSort.RANK))
    }

    @Test
    fun `online order puts online members first, then rank`() {
        assertEquals(listOf(officerB, member, leader, officerA), GuildRoster.order(all, RosterSort.ONLINE))
    }

    @Test
    fun `name order ignores case`() {
        assertEquals(listOf(officerA, officerB, member, leader), GuildRoster.order(all, RosterSort.NAME))
    }

    @Test
    fun `sort cycles rank, online, name`() {
        assertEquals(RosterSort.ONLINE, RosterSort.RANK.next())
        assertEquals(RosterSort.NAME, RosterSort.ONLINE.next())
        assertEquals(RosterSort.RANK, RosterSort.NAME.next())
    }

    @Test
    fun `36 heads per page`() {
        val many = (1..37).map { e("p$it", 5) }
        assertEquals(1, GuildRoster.pageCount(0))
        assertEquals(1, GuildRoster.pageCount(36))
        assertEquals(2, GuildRoster.pageCount(37))
        assertEquals(36, GuildRoster.page(many, 0).size)
        assertEquals(listOf(many[36]), GuildRoster.page(many, 1))
        assertEquals(listOf(many[36]), GuildRoster.page(many, 5), "a page past the end shows the last page")
        assertEquals(36, GuildRoster.page(many, -1).size)
    }
}

/** REQ-192: last seen shown in whole minutes, hours or days. */
class LastSeenTest {
    private val now = 10_000_000_000L

    @Test
    fun `last seen picks the largest whole unit`() {
        assertEquals(LastSeen(LastSeen.Unit.MINUTES, 1), LastSeen.of(now, now - 30_000))
        assertEquals(LastSeen(LastSeen.Unit.MINUTES, 59), LastSeen.of(now, now - 59 * 60_000L))
        assertEquals(LastSeen(LastSeen.Unit.HOURS, 5), LastSeen.of(now, now - 5 * 3_600_000L - 1))
        assertEquals(LastSeen(LastSeen.Unit.DAYS, 3), LastSeen.of(now, now - 3 * 86_400_000L))
        assertEquals(null, LastSeen.of(now, 0), "never seen")
    }
}
