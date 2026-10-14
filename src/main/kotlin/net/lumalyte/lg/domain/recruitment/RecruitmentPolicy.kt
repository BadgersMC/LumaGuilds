package net.lumalyte.lg.domain.recruitment

import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val MAX_PITCH = 300
private const val MAX_REQUIREMENTS = 500
private const val MAX_TIMES = 100
private const val MAX_LANGUAGE = 40
private const val MAX_INTERESTS = 8
private const val MAX_INTEREST_LENGTH = 32
private const val MAX_ANSWERS = 2_000
private const val MAX_REASON = 300
private const val MAX_APPLICATIONS = 20
private const val MAX_APPLICATION_DAYS = 90L
private const val MAX_OFFER_DAYS = 30L

private const val DEFAULT_APPLICATION_DAYS = 7L
private const val DEFAULT_OFFER_HOURS = 48L

/** Joining method; Discord application state is deliberately never inferred in game. */
internal enum class RecruitmentMode { JOIN_IMMEDIATELY, IN_GAME, DISCORD, CLOSED }

/** Recruitment fields are independent of existing membership, bank and fee settings. */
internal data class RecruitmentProfile(
    val guildId: UUID,
    val mode: RecruitmentMode,
    val pitch: String = "",
    val requirements: String = "",
    val interests: Set<String> = emptySet(),
    val preferredTimes: String = "",
    val language: String = "",
    val beginnerFriendly: Boolean = false,
    val discordInvite: String? = null,
    val discordInstructions: String = "",
) {
    init {
        require(pitch.length <= MAX_PITCH && requirements.length <= MAX_REQUIREMENTS)
        require(preferredTimes.length <= MAX_TIMES && language.length <= MAX_LANGUAGE)
        require(
            interests.size <= MAX_INTERESTS && interests.all { it.isNotBlank() && it.length <= MAX_INTEREST_LENGTH },
        )
        require(discordInvite == null || DiscordInvite.isValid(discordInvite))
        require(discordInstructions.length <= MAX_REQUIREMENTS)
        require(mode != RecruitmentMode.DISCORD || discordInvite != null && discordInstructions.isNotBlank()) {
            "Discord applications require a Discord invite and application instructions"
        }
    }

    companion object {
        /** No stored profile means the original open/closed rule, without modifying fees. */
        fun legacy(guildId: UUID, isOpen: Boolean) =
            RecruitmentProfile(
                guildId,
                if (isOpen) RecruitmentMode.JOIN_IMMEDIATELY else RecruitmentMode.CLOSED,
            )
    }
}

/** Exact HTTPS invite destinations only; links cannot contain redirects or formatting. */
internal object DiscordInvite {
    private val INVITE_PATTERN = Regex("https://(?:discord\\.gg/|discord\\.com/invite/)[A-Za-z0-9-]{2,64}/?")

    /** Accepts only direct HTTPS invites on Discord owned hosts. */
    fun isValid(value: String): Boolean = INVITE_PATTERN.matches(value)
}

/** Explains a duplicate or player application limit denial. */
internal enum class SubmissionDenial { DUPLICATE, LIMIT }

/** Immutable policy value object; persistence enforces its checks atomically. */
@Suppress("DataClassContainsFunctions")
internal data class RecruitmentPolicy(
    val maxApplications: Int = 3,
    val applicationLifetime: Duration = Duration.ofDays(DEFAULT_APPLICATION_DAYS),
    val offerLifetime: Duration = Duration.ofHours(DEFAULT_OFFER_HOURS),
) {
    init {
        require(maxApplications in 1..MAX_APPLICATIONS)
        require(applicationLifetime > Duration.ZERO && applicationLifetime <= Duration.ofDays(MAX_APPLICATION_DAYS))
        require(offerLifetime > Duration.ZERO && offerLifetime <= Duration.ofDays(MAX_OFFER_DAYS))
    }

    /** Evaluates live application limits; storage locks before persisting the result. */
    fun canSubmit(
        guild: UUID,
        applicant: UUID,
        applications: Collection<GuildApplication>,
        at: Instant,
    ): SubmissionDenial? {
        val active = applications.filter { it.applicantId == applicant && it.isActive(at) }
        return when {
            active.any { it.guildId == guild } -> SubmissionDenial.DUPLICATE
            active.size >= maxApplications -> SubmissionDenial.LIMIT
            else -> null
        }
    }
}

