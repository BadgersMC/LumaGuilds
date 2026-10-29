package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Recruitment listings and durable player discovery preferences. */
internal interface RecruitmentDirectoryRepository {
    /** Unknown for pre-existing listings without a recorded edit timestamp. */
    fun updatedAt(guild: UUID): Instant? = null

    /** Null preserves the guild's legacy open/closed setting. */
    fun profile(guild: UUID): RecruitmentProfile?

    /** Replaces only recruitment fields; guild membership, fees and ranks are unchanged. */
    fun save(profile: RecruitmentProfile)

    /** Only UI preferences change; production adapters merge them under the guild lock. */
    fun savePresentation(
        profile: RecruitmentProfile,
        fields: Set<String> = setOf("publicListing", "joinAnnouncements", "showBanner", "questReminders"),
    ) = save(profile)

    /** Personal opt-out persists across reconnects and restarts. */
    fun setReminders(
        player: UUID,
        enabled: Boolean,
    )

    /** Reads the personal prompt preference, enabled for a player with no stored preference. */
    fun remindersEnabled(player: UUID): Boolean

    /** Atomically consumes either the one-time prompt or a due optional reminder. */
    fun consumePrompt(
        player: UUID,
        at: Instant,
        repeatAfter: Duration?,
    ): Boolean

    /** Suppresses replay of the same private application/inbox digest across reconnects. */
    fun consumeNotification(
        player: UUID,
        signature: String,
    ): Boolean

    /** A distinct durable key prevents quest reminders from replaying recruitment digests. */
    fun consumeQuestReminder(
        player: UUID,
        guild: UUID,
        week: String,
    ): Boolean = false
}
