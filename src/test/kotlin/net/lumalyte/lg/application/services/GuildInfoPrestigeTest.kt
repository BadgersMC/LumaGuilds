package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-172: what /g info shows about a guild's prestiges. */
class GuildInfoPrestigeTest {
    private val guild = UUID.randomUUID()
    private val other = UUID.randomUUID()

    private fun record(guildId: UUID, tier: Int, day: Long) =
        PrestigeRecord(UUID.randomUUID(), guildId, UUID.randomUUID(), tier, "bank_extension_3", Instant.ofEpochSecond(day * 86_400))

    private fun history(records: List<PrestigeRecord>) = object : PrestigeHistory {
        override fun history(guildId: UUID) = records.filter { it.guildId == guildId }.sortedBy { it.appliedAt }
        override fun firstToReach(tier: Int) = records.filter { it.tier == tier }.minByOrNull { it.appliedAt }
    }

    @Test
    fun `a guild that never prestiged has no history`() {
        assertEquals(GuildInfoPrestige.None, GuildInfoPrestige.of(history(emptyList()), guild))
    }

    @Test
    fun `each prestige is listed oldest first with its date and whether it was first on the server`() {
        val mine1 = record(guild, 1, 10)
        val theirs1 = record(other, 1, 5)
        val mine2 = record(guild, 2, 20)
        val theirs2 = record(other, 2, 25)
        val result = GuildInfoPrestige.of(history(listOf(mine2, theirs1, mine1, theirs2)), guild)
        assertEquals(
            GuildInfoPrestige.Prestiged(
                listOf(
                    GuildInfoPrestige.Entry(1, mine1.appliedAt, serverFirst = false),
                    GuildInfoPrestige.Entry(2, mine2.appliedAt, serverFirst = true),
                ),
            ),
            result,
        )
        assertEquals(2, (result as GuildInfoPrestige.Prestiged).current)
    }

    @Test
    fun `an unreadable history still lets the menu open`() {
        val broken = object : PrestigeHistory {
            override fun history(guildId: UUID): List<PrestigeRecord> = error("database down")
            override fun firstToReach(tier: Int): PrestigeRecord? = null
        }
        assertEquals(GuildInfoPrestige.Unavailable, GuildInfoPrestige.of(broken, guild))
    }

    @Test
    fun `a failing server-first lookup only drops the mark`() {
        val mine = record(guild, 1, 10)
        val flaky = object : PrestigeHistory {
            override fun history(guildId: UUID) = listOf(mine)
            override fun firstToReach(tier: Int): PrestigeRecord? = error("database down")
        }
        assertEquals(
            GuildInfoPrestige.Prestiged(listOf(GuildInfoPrestige.Entry(1, mine.appliedAt, serverFirst = false))),
            GuildInfoPrestige.of(flaky, guild),
        )
    }
}
