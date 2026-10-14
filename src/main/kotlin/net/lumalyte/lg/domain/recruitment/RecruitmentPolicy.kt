package net.lumalyte.lg.domain.recruitment

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Joining method; Discord application state is deliberately never inferred in game. */
enum class RecruitmentMode { JOIN_IMMEDIATELY, IN_GAME, DISCORD, CLOSED }

/** Recruitment fields are independent of existing membership, bank and fee settings. */
data class RecruitmentProfile(
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
        require(pitch.length <= 300 && requirements.length <= 500)
        require(preferredTimes.length <= 100 && language.length <= 40)
        require(interests.size <= 8 && interests.all { it.isNotBlank() && it.length <= 32 })
        require(discordInvite == null || DiscordInvite.isValid(discordInvite))
        require(discordInstructions.length <= 500)
        require(mode != RecruitmentMode.DISCORD || (discordInvite != null && discordInstructions.isNotBlank())) {
            "Discord applications require a Discord invite and application instructions"
        }
    }

    companion object {
        /** No stored profile means the original open/closed rule, without modifying fees. */
        fun legacy(guildId: UUID, isOpen: Boolean) = RecruitmentProfile(guildId,
            if (isOpen) RecruitmentMode.JOIN_IMMEDIATELY else RecruitmentMode.CLOSED)
    }
}

/** Exact HTTPS invite destinations only; links cannot contain redirects or formatting. */
object DiscordInvite {
    private val pattern = Regex("https://(?:discord\\.gg/|discord\\.com/invite/)[A-Za-z0-9-]{2,64}/?")
    fun isValid(value: String): Boolean = pattern.matches(value)
}

enum class SubmissionDenial { DUPLICATE, LIMIT }

/** Configurable defaults; the persistence adapter must enforce the check and insert atomically. */
data class RecruitmentPolicy(
    val maxApplications: Int = 3,
    val applicationLifetime: Duration = Duration.ofDays(7),
    val offerLifetime: Duration = Duration.ofHours(48),
) {
    init {
        require(maxApplications in 1..20)
        require(applicationLifetime > Duration.ZERO && applicationLifetime <= Duration.ofDays(90))
        require(offerLifetime > Duration.ZERO && offerLifetime <= Duration.ofDays(30))
    }

    fun canSubmit(guild: UUID, applicant: UUID, applications: Collection<GuildApplication>, at: Instant): SubmissionDenial? {
        val active = applications.filter { it.applicantId == applicant && it.isActive(at) }
        return when {
            active.any { it.guildId == guild } -> SubmissionDenial.DUPLICATE
            active.size >= maxApplications -> SubmissionDenial.LIMIT
            else -> null
        }
    }
}

/** Pending admission is a durable fail-closed reservation, not proof of membership. */
enum class ApplicationStatus { SUBMITTED, OFFERED, ADMISSION_PENDING, JOINED, DECLINED, WITHDRAWN, EXPIRED, CLOSED }

/** Pure state transitions; adapters must independently authorize recruiters and revalidate admission. */
data class GuildApplication(
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
        require(answers.isNotBlank() && answers.length <= 2000)
        require(expiresAt >= createdAt)
        require(decisionReason.length <= 300)
    }

    fun isActive(at: Instant): Boolean = status == ApplicationStatus.ADMISSION_PENDING ||
        (status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED) && at < expiresAt)

    /** Approval records an offer only. No payment or member mutation occurs here. */
    fun offer(actor: UUID, at: Instant, lifetime: Duration): GuildApplication {
        require(status == ApplicationStatus.SUBMITTED && at >= createdAt && at < expiresAt)
        require(lifetime > Duration.ZERO && lifetime <= Duration.ofDays(30))
        return copy(status = ApplicationStatus.OFFERED, expiresAt = at.plus(lifetime), reviewedBy = actor,
            reviewedAt = at, decisionReason = "")
    }

    fun decline(actor: UUID, reason: String, at: Instant): GuildApplication {
        require(status == ApplicationStatus.SUBMITTED && at >= createdAt && at < expiresAt)
        return copy(status = ApplicationStatus.DECLINED, reviewedBy = actor, reviewedAt = at, decisionReason = reason)
    }

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
