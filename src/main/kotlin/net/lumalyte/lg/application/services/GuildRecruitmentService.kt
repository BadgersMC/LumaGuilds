package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildRecruitmentRepository
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.GuildApplication
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import net.lumalyte.lg.domain.values.JoinRequirement
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Shared Java/Bedrock recruitment use cases; repository reads are never a substitute for actor authorization. */
internal class GuildRecruitmentService(
    private val applications: GuildRecruitmentRepository,
    private val directory: RecruitmentDirectoryRepository,
    private val guilds: GuildService,
    private val members: MemberService,
    private val lfg: LfgService,
    private val policy: () -> RecruitmentPolicy,
    private val eligible: (UUID, UUID) -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
    private val maximumMembers: () -> Int = { Int.MAX_VALUE },
) {
    /** Lists public guild identities; closed/full listings remain visible with joining disabled. */
    fun browse(): List<Guild> = guilds.getAllGuilds().sortedBy { it.name.lowercase() }

    /** Returns a public recruitment profile, preserving the legacy open flag when no profile exists. */
    fun profile(guild: Guild): RecruitmentProfile =
        directory.profile(guild.id) ?: RecruitmentProfile.legacy(guild.id, guild.isOpen)

    /** Returns current capacity rather than only the global configured maximum. */
    fun spaces(guild: UUID): Int =
        (minOf(members.getMemberLimit(guild), maximumMembers()) - members.getMemberCount(guild)).coerceAtLeast(0)

    /** Requires settings authority at write time; does not modify fees or any other guild field. */
    fun saveProfile(actor: UUID, profile: RecruitmentProfile) {
        require(guilds.getGuild(profile.guildId) != null)
        require(GuildRecruitmentPermissions.canEditSettings(members, actor, profile.guildId)) {
            "Recruitment settings permission required"
        }
        directory.save(profile)
    }

    /** Submission is private and charges nothing; the repository enforces duplicate/limit checks atomically. */
    fun submit(player: UUID, guildId: UUID, answers: String): GuildApplication {
        val guild = requireNotNull(guilds.getGuild(guildId)) { "Guild no longer exists" }
        require(profile(guild).mode == RecruitmentMode.IN_GAME) { "This guild does not accept in-game applications" }
        require(
            members.getPlayerGuilds(player).isEmpty() && spaces(guildId) > 0 && eligible(player, guildId),
        ) { "You are not eligible to apply" }
        val at = now()
        val policy = policy()
        val application =
            GuildApplication(
                UUID.randomUUID(),
                guildId,
                player,
                answers.trim(),
                at,
                at.plus(policy.applicationLifetime),
            )
        require(
            applications.submit(application, policy, at) == null,
        ) { "An application is already active or your application limit was reached" }
        return application
    }

    /** Applicants can read only their own records through this method. */
    fun mine(player: UUID): List<GuildApplication> = applications.forApplicant(player).map(::expire)

    /** Inbox authorization is checked every time the screen is opened or refreshed. */
    fun inbox(actor: UUID, guild: UUID): List<GuildApplication> {
        require(GuildRecruitmentPermissions.canReview(members, actor, guild)) { "Recruitment permission required" }
        return applications.forGuild(guild).map(::expire)
    }

    /** A decision records its actor and creates an offer rather than adding or charging a member. */
    fun review(actor: UUID, id: UUID, accept: Boolean, reason: String): GuildApplication {
        val current = requireNotNull(applications.get(id)) { "Application no longer exists" }
        require(
            GuildRecruitmentPermissions.canReview(members, actor, current.guildId),
        ) { "Recruitment permission required" }
        val guild = requireNotNull(guilds.getGuild(current.guildId)) { "Guild no longer exists" }
        if (accept) {
            require(profile(guild).mode == RecruitmentMode.IN_GAME) { "Recruitment method has changed" }
            require(eligible(current.applicantId, guild.id) && members.getPlayerGuilds(current.applicantId).isEmpty()) {
                "Applicant is no longer eligible"
            }
        }
        val next =
            if (accept) {
                current.offer(
                    actor,
                    now(),
                    policy().offerLifetime,
                )
            } else {
                current.decline(actor, reason, now())
            }
        require(applications.transition(current, next)) { "Application changed; reopen the inbox" }
        return next
    }

    /** Only the applicant can withdraw; stale transitions cannot undo a confirmed admission. */
    fun withdraw(player: UUID, id: UUID) {
        val current = requireNotNull(applications.get(id))
        require(applications.transition(current, current.withdraw(player, now()))) {
            "Application changed; refresh your applications"
        }
    }

    /** Confirms the fee snapshot and reserves once before invoking canonical paid admission. */
    @Synchronized
    fun confirm(
        player: UUID,
        id: UUID,
        quotedGuild: Guild,
        quotedFee: JoinRequirement? = lfg.getJoinRequirement(quotedGuild),
    ): LfgJoinResult {
        val current = requireNotNull(applications.get(id)) { "Application no longer exists" }
        require(current.applicantId == player && current.guildId == quotedGuild.id)
        val guild = requireNotNull(guilds.getGuild(current.guildId)) { "Guild no longer exists" }
        require(
            guild.joinFeeEnabled == quotedGuild.joinFeeEnabled && guild.joinFeeAmount == quotedGuild.joinFeeAmount,
        ) {
            "Joining fee changed; review the offer again"
        }
        require(
            lfg.getJoinRequirement(guild) == quotedFee,
        ) { "Joining currency or quoted fee changed; review the offer again" }
        require(profile(guild).mode == RecruitmentMode.IN_GAME && eligible(player, guild.id)) {
            "You are no longer eligible to join"
        }
        val validation = lfg.canJoinGuild(player, guild)
        if (validation !is LfgJoinResult.Success) return validation
        val pending = current.confirm(player, now())
        require(applications.transition(current, pending)) { "Offer already changed; refresh your applications" }
        val result =
            lfg.joinApprovedGuild(player, guild) {
                val latest = guilds.getGuild(guild.id)
                latest != null && profile(latest).mode == RecruitmentMode.IN_GAME && eligible(player, guild.id) &&
                    lfg.getJoinRequirement(latest) == quotedFee &&
                    applications.get(id)?.status == ApplicationStatus.ADMISSION_PENDING
            }
        if (result is LfgJoinResult.Success) {
            applications.transition(pending, pending.completeAdmission())
            closeOtherApplications(player, guild.id)
        }
        // Failures after reservation remain fail-closed; never guess whether a payment committed.
        return result
    }

    /** Membership events close other offers without changing rewards or charging again. */
    fun closeOtherApplications(player: UUID, joinedGuild: UUID) {
        if (joinedGuild !in members.getPlayerGuilds(player)) return
        applications.forApplicant(player).forEach { application ->
            if (application.status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED)) {
                applications.transition(application, application.close())
            }
        }
    }

    /** Disbanding closes unconfirmed requests but never guesses the outcome of an uncertain payment. */
    fun closeGuildApplications(guild: UUID) {
        applications.forGuild(
            guild,
        ).filter { it.status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED) }
            .forEach { applications.transition(it, it.close()) }
    }

    private fun expire(application: GuildApplication): GuildApplication {
        val active = application.status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED)
        if (!active || now() < application.expiresAt) return application
        val expired = application.expire(now())
        return if (applications.transition(
                application,
                expired,
            )
        ) {
            expired
        } else {
            applications.get(application.id) ?: expired
        }
    }

    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)
}
