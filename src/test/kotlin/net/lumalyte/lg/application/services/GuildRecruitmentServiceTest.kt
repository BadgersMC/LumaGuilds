package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRecruitmentRepository
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.GuildApplication
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import net.lumalyte.lg.infrastructure.services.admissionFeedback
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val APPLICATION_WINDOW_SECONDS = 604_800L
private const val MEMBER_CAPACITY = 10
private const val JOIN_FEE_QUOTE = 100
private const val ELIGIBLE_MESSAGE = "Eligible"

internal class GuildRecruitmentServiceTest : GuildRecruitmentFixture() {
    @org.junit.jupiter.api.DisplayName("Recruiters can decline applicants who became ineligible")
    @Test
    fun declineIneligibleApplicant() {
        val service = service()
        eligible = false
        service.review(actor, application.id, false, "Recruitment unavailable")
        assertEquals(ApplicationStatus.DECLINED, current.status)
        verify(exactly = 0) { lfg.joinApprovedGuild(any(), any(), any()) }
    }

    @org.junit.jupiter.api.DisplayName("review is an offer and never calls admission")
    @Test
    fun reviewIsAnOfferAndNeverCal1() {
        val service = service()
        service.review(actor, application.id, true, "")
        assertEquals(ApplicationStatus.OFFERED, current.status)
        verify(exactly = 0) { lfg.joinApprovedGuild(any(), any(), any()) }
    }

    @org.junit.jupiter.api.DisplayName("ordinary member cannot read private inbox or review applications")
    @Test
    fun ordinaryMemberCannotReadPr2() {
        val service = service()
        every { members.hasPermission(actor, guild.id, any()) } returns false
        assertFailsWith<IllegalArgumentException> { service.inbox(actor, guild.id) }
        assertFailsWith<IllegalArgumentException> { service.review(actor, application.id, true, "") }
        assertEquals(ApplicationStatus.SUBMITTED, current.status)
    }

    @org.junit.jupiter.api.DisplayName("confirmation reserves once and repeated clicks cannot pay twice")
    @Test
    fun confirmationReservesOnceAn3() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        every { lfg.canJoinGuild(player, guild) } returns LfgJoinResult.Success(ELIGIBLE_MESSAGE)
        every { lfg.joinApprovedGuild(player, guild, any()) } returns LfgJoinResult.Success("Joined")
        service.confirm(player, application.id, guild)
        assertEquals(ApplicationStatus.JOINED, current.status)
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild) }
        verify(exactly = 1) { lfg.joinApprovedGuild(player, guild, any()) }
    }

    @org.junit.jupiter.api.DisplayName("changed fee rejected before reserving or invoking payment")
    @Test
    fun changedFeeRejectedBeforeRe4() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        assertFailsWith<IllegalArgumentException> {
            service.confirm(
                player,
                application.id,
                guild.copy(joinFeeEnabled = true, joinFeeAmount = 10),
            )
        }
        assertEquals(ApplicationStatus.OFFERED, current.status)
        verify(exactly = 0) { lfg.joinApprovedGuild(any(), any(), any()) }
    }

    @org.junit.jupiter.api.DisplayName("blocked applicant cannot submit or accept an offer")
    @Test
    fun blockedApplicantCannotSubm5() {
        val service = service()
        eligible = false
        assertFailsWith<IllegalArgumentException> { service.submit(player, guild.id, "I build") }
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild) }
    }

    @org.junit.jupiter.api.DisplayName("changed quoted currency rejects confirmation before reserving")
    @Test
    fun changedQuotedCurrencyRejec6() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        val quote = net.lumalyte.lg.domain.values.JoinRequirement(JOIN_FEE_QUOTE, true, "RAW_GOLD")
        every { lfg.getJoinRequirement(guild) } returns quote.copy(isPhysicalCurrency = false, currencyName = "Coins")
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild, quote) }
        assertEquals(ApplicationStatus.OFFERED, current.status)
        verify(exactly = 0) { lfg.joinApprovedGuild(any(), any(), any()) }
    }

    @org.junit.jupiter.api.DisplayName("tentative membership event cannot complete an uncertain admission")
    @Test
    fun tentativeMembershipEventCa7() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime).confirm(player, now)
        every { members.getPlayerGuilds(player) } returns setOf(guild.id)
        every { applications.forApplicant(player) } answers { listOf(current) }
        service.closeOtherApplications(player, guild.id)
        assertEquals(ApplicationStatus.ADMISSION_PENDING, current.status)
    }

    @org.junit.jupiter.api.DisplayName("uncertain admission remains reserved and cannot be retried")
    @Test
    fun uncertainAdmissionRemainsR8() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        every { lfg.canJoinGuild(player, guild) } returns LfgJoinResult.Success(ELIGIBLE_MESSAGE)
        every { lfg.joinApprovedGuild(player, guild, any()) } throws IllegalStateException("uncertain payment")
        assertFailsWith<IllegalStateException> { service.confirm(player, application.id, guild) }
        assertEquals(ApplicationStatus.ADMISSION_PENDING, current.status)
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild) }
        verify(exactly = 1) { lfg.joinApprovedGuild(player, guild, any()) }
    }
}

