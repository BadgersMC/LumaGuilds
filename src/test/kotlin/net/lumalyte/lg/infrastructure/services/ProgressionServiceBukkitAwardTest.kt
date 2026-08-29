package net.lumalyte.lg.infrastructure.services

import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PermanentExperienceService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.entities.ExperienceTransaction
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.PeriodWindow
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ProgressionServiceBukkitAwardTest {

    @Test
    fun `legacy positive award delegates raw XP through capped permanent service`() {
        val awards = RecordingRepository()
        val config = MainConfig()
        val service = ProgressionServiceBukkit(
            progressionRepository = mockk<ProgressionRepository>(relaxed = true),
            guildRepository = mockk<GuildRepository>(relaxed = true),
            memberRepository = mockk<MemberRepository>(relaxed = true),
            configService = object : ConfigService {
                override fun loadConfig(): MainConfig = config
            },
            progressionConfigService = mockk(relaxed = true),
            plugin = mockk<Plugin>(relaxed = true),
            lang = mockk<LangService>(relaxed = true),
            permanentExperienceService = PermanentExperienceService(
                awards,
                object : PlaytimeActivityService {
                    override fun isXpBlocked(playerId: UUID): Boolean = false
                },
            ),
            experienceAwardRepository = awards,
        )
        val guildId = UUID.fromString("00000000-0000-0000-0000-000000000021")

        val leveledUp = service.awardExperience(guildId, 42, ExperienceSource.MOB_KILL)

        assertEquals(null, leveledUp)
        assertEquals(guildId, awards.request?.guildId)
        assertEquals(42, awards.request?.units)
        assertEquals(42, awards.requestedXp)
        assertEquals(6_000, awards.policy?.capXp)
    }

    @Test
    fun `atomic level-up result is returned to existing callers`() {
        val awards = RecordingRepository().apply { leveledUpTo = 2 }
        val service = serviceWith(awards)

        assertEquals(2, service.awardExperience(UUID.randomUUID(), 2_000, ExperienceSource.MOB_KILL))
    }

    @Test
    fun `player activity preserves actor and configured units`() {
        val awards = RecordingRepository()
        val service = serviceWith(awards)
        val guildId = UUID.randomUUID()
        val actorId = UUID.randomUUID()

        service.awardPlayerActivity(guildId, actorId, 3, ExperienceSource.DIAMOND_ORE)

        assertEquals(actorId, awards.request?.actorId)
        assertEquals(3, awards.request?.units)
        assertEquals(60, awards.requestedXp)
        assertEquals("ORE", awards.policy?.pool)
    }

    @Test
    fun `weekly quest sink forces reward outside source caps`() {
        val awards = RecordingRepository()
        val service = serviceWith(awards)

        QuestRewardSinkBukkit(service).awardExperience(
            UUID.randomUUID(),
            50_000,
        )

        assertEquals(ExperienceSource.WEEKLY_ACTIVITY, awards.request?.source)
        assertEquals(false, awards.policy?.isCapped)
        assertEquals(50_000, awards.requestedXp)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `daily cap reads configured shared-pool source policy`() {
        val service = serviceWith(RecordingRepository())
        assertEquals(18_000, service.getDailyCap(ExperienceSource.DIAMOND_ORE))
        assertEquals(0, service.getDailyCap(ExperienceSource.WEEKLY_ACTIVITY))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `daily source facade reports only transactions from today`() {
        val guildId = UUID.randomUUID()
        val today = Instant.now()
        val progressionRepository = mockk<ProgressionRepository>(relaxed = true)
        io.mockk.every { progressionRepository.getExperienceTransactions(guildId, 1000) } returns listOf(
            ExperienceTransaction(guildId = guildId, amount = 125, source = ExperienceSource.ENDER_DRAGON_KILL, timestamp = today),
            ExperienceTransaction(guildId = guildId, amount = 500, source = ExperienceSource.ENDER_DRAGON_KILL, timestamp = today.minus(2, ChronoUnit.DAYS)),
        )
        val service = serviceWith(RecordingRepository(), progressionRepository)

        val daily = service.getDailySourceXp(guildId)

        assertEquals(125, daily[ExperienceSource.ENDER_DRAGON_KILL])
    }

    private fun serviceWith(
        awards: RecordingRepository,
        progressionRepository: ProgressionRepository = mockk(relaxed = true),
    ): ProgressionServiceBukkit {
        val config = MainConfig()
        return ProgressionServiceBukkit(
            progressionRepository = progressionRepository,
            guildRepository = mockk<GuildRepository>(relaxed = true),
            memberRepository = mockk<MemberRepository>(relaxed = true),
            configService = object : ConfigService {
                override fun loadConfig(): MainConfig = config
            },
            progressionConfigService = mockk(relaxed = true),
            plugin = mockk<Plugin>(relaxed = true),
            lang = mockk<LangService>(relaxed = true),
            permanentExperienceService = PermanentExperienceService(
                awards,
                object : PlaytimeActivityService {
                    override fun isXpBlocked(playerId: UUID): Boolean = false
                },
            ),
            experienceAwardRepository = awards,
        )
    }

    private class RecordingRepository : ExperienceAwardRepository {
        var request: ExperienceAwardRequest? = null
        var policy: ExperiencePolicy? = null
        var requestedXp: Int? = null
        var leveledUpTo: Int? = null

        override fun awardAtomically(
            request: ExperienceAwardRequest,
            policy: ExperiencePolicy,
            requestedXp: Int,
            window: PeriodWindow?,
        ): ExperienceAwardResult {
            this.request = request
            this.policy = policy
            this.requestedXp = requestedXp
            return ExperienceAwardResult.Awarded(requestedXp, requestedXp, policy.isCapped, leveledUpTo)
        }
    }
}
