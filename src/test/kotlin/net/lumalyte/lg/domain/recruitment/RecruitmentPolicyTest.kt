package net.lumalyte.lg.domain.recruitment

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecruitmentPolicyTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val guild = UUID.randomUUID()
    private val player = UUID.randomUUID()
    private val recruiter = UUID.randomUUID()
    private val policy = RecruitmentPolicy()

    private fun application() =
        GuildApplication(
            UUID.randomUUID(),
            guild,
            player,
            "I enjoy building",
            now,
            now.plus(Duration.ofDays(7)),
        )

    @Test fun `legacy open flag migrates without guessing application mode`() {
        assertEquals(RecruitmentMode.JOIN_IMMEDIATELY, RecruitmentProfile.legacy(guild, true).mode)
        assertEquals(RecruitmentMode.CLOSED, RecruitmentProfile.legacy(guild, false).mode)
    }

    @Test fun `Discord mode requires a strict invite and instructions`() {
        assertFailsWith<IllegalArgumentException> { RecruitmentProfile(guild, RecruitmentMode.DISCORD) }
        assertFailsWith<IllegalArgumentException> {
            RecruitmentProfile(
                guild,
                RecruitmentMode.DISCORD,
                discordInvite = "https://evil.example/discord.gg/test",
                discordInstructions = "Open a ticket",
            )
        }
        for (link in listOf("https://discord.gg/Example-1", "https://discord.com/invite/Example-1/")) {
            assertEquals(
                link,
                RecruitmentProfile(
                    guild,
                    RecruitmentMode.DISCORD,
                    discordInvite = link,
                    discordInstructions = "Open a ticket",
                ).discordInvite,
            )
        }
        for (link in listOf(
            "http://discord.gg/test",
            "https://discord.gg/test?redirect=x",
            "https://discord.gg/test\n",
            "https://discord.com.evil/invite/test",
        )) {
            assertFalse(DiscordInvite.isValid(link), link)
        }
    }

    @Test fun `one active application per guild and at most three per player`() {
        val a = application()
        assertEquals(SubmissionDenial.DUPLICATE, policy.canSubmit(guild, player, listOf(a), now))
        val other = (1..3).map { a.copy(id = UUID.randomUUID(), guildId = UUID.randomUUID()) }
        assertEquals(SubmissionDenial.LIMIT, policy.canSubmit(guild, player, other, now))
        assertNull(policy.canSubmit(guild, UUID.randomUUID(), other, now))
        assertNull(policy.canSubmit(guild, player, listOf(a.copy(expiresAt = now)), now))
    }

    @Test fun `approval is an offer requiring applicant confirmation not membership`() {
        val offered = application().offer(recruiter, now, policy.offerLifetime)
        assertEquals(ApplicationStatus.OFFERED, offered.status)
        assertEquals(recruiter, offered.reviewedBy)
        assertEquals(now.plus(Duration.ofHours(48)), offered.expiresAt)
        assertFailsWith<IllegalArgumentException> { offered.confirm(UUID.randomUUID(), now) }
        assertEquals(ApplicationStatus.ADMISSION_PENDING, offered.confirm(player, now).status)
        assertFailsWith<IllegalArgumentException> { offered.confirm(player, offered.expiresAt) }
    }

    @Test fun `expired withdrawn and declined applications cannot be offered`() {
        val a = application()
        assertFailsWith<IllegalArgumentException> { a.offer(recruiter, a.expiresAt, policy.offerLifetime) }
        assertFailsWith<IllegalArgumentException> { a.withdraw(player, now).offer(recruiter, now, policy.offerLifetime) }
        assertFailsWith<IllegalArgumentException> { a.decline(recruiter, "Not a fit", now).offer(recruiter, now, policy.offerLifetime) }
        assertFailsWith<IllegalArgumentException> { a.withdraw(recruiter, now) }
    }

    @Test fun `uncertain admissions stay closed to repeated confirmation`() {
        val pending = application().offer(recruiter, now, policy.offerLifetime).confirm(player, now)
        assertTrue(pending.isActive(now))
        assertTrue(pending.isActive(now.plus(Duration.ofDays(20))))
        assertFailsWith<IllegalArgumentException> { pending.confirm(player, now) }
        assertEquals(ApplicationStatus.JOINED, pending.completeAdmission().status)
        assertFailsWith<IllegalArgumentException> { pending.withdraw(player, now) }
    }

    @Test fun `config bounds and empty answers are rejected`() {
        assertFailsWith<IllegalArgumentException> { RecruitmentPolicy(maxApplications = 0) }
        assertFailsWith<IllegalArgumentException> { RecruitmentPolicy(offerLifetime = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { application().copy(answers = " ") }
    }
}
