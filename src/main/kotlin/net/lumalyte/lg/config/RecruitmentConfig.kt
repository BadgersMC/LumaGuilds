package net.lumalyte.lg.config

import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import java.time.Duration

private const val MIN_ONBOARDING_SECONDS = 30
private const val MAX_ONBOARDING_SECONDS = 3_600
private const val MIN_REMINDER_MINUTES = 30
private const val MAX_REMINDER_MINUTES = 1_440

/**
 * Configurable application expiry, offer lifetime and non-interrupting discovery cadence.
 * @property maximumApplications Maximum simultaneous active applications per player.
 * @property applicationDays Lifetime of an unreviewed application in days.
 * @property offerHours Time allowed for explicit applicant confirmation after approval.
 * @property onboardingDelaySeconds Delay before a guildless player's first discovery prompt.
 * @property reminderMinutes Optional repeat interval; zero disables repeating reminders.
 */
@Suppress(
    "LibraryEntitiesShouldNotBePublic",
) // MainConfig exposes this configuration as part of its existing public API.
class RecruitmentConfig(
    val maximumApplications: Int = 3,
    val applicationDays: Int = 7,
    val offerHours: Int = 48,
    val onboardingDelaySeconds: Int = 300,
    val reminderMinutes: Int = 0,
) {
    init {
        require(onboardingDelaySeconds in MIN_ONBOARDING_SECONDS..MAX_ONBOARDING_SECONDS)
        require(reminderMinutes in 0..MAX_REMINDER_MINUTES)
        require(reminderMinutes == 0 || reminderMinutes >= MIN_REMINDER_MINUTES)
        policy()
    }

    /** Produces bounded domain policy while failing startup/config reload for invalid values. */
    internal fun policy(): RecruitmentPolicy {
        return RecruitmentPolicy(
            maxApplications = maximumApplications,
            applicationLifetime = Duration.ofDays(applicationDays.toLong()),
            offerLifetime = Duration.ofHours(offerHours.toLong()),
        )
    }
}
