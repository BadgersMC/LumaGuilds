package net.lumalyte.lg.infrastructure.enthusiastaff

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.enthusia.staff.moderation.api.PunishmentCategory
import net.enthusia.staff.moderation.api.PunishmentLifecycleCursor
import net.enthusia.staff.moderation.api.PunishmentLifecycleEvent
import net.enthusia.staff.moderation.api.PunishmentLifecycleEventKind
import net.enthusia.staff.moderation.api.PunishmentLifecycleSource
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.MembershipHistory
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class EnthusiaStaffStrikeFeedTest {
    private val issuedAt = Instant.parse("2026-10-07T05:00:00Z")
    private val player = UUID.randomUUID()
    private val guild = UUID.randomUUID()

    @Test
    fun `native creation records provider-neutral strike against historical guild`() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.feedCursor(any()) } returns null
        val history = mockk<MembershipHistoryRepository>()
        every { history.getByPlayer(player) } returns listOf(
            mockk<MembershipHistory> {
                every { guildId } returns guild
                every { joinedAt } returns issuedAt.minusSeconds(60)
                every { departedAt } returns null
            },
        )
        val feed = feed(strikes, history)

        feed.applyEvent(event(PunishmentLifecycleSource.ENTHUSIA_STAFF, PunishmentLifecycleEventKind.CREATED))

        verify(exactly = 1) {
            strikes.recordExternalStrike(
                guild,
                player,
                "Player",
                "BAN",
                "Reason",
                "Moderator",
                issuedAt,
                "ENTHUSIA_STAFF",
                "90000000-0000-0000-0000-000000000001",
                issuedAt.plusSeconds(3600),
                true,
            )
        }
    }

    @Test
    fun `imported LiteBans creation is skipped but later change reconciles legacy row`() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.feedCursor(any()) } returns null
        val history = mockk<MembershipHistoryRepository>(relaxed = true)
        val feed = feed(strikes, history)

        feed.applyEvent(
            event(
                PunishmentLifecycleSource.LITEBANS,
                PunishmentLifecycleEventKind.CREATED,
                sourceId = "42",
            ),
        )
        verify(exactly = 0) { strikes.recordExternalStrike(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { strikes.reconcileLegacyStrike(any(), any(), any()) }

        feed.applyEvent(
            event(
                PunishmentLifecycleSource.LITEBANS,
                PunishmentLifecycleEventKind.CHANGED,
                sourceId = "42",
                active = false,
            ),
        )
        verify(exactly = 1) { strikes.reconcileLegacyStrike("BAN", 42L, false) }
    }

    @Test
    fun `native lifecycle change reconciles existing provider strike`() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.feedCursor(any()) } returns null
        val feed = feed(strikes, mockk(relaxed = true))

        feed.applyEvent(
            event(
                PunishmentLifecycleSource.ENTHUSIA_STAFF,
                PunishmentLifecycleEventKind.CHANGED,
                active = false,
            ),
        )

        verify(exactly = 1) {
            strikes.reconcileExternalStrike(
                "ENTHUSIA_STAFF",
                "90000000-0000-0000-0000-000000000001",
                false,
                issuedAt.plusSeconds(3600),
            )
        }
    }

    private fun feed(
        strikes: StrikeService,
        history: MembershipHistoryRepository,
    ): EnthusiaStaffStrikeFeed =
        EnthusiaStaffStrikeFeed(
            plugin = mockk<JavaPlugin>(relaxed = true),
            guildService = mockk<GuildService>(relaxed = true),
            strikeService = strikes,
            membershipHistoryRepository = history,
            configProvider = {
                StrikesConfig(
                    enabled = true,
                    countedTypes = listOf("WARN", "KICK", "MUTE", "BAN"),
                )
            },
        )

    private fun event(
        source: PunishmentLifecycleSource,
        kind: PunishmentLifecycleEventKind,
        sourceId: String = "90000000-0000-0000-0000-000000000001",
        active: Boolean = true,
    ) = PunishmentLifecycleEvent(
        PunishmentLifecycleCursor(issuedAt, UUID.randomUUID()),
        UUID.fromString("90000000-0000-0000-0000-000000000001"),
        "CASE000000000001",
        player,
        Optional.of("Player"),
        PunishmentCategory.BAN,
        kind,
        source,
        sourceId,
        issuedAt,
        Optional.of(issuedAt.plusSeconds(3600)),
        "Reason",
        Optional.of("Moderator"),
        active,
    )
}
