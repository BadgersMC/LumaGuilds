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
import net.lumalyte.lg.config.StrikesBackfillConfig
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.GuildStrike
import net.lumalyte.lg.domain.entities.MembershipHistory
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

// Independent feed scenarios share a deterministic attribution fixture.

/** Covers attribution, source identity, expiration and replay-safe historical failures. */
@Suppress("TooManyFunctions")
internal class EnthusiaStaffStrikeFeedTest {
    private val issuedAt = Instant.parse("2026-10-07T05:00:00Z")
    private val player = UUID.randomUUID()
    private val guild = UUID.randomUUID()

    /** A failed historical query cannot enable current-guild attribution. */
    @Test
    fun failedHistoryDoesNotFallback() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileLegacyStrike(BAN_TYPE, LEGACY_ID, true) } returns false
        val history = mockk<MembershipHistoryRepository>()
        every { history.getByPlayer(player) } throws IllegalStateException("storage unavailable")
        val guildService = mockk<GuildService>(relaxed = true)
        val config = StrikesConfig(enabled = true).apply { backfill.fallbackToCurrentGuild = true }
        val adapter = feed(strikes, history, guildService, config)

        assertFailsWith<IllegalStateException> {
            adapter.applyEvent(event(PunishmentLifecycleSource.LITEBANS, sourceId = LEGACY_ID.toString()))
        }
        verify(exactly = 0) { guildService.getPlayerGuilds(any()) }
        verify(exactly = 0) { strikes.recordStrike(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    /** Native snapshot creates provider strike when none exists. */
    @DisplayName("native snapshot creates provider strike when none exists")
    @Test
    fun nativeCreatesStrike() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileExternalStrike(any(), any(), any(), any()) } returns false
        val history = historicalMembership()
        val feed = feed(strikes, history)

        feed.applyEvent(event(PunishmentLifecycleSource.ENTHUSIA_STAFF))

        verify(exactly = 1) {
            strikes.recordExternalStrike(expectedNativeStrike())
        }
    }

    /** Native unattributable punishment stays skipped even if player later has a current guild. */
    @DisplayName("native unattributable punishment stays skipped even if player later has a current guild")
    @Test
    fun nativeSkipsMissingHistory() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileExternalStrike(any(), any(), any(), any()) } returns false
        val history = mockk<MembershipHistoryRepository>()
        every { history.getByPlayer(player) } returns emptyList()
        val guildService = mockk<GuildService>()
        every { guildService.getPlayerGuilds(player) } returns
            setOf(mockk<net.lumalyte.lg.domain.entities.Guild>(relaxed = true))
        val feed = feed(strikes, history, guildService)

        feed.applyEvent(event(PunishmentLifecycleSource.ENTHUSIA_STAFF))

        verify(exactly = 0) { guildService.getPlayerGuilds(any()) }
        verify(exactly = 0) {
            strikes.recordExternalStrike(any())
        }
    }

    /** Snapshot expiration is recomputed when applied. */
    @DisplayName("snapshot expiration is recomputed when applied")
    @Test
    fun snapshotExpiresAtApply() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileExternalStrike(any(), any(), any(), any()) } returns true
        val feed = feed(strikes, mockk(relaxed = true))

        feed.applyEvent(
            event(
                PunishmentLifecycleSource.ENTHUSIA_STAFF,
                active = true,
                expiresAt = Instant.now().minusSeconds(1),
            ),
        )

        verify(exactly = 1) {
            strikes.reconcileExternalStrike(
                PROVIDER,
                SANCTION_ID,
                false,
                any(),
            )
        }
    }

    /** Native snapshot reconciles existing strike without guild lookup. */
    @DisplayName("native snapshot reconciles existing strike without guild lookup")
    @Test
    fun nativeReconcilesWithoutLookup() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileExternalStrike(any(), any(), any(), any()) } returns true
        val history = mockk<MembershipHistoryRepository>(relaxed = true)
        val feed = feed(strikes, history)

        feed.applyEvent(event(PunishmentLifecycleSource.ENTHUSIA_STAFF, active = false))

        verify(exactly = 1) {
            strikes.reconcileExternalStrike(
                PROVIDER,
                SANCTION_ID,
                false,
                FUTURE_EXPIRY,
            )
        }
        verify(exactly = 0) { history.getByPlayer(any()) }
        verify(exactly = 0) {
            strikes.recordExternalStrike(any())
        }
    }

    /** Imported litebans snapshot reconciles existing legacy row without recreating it. */
    @DisplayName("imported LiteBans snapshot reconciles existing legacy row without recreating it")
    @Test
    fun legacyReconcilesWithoutCreate() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileLegacyStrike(BAN_TYPE, LEGACY_ID, false) } returns true
        val history = mockk<MembershipHistoryRepository>(relaxed = true)
        val feed = feed(strikes, history)

        feed.applyEvent(
            event(
                PunishmentLifecycleSource.LITEBANS,
                sourceId = LEGACY_ID.toString(),
                active = false,
            ),
        )

        verify(exactly = 1) { strikes.reconcileLegacyStrike(BAN_TYPE, LEGACY_ID, false) }
        verify(exactly = 0) { history.getByPlayer(any()) }
        verify(exactly = 0) {
            strikes.recordStrike(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    /** Imported litebans snapshot does not repair missing history when backfill is disabled. */
    @DisplayName("imported LiteBans snapshot does not repair missing history when backfill is disabled")
    @Test
    fun legacyRespectsBackfillToggle() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileLegacyStrike(BAN_TYPE, LEGACY_ID, true) } returns false
        val history = mockk<MembershipHistoryRepository>(relaxed = true)
        val config =
            StrikesConfig(
                enabled = true,
                countedTypes = listOf("WARN", "KICK", "MUTE", BAN_TYPE),
                backfill = StrikesBackfillConfig(enabled = false),
            )
        val feed = feed(strikes, history, config = config)

        feed.applyEvent(event(PunishmentLifecycleSource.LITEBANS, sourceId = LEGACY_ID.toString()))

        verify(exactly = 1) { strikes.reconcileLegacyStrike(BAN_TYPE, LEGACY_ID, true) }
        verify(exactly = 0) { history.getByPlayer(any()) }
        verify(exactly = 0) {
            strikes.recordStrike(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    /** Imported litebans snapshot repairs a missing historical row without litebans runtime. */
    @DisplayName("imported LiteBans snapshot repairs a missing historical row without LiteBans runtime")
    @Test
    fun legacyBackfillsHistoricalRow() {
        val strikes = mockk<StrikeService>(relaxed = true)
        every { strikes.reconcileLegacyStrike(BAN_TYPE, LEGACY_ID, true) } returns false
        val history = historicalMembership()
        val feed = feed(strikes, history)

        feed.applyEvent(event(PunishmentLifecycleSource.LITEBANS, sourceId = LEGACY_ID.toString()))

        verify(exactly = 1) {
            strikes.recordStrike(
                guild,
                player,
                SUBJECT_NAME,
                BAN_TYPE,
                PUBLIC_REASON,
                ACTOR_NAME,
                issuedAt,
                LEGACY_ID,
                true,
            )
        }
    }

    private fun historicalMembership(): MembershipHistoryRepository {
        val history = mockk<MembershipHistoryRepository>()
        every { history.getByPlayer(player) } returns
            listOf(
            mockk<MembershipHistory> {
                every { guildId } returns guild
                every { joinedAt } returns issuedAt.minusSeconds(STINT_AGE_SECONDS)
                every { departedAt } returns null
            },
        )
        return history
    }

    private fun expectedNativeStrike(): GuildStrike {
        return GuildStrike(
            guildId = guild, playerUuid = player, playerName = SUBJECT_NAME, punishmentType = BAN_TYPE,
            reason = PUBLIC_REASON, executorName = ACTOR_NAME, issuedAt = issuedAt, sourceProvider = PROVIDER,
            sourcePunishmentId = SANCTION_ID, expiresAt = FUTURE_EXPIRY,
        )
    }

    private fun feed(
        strikes: StrikeService,
        history: MembershipHistoryRepository,
        guildService: GuildService = mockk(relaxed = true),
        config: StrikesConfig = defaultFeedConfig(),
    ): EnthusiaStaffStrikeFeed {
        return EnthusiaStaffStrikeFeed(mockk<JavaPlugin>(relaxed = true), guildService, strikes, history) {
            config
        }
    }

    private fun event(
        source: PunishmentLifecycleSource,
        sourceId: String = SANCTION_ID,
        active: Boolean = true,
        expiresAt: Instant = FUTURE_EXPIRY,
    ): PunishmentLifecycleEvent =
        PunishmentLifecycleEvent(
            UUID.fromString(SANCTION_ID),
            "CASE000000000001",
            player,
            Optional.of(SUBJECT_NAME),
            PunishmentCategory.BAN,
            source,
            sourceId,
            issuedAt,
            Optional.of(expiresAt),
            PUBLIC_REASON,
            Optional.of(ACTOR_NAME),
            active,
        )

    private companion object {
        const val SUBJECT_NAME = "Player"
        const val BAN_TYPE = "BAN"
        const val PUBLIC_REASON = "Reason"
        const val ACTOR_NAME = "Moderator"
        const val PROVIDER = "ENTHUSIA_STAFF"
        const val LEGACY_ID = 42L
        const val STINT_AGE_SECONDS = 60L
        private const val SANCTION_ID = "90000000-0000-0000-0000-000000000001"
        private val FUTURE_EXPIRY = Instant.parse("2099-01-01T00:00:00Z")
    }
}

private fun defaultFeedConfig(): StrikesConfig =
    StrikesConfig(enabled = true, countedTypes = listOf("WARN", "KICK", "MUTE", "BAN"))
