package net.lumalyte.lg.infrastructure.placeholders

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildPrestigeStandings
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.config.PrestigeConfig
import net.lumalyte.lg.config.ProgressionConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.PrestigeStanding
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-155 and REQ-156 through the real PlaceholderAPI expansion. */
class LumaGuildsExpansionPrestigeTest {
    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>()
    private val configService = mockk<ConfigService>()
    private val standings = mockk<GuildPrestigeStandings>()
    private val progression = mockk<ProgressionRepository>()
    private val crowned = guild("Vibe", 12)
    private val climber = guild("WOT", 88)
    private val sameTier = guild("BoopGang", 40)
    private val fresh = guild("PTC", 99)

    private fun guild(name: String, level: Int) =
        Guild(UUID.randomUUID(), name, level = level, createdAt = Instant.parse("2026-05-01T00:00:00Z"))

    @BeforeEach
    fun setup() {
        stopKoin()
        startKoin {
            modules(module {
                single<GuildService> { guildService }
                single<MemberService> { memberService }
                single<ConfigService> { configService }
                single<GuildPrestigeStandings> { standings }
                single<ProgressionRepository> { progression }
            })
        }
        every { configService.loadConfig() } returns MainConfig().apply {
            progression = ProgressionConfig(prestige = PrestigeConfig(badges = listOf("", "B1", "B2", "B3", "B4", "B5", "B6")))
        }
        every { guildService.getAllGuilds() } returns setOf(fresh, sameTier, climber, crowned)
        listOf(crowned, climber, sameTier, fresh).forEach { every { guildService.getGuild(it.id) } returns it }
        every { progression.getGuildProgression(any()) } returns null
        every { standings.standing(crowned.id) } returns PrestigeStanding(6, Instant.parse("2026-10-24T12:00:00Z"), 6)
        every { standings.standing(climber.id) } returns PrestigeStanding(3, null, 0)
        every { standings.standing(sameTier.id) } returns PrestigeStanding(3, null, 0)
        every { standings.standing(fresh.id) } returns PrestigeStanding(0, null, 0)
    }

    @AfterEach
    fun cleanup() = stopKoin()

    private fun member(of: Guild): Player {
        val player = mockk<Player>()
        val id = UUID.randomUUID()
        every { player.uniqueId } returns id
        every { memberService.getPlayerGuilds(id) } returns setOf(of.id)
        return player
    }

    @Test
    fun `player placeholders use the cached standing and configured badges`() {
        val expansion = LumaGuildsExpansion()
        val player = member(crowned)
        assertEquals("6", expansion.onPlaceholderRequest(player, "guild_prestige"))
        assertEquals("VI", expansion.onPlaceholderRequest(player, "guild_prestige_roman"))
        assertEquals("B6", expansion.onPlaceholderRequest(player, "guild_prestige_badge"))
        assertEquals("6", expansion.onPlaceholderRequest(player, "guild_prestige_first_tier"))
        assertEquals("2026-10-24", expansion.onPlaceholderRequest(player, "guild_prestige_last_date"))
        assertEquals("6", expansion.onPlaceholderRequest(player, "guild_prestige_max"))
        verify(exactly = 1) { configService.loadConfig() }
    }

    @Test
    fun `prestige leaderboard orders by tier then level and exposes prestige fields`() {
        val expansion = LumaGuildsExpansion()
        assertEquals("Vibe", expansion.onPlaceholderRequest(null, "top_prestige_1_name"))
        assertEquals("6", expansion.onPlaceholderRequest(null, "top_prestige_1_value"))
        assertEquals("WOT", expansion.onPlaceholderRequest(null, "top_prestige_2_name"))
        assertEquals("BoopGang", expansion.onPlaceholderRequest(null, "top_prestige_3_name"))
        assertEquals("PTC", expansion.onPlaceholderRequest(null, "top_prestige_4_name"))
        assertEquals("III", expansion.onPlaceholderRequest(null, "top_prestige_2_prestige_roman"))
        assertEquals("B6", expansion.onPlaceholderRequest(null, "top_prestige_1_prestige_badge"))
        assertEquals("", expansion.onPlaceholderRequest(null, "top_prestige_4_prestige_badge"))
    }

    @Test
    fun `player guild rank on the prestige board`() {
        val expansion = LumaGuildsExpansion()
        assertEquals("3", expansion.onPlaceholderRequest(member(sameTier), "guild_rank_prestige"))
    }
}
