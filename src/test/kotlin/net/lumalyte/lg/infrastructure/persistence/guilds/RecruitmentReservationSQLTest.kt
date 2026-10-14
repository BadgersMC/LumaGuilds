package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.recruitment.GuildApplication
import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class RecruitmentReservationSQLTest : RewardSqlTestFixture() {
    @Test
    fun reservationReleaseIsAtomic() {
        val repository = GuildRecruitmentRepositorySQL(openStorage())
        val now = Instant.EPOCH
        val player = UUID.randomUUID()
        val policy = RecruitmentPolicy()
        val submitted = GuildApplication(
            UUID.randomUUID(),
            UUID.randomUUID(),
            player,
            "Builder",
            now,
            now.plus(policy.applicationLifetime),
        )
        assertNull(repository.submit(submitted, policy, now))
        val offer = submitted.offer(UUID.randomUUID(), now, policy.offerLifetime)
        assertTrue(repository.transition(submitted, offer))
        val pending = offer.confirm(player, now)
        assertTrue(repository.transition(offer, pending))
        val restored = pending.releaseAdmission(now)
        assertTrue(repository.transition(pending, restored))
        assertFalse(repository.transition(pending, restored))
        assertTrue(repository.transition(restored, restored.confirm(player, now)))
    }
}
