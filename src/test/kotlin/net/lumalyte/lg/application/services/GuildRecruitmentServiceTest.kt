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

class GuildRecruitmentServiceTest {
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
    private val application = GuildApplication(UUID.randomUUID(), guild.id, player, "I build", now, now.plusSeconds(604800))
    private var current = application
    private var eligible = true

    private fun service(): GuildRecruitmentService {
        every { guilds.getGuild(guild.id) } returns guild
        every { directory.profile(guild.id) } returns profile
        every { members.getMemberLimit(guild.id) } returns 10
        every { members.hasPermission(actor, guild.id, RankPermission.MANAGE_RECRUITMENT) } returns true
        every { applications.get(application.id) } answers { current }
        every { applications.transition(any(), any()) } answers {
            if (firstArg<GuildApplication>() != current) {
                false
            } else {
                current = secondArg()
                true
            }
        }
        return GuildRecruitmentService(
            applications,
            directory,
            guilds,
            members,
            lfg,
            { RecruitmentPolicy() },
            { _, _ -> eligible },
            Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @Test fun `review is an offer and never calls admission`() {
        val service = service()
        service.review(actor, application.id, true, "")
        assertEquals(ApplicationStatus.OFFERED, current.status)
        verify(exactly = 0) { lfg.joinApprovedGuild(any(), any(), any()) }
    }

    @Test fun `ordinary member cannot read private inbox or review applications`() {
        val service = service()
        every { members.hasPermission(actor, guild.id, any()) } returns false
        assertFailsWith<IllegalArgumentException> { service.inbox(actor, guild.id) }
        assertFailsWith<IllegalArgumentException> { service.review(actor, application.id, true, "") }
        assertEquals(ApplicationStatus.SUBMITTED, current.status)
    }

    @Test fun `confirmation reserves once and repeated clicks cannot pay twice`() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        every { lfg.canJoinGuild(player, guild) } returns LfgJoinResult.Success("Eligible")
        every { lfg.joinApprovedGuild(player, guild, any()) } returns LfgJoinResult.Success("Joined")
        service.confirm(player, application.id, guild)
        assertEquals(ApplicationStatus.JOINED, current.status)
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild) }
        verify(exactly = 1) { lfg.joinApprovedGuild(player, guild, any()) }
    }

    @Test fun `changed fee rejected before reserving or invoking payment`() {
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

    @Test fun `blocked applicant cannot submit or accept an offer`() {
        val service = service()
        eligible = false
        assertFailsWith<IllegalArgumentException> { service.submit(player, guild.id, "I build") }
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild) }
    }

    @Test fun `changed quoted currency rejects confirmation before reserving`() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        val quote = net.lumalyte.lg.domain.values.JoinRequirement(100, true, "RAW_GOLD")
        every { lfg.getJoinRequirement(guild) } returns quote.copy(isPhysicalCurrency = false, currencyName = "Coins")
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild, quote) }
        assertEquals(ApplicationStatus.OFFERED, current.status)
        verify(exactly = 0) { lfg.joinApprovedGuild(any(), any(), any()) }
    }

    @Test fun `tentative membership event cannot complete an uncertain admission`() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime).confirm(player, now)
        every { members.getPlayerGuilds(player) } returns setOf(guild.id)
        every { applications.forApplicant(player) } answers { listOf(current) }
        service.closeOtherApplications(player, guild.id)
        assertEquals(ApplicationStatus.ADMISSION_PENDING, current.status)
    }

    @Test fun `uncertain admission remains reserved and cannot be retried`() {
        val service = service()
        current = application.offer(actor, now, RecruitmentPolicy().offerLifetime)
        every { lfg.canJoinGuild(player, guild) } returns LfgJoinResult.Success("Eligible")
        every { lfg.joinApprovedGuild(player, guild, any()) } throws IllegalStateException("uncertain payment")
        assertFailsWith<IllegalStateException> { service.confirm(player, application.id, guild) }
        assertEquals(ApplicationStatus.ADMISSION_PENDING, current.status)
        assertFailsWith<IllegalArgumentException> { service.confirm(player, application.id, guild) }
        verify(exactly = 1) { lfg.joinApprovedGuild(player, guild, any()) }
    }
}
