package net.lumalyte.lg.infrastructure.placeholders

import net.lumalyte.lg.application.services.SourceUsageView
import net.lumalyte.lg.config.ChapterTwoExperiencePolicies
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperienceSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class LumaGuildsExpansionProgressionTest {
    private val policies = ChapterTwoExperiencePolicies.defaults()
    private val views = listOf(
        SourceUsageView(
            ExperienceSource.COAL_ORE,
            "ORE",
            CapPeriod.DAILY,
            50,
            18_000,
            17_950,
            Instant.parse("2026-08-30T00:00:00Z"),
        ),
        SourceUsageView(
            ExperienceSource.WEEKLY_ACTIVITY,
            "WEEKLY_ACTIVITY",
            CapPeriod.UNLIMITED,
            0,
            null,
            null,
            null,
        ),
    )

    @Test
    fun `shared source placeholders resolve through their configured pool`() {
        assertEquals("50", LumaGuildsExpansion.sourceUsageValue("source_diamond_ore_used", views, policies))
        assertEquals("17950", LumaGuildsExpansion.sourceUsageValue("source_iron_ore_remaining", views, policies))
    }

    @Test
    fun `unlimited and invalid source placeholders fall back safely`() {
        assertEquals("", LumaGuildsExpansion.sourceUsageValue("source_weekly_activity_remaining", views, policies))
        assertEquals("", LumaGuildsExpansion.sourceUsageValue("source_not_real_used", views, policies))
    }
}
