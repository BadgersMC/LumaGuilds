package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.GuildApplication
import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import net.lumalyte.lg.domain.recruitment.SubmissionDenial
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class GuildRecruitmentRepositorySQLTest : RewardSqlTestFixture() {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val player = UUID.randomUUID()
    private val guild = UUID.randomUUID()
    private val policy = RecruitmentPolicy()

    private fun application() = GuildApplication(
        UUID.randomUUID(),
        guild,
        player,
        "I enjoy building",
        now,
        now.plus(policy.applicationLifetime),
    )

    @org.junit.jupiter.api.DisplayName("applications and decision actor survive a new repository instance")
    @Test
    fun applicationsAndDecisionAct1() {
        val storage = openStorage()
        val repository = GuildRecruitmentRepositorySQL(storage)
        val a = application()
        assertNull(repository.submit(a, policy, now))
        val offered = a.offer(UUID.randomUUID(), now, policy.offerLifetime)
        assertTrue(repository.transition(a, offered))
        val reopened = GuildRecruitmentRepositorySQL(storage)
        assertEquals(offered, reopened.get(a.id))
        assertEquals(listOf(offered), reopened.forApplicant(player))
        assertEquals(listOf(offered), reopened.forGuild(guild))
        assertTrue(reopened.forApplicant(UUID.randomUUID()).isEmpty())
    }

    @org.junit.jupiter.api.DisplayName("stale decisions and repeated admission reservation cannot overwrite state")
    @Test
    fun staleDecisionsAndRepeatedA2() {
        val repository = GuildRecruitmentRepositorySQL(openStorage())
        val a = application()
        repository.submit(a, policy, now)
        val offered = a.offer(UUID.randomUUID(), now, policy.offerLifetime)
        assertTrue(repository.transition(a, offered))
        assertFalse(repository.transition(a, a.decline(UUID.randomUUID(), "No", now)))
        val admitting = offered.confirm(player, now)
        assertTrue(repository.transition(offered, admitting))
        assertFalse(repository.transition(offered, admitting))
        assertEquals(admitting, repository.get(a.id))
    }

    @org.junit.jupiter.api.DisplayName("concurrent submissions enforce duplicate and global limits across instances")
    @Test
    fun concurrentSubmissionsEnfor3() {
        val storage = openStorage()
        val repositories = listOf(GuildRecruitmentRepositorySQL(storage), GuildRecruitmentRepositorySQL(storage))
        val attempts =
            (0..7).map { n ->
                CompletableFuture.supplyAsync {
                    repositories[n % 2].submit(application(), policy, now)
                }
            }
        assertEquals(1, attempts.count { it.get(20, TimeUnit.SECONDS) == null })
        for (n in 1..2) assertNull(repositories[0].submit(application().copy(guildId = UUID.randomUUID()), policy, now))
        assertEquals(
            SubmissionDenial.LIMIT,
            repositories[1].submit(application().copy(guildId = UUID.randomUUID()), policy, now),
        )
    }

    @org.junit.jupiter.api.DisplayName("expired applications permit reapplication but uncertain admission does not")
    @Test
    fun expiredApplicationsPermitR4() {
        val repository = GuildRecruitmentRepositorySQL(openStorage())
        val a = application()
        repository.submit(a, policy, now)
        assertNull(
            repository.submit(
                application().copy(createdAt = a.expiresAt, expiresAt = a.expiresAt.plus(policy.applicationLifetime)),
                policy,
                a.expiresAt,
            ),
        )
        val b = repository.forApplicant(player).first { it.id != a.id }
        val offered = b.offer(UUID.randomUUID(), b.createdAt, policy.offerLifetime)
        repository.transition(b, offered)
        repository.transition(offered, offered.confirm(player, b.createdAt))
        assertEquals(
            SubmissionDenial.DUPLICATE,
            repository.submit(
                application().copy(
                    createdAt = b.expiresAt,
                    expiresAt = b.expiresAt.plus(policy.applicationLifetime),
                ),
                policy,
                b.expiresAt,
            ),
        )
    }

    @org.junit.jupiter.api.DisplayName("database write failure never reports successful submission")
    @Test
    fun databaseWriteFailureNeverR5() {
        val storage = openStorage()
        val repository = GuildRecruitmentRepositorySQL(storage)
        storage.connection.executeUpdate("DROP TABLE guild_recruitment_applications")
        assertFails { repository.submit(application(), policy, now) }
    }

    @org.junit.jupiter.api.DisplayName("nanosecond clock values do not cause false stale comparisons after persistence")
    @Test
    fun nanosecondClockValuesDoNot6() {
        val repository = GuildRecruitmentRepositorySQL(openStorage())
        val precise = now.plusNanos(123456)
        val a = application().copy(createdAt = precise, expiresAt = precise.plus(policy.applicationLifetime))
        repository.submit(a, policy, precise)
        val offered = a.offer(UUID.randomUUID(), precise, policy.offerLifetime)
        assertTrue(repository.transition(a, offered))
        assertTrue(repository.transition(offered, offered.confirm(player, precise)))
    }

    @org.junit.jupiter.api.DisplayName("only one guild admission can be reserved for the same applicant")
    @Test
    fun onlyOneGuildAdmissionCanBe7() {
        val repository = GuildRecruitmentRepositorySQL(openStorage())
        val first = application()
        val second = application().copy(guildId = UUID.randomUUID())
        repository.submit(first, policy, now)
        repository.submit(second, policy, now)
        val offer1 = first.offer(UUID.randomUUID(), now, policy.offerLifetime)
        val offer2 = second.offer(UUID.randomUUID(), now, policy.offerLifetime)
        repository.transition(first, offer1)
        repository.transition(second, offer2)
        assertTrue(repository.transition(offer1, offer1.confirm(player, now)))
        assertFalse(repository.transition(offer2, offer2.confirm(player, now)))
        assertEquals(ApplicationStatus.OFFERED, repository.get(second.id)?.status)
    }
}
