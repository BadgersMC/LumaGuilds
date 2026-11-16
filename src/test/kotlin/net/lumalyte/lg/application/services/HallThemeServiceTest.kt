package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.HallDisplayRepository
import net.lumalyte.lg.application.persistence.PrestigeConfirmation
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.persistence.PrestigeRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import net.lumalyte.lg.domain.rewards.PrestigeQuote
import net.lumalyte.lg.domain.rewards.PrestigeResult
import net.lumalyte.lg.domain.rewards.RewardCatalog
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** REQ-260/261: one theme per prestige, and only owned themes can be worn. */
class HallThemeServiceTest {
    private val guild = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private var prestiges = 2

    private class Memory : HallDisplayRepository {
        val owned = mutableListOf<HallTheme>()
        val choices = mutableMapOf<HallSlot, HallTheme>()
        var skin: UUID? = null
        override fun owned(guildId: UUID) = owned.toList()
        override fun earn(guildId: UUID, theme: HallTheme, limit: Int, at: Instant): Boolean {
            if (theme in owned || owned.size >= limit) return false
            owned += theme
            return true
        }
        override fun choices(guildId: UUID) = choices.toMap()
        override fun choose(guildId: UUID, slot: HallSlot, theme: HallTheme?) {
            if (theme == null) choices.remove(slot) else choices[slot] = theme
        }
        override fun skin(guildId: UUID) = skin
        override fun setSkin(guildId: UUID, playerId: UUID?) { skin = playerId }
        override fun deleteAll(guildId: UUID) { owned.clear(); choices.clear(); skin = null }
    }

    private val memory = Memory()
    private val service = HallThemeService(memory, { prestiges }, { actor, _ -> actor == owner }, { id, _ -> id == member || id == owner })

    @Test
    fun `a guild owns one theme per prestige`() {
        assertEquals(HallThemeService.Outcome.DONE, service.earnFromPrestige(guild, HallTheme.EMBER))
        assertEquals(HallThemeService.Outcome.NO_PICKS, service.earnFromPrestige(guild, HallTheme.EMBER))
        assertEquals(1, service.display(guild)!!.picksLeft)
        assertEquals(HallThemeService.Outcome.NOT_ALLOWED, service.earn(member, guild, HallTheme.TIDAL))
        assertEquals(HallThemeService.Outcome.DONE, service.earn(owner, guild, HallTheme.TIDAL))
        assertEquals(HallThemeService.Outcome.NO_PICKS, service.earn(owner, guild, HallTheme.GILDED))
        assertEquals(listOf(HallTheme.EMBER, HallTheme.TIDAL), service.display(guild)!!.owned)
        assertEquals(0, service.display(guild)!!.picksLeft)
        assertEquals(4, service.available(guild).size)
    }

    @Test
    fun `slots wear the first theme until changed, and only owned themes`() {
        service.earnFromPrestige(guild, HallTheme.EMBER)
        service.earnFromPrestige(guild, HallTheme.TIDAL)
        assertEquals(HallTheme.EMBER, service.display(guild)!!.wearing(HallSlot.POSE))
        assertEquals(HallThemeService.Outcome.DONE, service.wear(owner, guild, HallSlot.POSE, HallTheme.TIDAL))
        assertEquals(HallThemeService.Outcome.NOT_OWNED, service.wear(owner, guild, HallSlot.POSE, HallTheme.CROWNED))
        assertEquals(HallTheme.TIDAL, service.display(guild)!!.wearing(HallSlot.POSE))
        assertEquals(HallThemeService.Outcome.DONE, service.wearAll(owner, guild, HallTheme.TIDAL))
        assertEquals(HallTheme.TIDAL, service.display(guild)!!.wearing(HallSlot.PLINTH))
        assertEquals(HallThemeService.Outcome.NOT_ALLOWED, service.wear(member, guild, HallSlot.POSE, HallTheme.EMBER))
    }

    @Test
    fun `the statue skin must be a member`() {
        assertEquals(HallThemeService.Outcome.NOT_A_MEMBER, service.setSkin(owner, guild, UUID.randomUUID()))
        assertEquals(HallThemeService.Outcome.DONE, service.setSkin(owner, guild, member))
        assertEquals(member, service.display(guild)!!.skin)
        service.disbanded(guild)
        assertNull(service.display(guild)!!.skin)
    }

    @Test
    fun `the theme chosen on the prestige screen is granted when the prestige applies, retries included`() {
        val actor = UUID.randomUUID()
        val quote = PrestigeQuote(UUID.randomUUID(), guild, actor, "bank-1", 100, 1, 0)
        val applied = PrestigeResult.Applied(quote.transactionId, "bank-1", 100, 1000, 900, 1, 1, 10000, 1)
        val repository = mockk<PrestigeRepository> {
            every { confirmWithCommitStatus(quote, any()) } returnsMany listOf(
                PrestigeConfirmation(applied, true), PrestigeConfirmation(applied),
            )
        }
        val granted = mutableListOf<Pair<UUID, HallTheme>>()
        val prestige = GuildPrestigeService(
            mockk(), mockk<ProgressionRepository>(relaxed = true), RewardCatalog.chapterTwo(), repository,
            mockk { every { allowed(actor, guild) } returns true },
            mockk<ConfigService> { every { loadConfig() } returns MainConfig() },
            history = mockk<PrestigeHistory>(relaxed = true),
            grantHallTheme = { id, theme -> granted += id to theme },
        )
        prestige.confirm(actor, quote, HallTheme.WINGED)
        prestige.confirm(actor, quote, HallTheme.WINGED)
        prestige.confirm(actor, quote)
        assertEquals(listOf(guild to HallTheme.WINGED, guild to HallTheme.WINGED), granted)
    }
}