/** Pending admission is a durable fail-closed reservation, not proof of membership. */
internal enum class ApplicationStatus {
    SUBMITTED,
    OFFERED,
    ADMISSION_PENDING,
    JOINED,
    DECLINED,
    WITHDRAWN,
    EXPIRED,
    CLOSED,
}

/** Immutable state transitions retain data equality; adapters authorize actors and revalidate admission. */
@Suppress("DataClassContainsFunctions")
internal data class GuildApplication(
    val id: UUID,
    val guildId: UUID,
    val applicantId: UUID,
    val answers: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: ApplicationStatus = ApplicationStatus.SUBMITTED,
    val reviewedBy: UUID? = null,
    val reviewedAt: Instant? = null,
    val decisionReason: String = "",
) {
    init {
        require(answers.isNotBlank() && answers.length <= MAX_ANSWERS)
        require(expiresAt >= createdAt)
        require(decisionReason.length <= MAX_REASON)
    }

    /** Pending payments remain active until staff reconcile their outcome. */
    fun isActive(at: Instant): Boolean =
        status == ApplicationStatus.ADMISSION_PENDING ||
            status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED) && at < expiresAt

    /** Approval records an offer only. No payment or member mutation occurs here. */
    fun offer(actor: UUID, at: Instant, lifetime: Duration): GuildApplication {
        require(status == ApplicationStatus.SUBMITTED && at >= createdAt && at < expiresAt)
        require(lifetime > Duration.ZERO && lifetime <= Duration.ofDays(MAX_OFFER_DAYS))
        return copy(
            status = ApplicationStatus.OFFERED,
            expiresAt = at.plus(lifetime),
            reviewedBy = actor,
            reviewedAt = at,
            decisionReason = "",
        )
    }

    /** Records a recruiter decision and optional reason without enrolling the applicant. */
    fun decline(actor: UUID, reason: String, at: Instant): GuildApplication {
        require(status == ApplicationStatus.SUBMITTED && at >= createdAt && at < expiresAt)
        return copy(status = ApplicationStatus.DECLINED, reviewedBy = actor, reviewedAt = at, decisionReason = reason)
    }

    /** Allows only the applicant to withdraw an unexpired, unconfirmed request. */
    fun withdraw(actor: UUID, at: Instant): GuildApplication {
        require(actor == applicantId && status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED))
        require(at >= createdAt && at < expiresAt)
        return copy(status = ApplicationStatus.WITHDRAWN)
    }

    /** The adapter first commits this reservation, then invokes canonical admission once. */
    fun confirm(actor: UUID, at: Instant): GuildApplication {
        require(actor == applicantId && status == ApplicationStatus.OFFERED && at >= createdAt && at < expiresAt)
        return copy(status = ApplicationStatus.ADMISSION_PENDING)
    }

    /** Requires verified admission success; ambiguous outcomes require staff reconciliation. */
    fun completeAdmission(): GuildApplication {
        require(status == ApplicationStatus.ADMISSION_PENDING)
        return copy(status = ApplicationStatus.JOINED)
    }

    /** Closes unconfirmed requests once their deadline is reached. */
    fun expire(at: Instant): GuildApplication {
        require(status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED) && at >= expiresAt)
        return copy(status = ApplicationStatus.EXPIRED)
    }

    /** Used when the applicant joins elsewhere or the target guild is removed. */
    fun close(): GuildApplication {
        require(status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED))
        return copy(status = ApplicationStatus.CLOSED)
    }
}
