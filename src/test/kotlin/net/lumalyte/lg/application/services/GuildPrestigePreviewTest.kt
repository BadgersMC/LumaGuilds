package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.PrestigeCarryOverConfig
import net.lumalyte.lg.domain.entities.GuildProgression
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.domain.values.ProgressionCurve
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-249: the preview is worked out the same way the prestige applies. */
class GuildPrestigePreviewTest {
    private val guild = UUID.randomUUID()
    private val owners = mockk<RewardOwnershipRepository>()
    private val progression = mockk<ProgressionRepository>()
    private val history = mockk<PrestigeHistory>()
    private var settings = MainConfig()

    private fun service() = GuildPrestigeService(
        owners, progression, RewardCatalog.chapterTwo(), mockk(), mockk(),
        mockk<ConfigService>().also { every { it.loadConfig() } answers { settings } },
        history = history,
    )

    private fun guildAt(count: Int, xpPast100: Int = 0) {
        val curve = ProgressionCurve.from(settings.progression)
        val permanent = listOf("bank-3", "bank-4", "bank-5", "bank-6", "bank-7", "bank-8").take(count).toSet() + "home-2"
        every { owners.read(guild) } returns RewardOwnershipRead.Found(
            RewardOwnershipSnapshot(1, RewardOwnership(setOf("bank-1"), permanent, 1, count)),
        )
        every { progression.getGuildProgression(guild) } returns
            GuildProgression(guild, curve.totalExperienceForLevel(100) + xpPast100, currentLevel = 100)
        every { history.firstToReach(any()) } returns null
    }

    private fun quote(count: Int) = PrestigeQuote(UUID.randomUUID(), guild, UUID.randomUUID(), "bank-1", 16_000, 1, count)

    @Test
    fun `first prestige lists the home slot change, kept member limit and Discord role`() {
        guildAt(0)
        val preview = service().preview(quote(0))!!
        // Upstream home policy: capacity is the base home plus one per prestige plus paid Outposts
        // active in the current prestige; an old permanent "home-2" id no longer adds a slot.
        assertEquals(1, preview.tier)
        assertEquals(1, preview.homeSlotsBefore)
        assertEquals(2, preview.homeSlotsAfter)
        assertEquals(settings.guild.memberCapacity.max.coerceAtMost(settings.guild.maxMembersPerGuild), preview.memberLimitKept)
        assertEquals(settings.discordGuildRoles.minimumLevel, preview.discordRoleMinimumLevel)
        assertTrue(preview.firstOnServer)
        assertNull(preview.carry, "carry-over is off by default")
        assertNull(preview.memberCreditTenureHours, "member advancements are off by default")
    }

    @Test
    fun `later prestiges skip first-prestige lines, list their perk and know the final tier`() {
        guildAt(1)
        val second = service().preview(quote(1))!!
        assertNull(second.memberLimitKept)
        assertNull(second.discordRoleMinimumLevel)
        assertEquals(listOf(PrestigePerk.SECOND_ALLY_HOME), second.perks)
        guildAt(5)
        assertTrue(service().preview(quote(5))!!.finalTier)
    }

    @Test
    fun `carry-over shows the exact XP and starting level`() {
        settings = MainConfig().apply {
            progression = progression.copy(prestige = progression.prestige.copy(
                carryOver = PrestigeCarryOverConfig(enabled = true, landingLevel = 5),
            ))
        }
        val curve = ProgressionCurve.from(settings.progression)
        val extra = curve.totalExperienceForLevel(3) + 10
        guildAt(0, xpPast100 = extra)
        val carry = service().preview(quote(0))!!.carry!!
        assertEquals(extra, carry.experience)
        assertEquals(3, carry.startLevel)
    }

    @Test
    fun `someone already reached the tier`() {
        guildAt(0)
        every { history.firstToReach(1) } returns PrestigeRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "bank-1", Instant.EPOCH)
        assertEquals(false, service().preview(quote(0))!!.firstOnServer)
    }
}
