package net.lumalyte.lg.infrastructure.enthusiastaff

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.enthusia.staff.moderation.api.PunishmentCategory
import net.enthusia.staff.moderation.api.PunishmentLifecycleEvent
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
    fun `native snapshot creates provider strike when none exists`() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileExternalStrike(any(), any(), any(), any()) } returns false
        val history = mockk<MembershipHistoryRepository>()
        every { history.getByPlayer(player) } returns listOf(
            mockk<MembershipHistory> {
                every { guildId } returns guild
                every { joinedAt } returns issuedAt.minusSeconds(60)
                every { departedAt } returns null
            },
        )
        val feed = feed(strikes, history)

        feed.applyEvent(event(PunishmentLifecycleSource.ENTHUSIA_STAFF))

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
                SANCTION_ID,
                issuedAt.plusSeconds(3600),
                true,
            )
        }
    }

    @Test
    fun `native unattributable punishment stays skipped even if player later has a current guild`() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileExternalStrike(any(), any(), any(), any()) } returns false
        val history = mockk<MembershipHistoryRepository>()
        every { history.getByPlayer(player) } returns emptyList()
        val guildService = mockk<GuildService>()
        every { guildService.getPlayerGuilds(player) } returns setOf(mockk<net.lumalyte.lg.domain.entities.Guild>(relaxed = true))
        val feed = feed(strikes, history, guildService)

        feed.applyEvent(event(PunishmentLifecycleSource.ENTHUSIA_STAFF))

        verify(exactly = 0) { guildService.getPlayerGuilds(any()) }
        verify(exactly = 0) {
            strikes.recordExternalStrike(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `native snapshot reconciles existing strike without guild lookup`() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileExternalStrike(any(), any(), any(), any()) } returns true
        val history = mockk<MembershipHistoryRepository>(relaxed = true)
        val feed = feed(strikes, history)

        feed.applyEvent(event(PunishmentLifecycleSource.ENTHUSIA_STAFF, active = false))

        verify(exactly = 1) {
            strikes.reconcileExternalStrike(
                "ENTHUSIA_STAFF",
                SANCTION_ID,
                false,
                issuedAt.plusSeconds(3600),
            )
        }
        verify(exactly = 0) { history.getByPlayer(any()) }
        verify(exactly = 0) {
            strikes.recordExternalStrike(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `imported LiteBans snapshot reconciles existing legacy row without recreating it`() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileLegacyStrike("BAN", 42L, false) } returns true
        val history = mockk<MembershipHistoryRepository>(relaxed = true)
        val feed = feed(strikes, history)

        feed.applyEvent(
            event(
                PunishmentLifecycleSource.LITEBANS,
                sourceId = "42",
                active = false,
            ),
        )

        verify(exactly = 1) { strikes.reconcileLegacyStrike("BAN", 42L, false) }
        verify(exactly = 0) { history.getByPlayer(any()) }
        verify(exactly = 0) {
            strikes.recordStrike(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `imported LiteBans snapshot repairs a missing historical row without LiteBans runtime`() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileLegacyStrike("BAN", 42L, true) } returns false
        val history = mockk<MembershipHistoryRepository>()
        every { history.getByPlayer(player) } returns listOf(
            mockk<MembershipHistory> {
                every { guildId } returns guild
                every { joinedAt } returns issuedAt.minusSeconds(60)
                every { departedAt } returns null
            },
        )
        val feed = feed(strikes, history)

        feed.applyEvent(event(PunishmentLifecycleSource.LITEBANS, sourceId = "42"))

        verify(exactly = 1) {
            strikes.recordStrike(
                guild,
                player,
                "Player",
                "BAN",
                "Reason",
                "Moderator",
                issuedAt,
                42L,
                true,
            )
        }
    }

    private fun feed(
        strikes: StrikeService,
        history: MembershipHistoryRepository,
        guildService: GuildService = mockk(relaxed = true),
    ): EnthusiaStaffStrikeFeed =
        EnthusiaStaffStrikeFeed(
            plugin = mockk<JavaPlugin>(relaxed = true),
            guildService = guildService,
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
        sourceId: String = SANCTION_ID,
        active: Boolean = true,
    ) = PunishmentLifecycleEvent(
        UUID.fromString(SANCTION_ID),
        "CASE000000000001",
        player,
        Optional.of("Player"),
        PunishmentCategory.BAN,
        source,
        sourceId,
        issuedAt,
        Optional.of(issuedAt.plusSeconds(3600)),
        "Reason",
        Optional.of("Moderator"),
        active,
    )

    companion object {
        private const val SANCTION_ID = "90000000-0000-0000-0000-000000000001"
    }
}
