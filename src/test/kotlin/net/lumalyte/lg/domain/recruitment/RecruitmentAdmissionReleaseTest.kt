package net.lumalyte.lg.domain.recruitment

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

internal class RecruitmentAdmissionReleaseTest {
    @Test
    fun releaseKeepsDeadlineAndExpiresAnOldOffer() {
        val created = Instant.EPOCH
        val expires = created.plusSeconds(60)
        val pending = GuildApplication(
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
            pending.releaseAdmission(created.plusSeconds(30)),
        )
        assertEquals(pending.copy(status = ApplicationStatus.EXPIRED), pending.releaseAdmission(expires))
    }
}
