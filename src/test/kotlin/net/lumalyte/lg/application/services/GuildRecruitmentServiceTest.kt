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

internal class GuildRecruitmentServiceTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val guild = Guild(UUID.randomUUID(), "Builders", createdAt = now)
    private val player = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val applications = mockk<GuildRecruitmentRepository>(relaxed = true)
    private val directory = mockk<RecruitmentDirectoryRepository>(relaxed = true)
    private val guilds = mockk<GuildService>(relaxed = true)
    private val members = mockk<MemberService>(relaxed = true)
    private val lfg = mockk<LfgService>(relaxed = true)
    private val profile = RecruitmentProfile(guild.id, RecruitmentMode.IN_GAME)
    private val application =
        GuildApplication(
            UUID.randomUUID(),
            guild.id,
            player,
            "I build",
            now,
            now.plusSeconds(APPLICATION_WINDOW_SECONDS),
        )
    private var current = application
    private var eligible = true

    @org.junit.jupiter.api.DisplayName("Recruiters can decline applicants who became ineligible")
    @Test
    fun declineIneligibleApplicant() {
        val service = service()
        eligible = false
        service.review(actor, application.id, false, "Recruitment unavailable")
        assertEquals(ApplicationStatus.DECLINED, current.status)
        verify(exactly = 0) { lfg.joinApprovedGuild(any(), any(), any()) }
    }

    private fun service(): GuildRecruitmentService {
        stubReads()
        stubTransitions()
        return GuildRecruitmentService(
            applications,
            GuildRecruitmentListings(directory, guilds, members),
            guilds,
            members,
            lfg,
            GuildRecruitmentRules({ RecruitmentPolicy() }, { _, _ -> eligible }, Clock.fixed(now, ZoneOffset.UTC)),
        )
    }

    private fun stubReads() {
        every { guilds.getGuild(guild.id) } returns guild
        every { directory.profile(guild.id) } returns profile
        every { members.getMemberLimit(guild.id) } returns MEMBER_CAPACITY
        every { members.hasPermission(actor, guild.id, RankPermission.MANAGE_RECRUITMENT) } returns true
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
        every { lfg.canJoinGuild(player, guild) } returns LfgJoinResult.Success("Eligible")
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
        every { lfg.canJoinGuild(player, guild) } returns LfgJoinResult.Success("Eligible")
        every { lfg.joinApprovedGuild(player, guild, any()) } throws IllegalStateException("uncertain payment")
        assertFailsWith<IllegalStateException> { service.confirm(player, application.id, guild) }
        assertEquals(ApplicationStatus.ADMISSION_PENDING, current.status)
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild) }
        verify(exactly = 1) { lfg.joinApprovedGuild(player, guild, any()) }
    }
    private fun stubTransitions() {
        every { applications.get(application.id) } answers { current }
        every { applications.transition(any(), any()) } answers {
            transitionCurrent(firstArg(), secondArg())
        }
    }

    private fun transitionCurrent(expected: GuildApplication, updated: GuildApplication): Boolean {
        if (expected != current) return false
        current = updated
        return true
    }
}
