package net.lumalyte.lg.config

import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import java.time.Duration

/**
 * Configurable application expiry, offer lifetime and non-interrupting discovery cadence.
 * @property maximumApplications Maximum simultaneous active applications per player.
 * @property applicationDays Lifetime of an unreviewed application in days.
 * @property offerHours Time allowed for explicit applicant confirmation after approval.
 * @property onboardingDelaySeconds Delay before a guildless player's first discovery prompt.
 * @property reminderMinutes Optional repeat interval; zero disables repeating reminders.
 */
@Suppress("LibraryEntitiesShouldNotBePublic") // MainConfig exposes this configuration as part of its existing public API.
data class RecruitmentConfig(
    val maximumApplications: Int = 3,
    val applicationDays: Int = 7,
    val offerHours: Int = 48,
    val onboardingDelaySeconds: Int = 300,
    val reminderMinutes: Int = 0,
) {
    /** Produces bounded domain policy while failing startup/config reload for invalid values. */
    internal fun policy(): RecruitmentPolicy =
        RecruitmentPolicy(
            maxApplications = maximumApplications,
            applicationLifetime = Duration.ofDays(applicationDays.toLong()),
            offerLifetime = Duration.ofHours(offerHours.toLong()),
        )

    init {
        require(onboardingDelaySeconds in 30..3600 && reminderMinutes in 0..1440)
        require(reminderMinutes == 0 || reminderMinutes >= 30)
        policy()
    }
}
