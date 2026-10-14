package net.lumalyte.lg.domain.recruitment

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

private const val OFFER_SECONDS = 60L
private const val BEFORE_EXPIRY_SECONDS = 30L

internal class RecruitmentAdmissionReleaseTest {
    @Test
    fun releaseKeepsDeadline() {
        val created = Instant.EPOCH
        val expires = created.plusSeconds(OFFER_SECONDS)
        val pending =
            GuildApplication(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Builder",
                created,
                expires,
                ApplicationStatus.ADMISSION_PENDING,
            )
        assertEquals(
            pending.copy(status = ApplicationStatus.OFFERED),
            pending.releaseAdmission(created.plusSeconds(BEFORE_EXPIRY_SECONDS)),
        )
        assertEquals(pending.copy(status = ApplicationStatus.EXPIRED), pending.releaseAdmission(expires))
    }
}
