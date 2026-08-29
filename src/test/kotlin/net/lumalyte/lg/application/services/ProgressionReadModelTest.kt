package net.lumalyte.lg.application.services

import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.ExperienceAwardRepository
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.infrastructure.services.ProgressionConfigService
import net.lumalyte.lg.infrastructure.services.ProgressionServiceBukkit
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ProgressionReadModelTest {
    private val guildId = UUID.randomUUID()
    private val instant = Instant.parse("2026-08-29T12:00:00Z")
    private val usage = RecordingUsageRepository(mapOf("ORE" to 50))
    private val service = ProgressionServiceBukkit(
        progressionRepository = mockk<ProgressionRepository>(relaxed = true),
        guildRepository = mockk<GuildRepository>(relaxed = true),
        memberRepository = mockk<MemberRepository>(relaxed = true),
        configService = object : ConfigService { override fun loadConfig() = MainConfig() },
        progressionConfigService = mockk<ProgressionConfigService>(relaxed = true),
        plugin = mockk<Plugin>(relaxed = true),
        lang = mockk<LangService>(relaxed = true),
        permanentExperienceService = PermanentExperienceService(usage, object : PlaytimeActivityService {
            override fun isXpBlocked(playerId: UUID) = false
        }),
        experienceAwardRepository = usage,
    )

    @Test
    fun `shared ore sources show one shared allowance`() {
        val views = service.getSourceUsage(guildId, instant)

        assertEquals(1, views.count { it.pool == "ORE" })
        assertEquals(18_000, views.single { it.pool == "ORE" }.capXp)
        assertEquals(17_950, views.single { it.pool == "ORE" }.remainingXp)
    }

    @Test
    fun `weekly activity is shown as unlimited`() {
        val view = service.getSourceUsage(guildId, instant)
            .single { it.source == ExperienceSource.WEEKLY_ACTIVITY }

        assertEquals(null, view.capXp)
        assertEquals(null, view.remainingXp)
        assertEquals(null, view.resetsAt)
    }

    private class RecordingUsageRepository(
        private val usageByPool: Map<String, Int>,
    ) : ExperienceAwardRepository {
        override fun awardAtomically(
            request: ExperienceAwardRequest,
            policy: ExperiencePolicy,
            requestedXp: Int,
            window: PeriodWindow?,
        ): ExperienceAwardResult = ExperienceAwardResult.Awarded(requestedXp, requestedXp, policy.isCapped)

        override fun getAwardedXpByPool(guildId: UUID, at: Instant): Map<String, Int> = usageByPool
    }
}