internal class GuildRecruitmentPaymentReservationTest : GuildRecruitmentFixture() {
    @Test
    fun rejectionRestoresOffer() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        val offer = current
        every { lfg.canJoinGuild(player, guild) } returns LfgJoinResult.Success(ELIGIBLE_MESSAGE)
        every { lfg.joinApprovedGuild(player, guild, any()) } returns
            net.lumalyte.lg.domain.gold.GuildGoldResult.Rejected(
                net.lumalyte.lg.domain.gold.GuildGoldRejection.FROZEN,
            ).admissionFeedback(guild, mockk(relaxed = true))
        service.confirm(player, application.id, guild)
        assertEquals(offer, current)
    }

    @Test
    fun refundAndUncertainty() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        val offer = current
        every { lfg.canJoinGuild(player, guild) } returns LfgJoinResult.Success(ELIGIBLE_MESSAGE)
        every { lfg.joinApprovedGuild(player, guild, any()) } returns LfgJoinResult.PaymentRefunded(UUID.randomUUID())
        service.confirm(player, application.id, guild)
        assertEquals(offer, current)
        every { lfg.joinApprovedGuild(player, guild, any()) } returns LfgJoinResult.PaymentUncertain(UUID.randomUUID())
        service.confirm(player, application.id, guild)
        assertEquals(ApplicationStatus.ADMISSION_PENDING, current.status)
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild) }
    }
}

internal abstract class GuildRecruitmentFixture {
    protected val now = Instant.parse("2026-10-14T12:00:00Z")
    protected val guild = Guild(UUID.randomUUID(), "Builders", createdAt = now)
    protected val player = UUID.randomUUID()
    protected val actor = UUID.randomUUID()
    protected val applications = mockk<GuildRecruitmentRepository>(relaxed = true)
    protected val directory = mockk<RecruitmentDirectoryRepository>(relaxed = true)
    protected val guilds = mockk<GuildService>(relaxed = true)
    protected val members = mockk<MemberService>(relaxed = true)
    protected val lfg = mockk<LfgService>(relaxed = true)
    protected val profile = RecruitmentProfile(guild.id, RecruitmentMode.IN_GAME)
    protected val application =
        GuildApplication(
            UUID.randomUUID(),
            guild.id,
            player,
            "I build",
            now,
            now.plusSeconds(APPLICATION_WINDOW_SECONDS),
        )
    protected var current = application
    protected var eligible = true

    protected fun service(): GuildRecruitmentService {
        every { guilds.getGuild(guild.id) } returns guild
        every { directory.profile(guild.id) } returns profile
        every { members.getMemberLimit(guild.id) } returns MEMBER_CAPACITY
        every { members.hasPermission(actor, guild.id, RankPermission.MANAGE_RECRUITMENT) } returns true
        applications.stubRecruitmentState(application.id, { current }, { current = it })
        return GuildRecruitmentService(
            applications,
            GuildRecruitmentListings(directory, guilds, members),
            guilds,
            members,
            lfg,
            GuildRecruitmentRules({ RecruitmentPolicy() }, { _, _ -> eligible }, Clock.fixed(now, ZoneOffset.UTC)),
        )
    }
}

private fun GuildRecruitmentRepository.stubRecruitmentState(
    id: UUID,
    read: () -> GuildApplication,
    write: (GuildApplication) -> Unit,
) {
    every { get(id) }.answers { read() }
    every { transition(any(), any()) }.answers {
        if (firstArg<GuildApplication>() != read()) {
            false
        } else {
            write(secondArg())
            true
        }
    }
}
