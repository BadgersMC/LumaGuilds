package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.recruitment.GuildApplication
import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import net.lumalyte.lg.domain.recruitment.SubmissionDenial
import java.time.Instant
import java.util.UUID

/** Durable application storage; callers enforce membership, guild mode and reader authorization. */
internal interface GuildRecruitmentRepository {
    /** Policy check and insert share a database transaction and applicant lock. Throws on storage failure. */
    fun submit(application: GuildApplication, policy: RecruitmentPolicy, at: Instant): SubmissionDenial?

    /** Reads one persisted record; the application service must authorize its reader. */
    fun get(id: UUID): GuildApplication?

    /** Returns an applicant's private history for an authorized use case. */
    fun forApplicant(applicant: UUID): List<GuildApplication>

    /** Returns a guild's private inbox for an authorized recruiter. */
    fun forGuild(guild: UUID): List<GuildApplication>

    /** Compare-and-set makes stale review/confirmation attempts unable to overwrite a newer decision. */
    fun transition(expected: GuildApplication, updated: GuildApplication): Boolean
}
