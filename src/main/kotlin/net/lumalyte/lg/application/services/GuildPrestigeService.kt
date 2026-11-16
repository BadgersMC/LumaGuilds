package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.persistence.PrestigeRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RewardOwnershipRepository
import net.lumalyte.lg.domain.rewards.*
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

class GuildPrestigeService(
    private val owners: RewardOwnershipRepository,
    private val progression: ProgressionRepository,
    catalog: RewardCatalog,
    private val repository: PrestigeRepository,
    private val access: GuildRewardPurchaseAccess,
    private val config: ConfigService,
    private val announcement: GuildPrestigeAnnouncement = GuildPrestigeAnnouncement { _, _ -> },
    private val history: PrestigeHistory? = null,
    private val notifiers: List<GuildPrestigeNotifier> = emptyList(),
    /** REQ-248: refreshes the cached guild level after a prestige, so vault sizes and other level rules see level 1. */
    private val refreshGuildLevel: (UUID) -> Unit = {},
    /** REQ-260: grants the Hall theme chosen on the prestige screen once the prestige has applied. */
    private val grantHallTheme: (UUID, HallTheme) -> Unit = { _, _ -> },
) {
    private val resolver = RewardEntitlementResolver(catalog)
    private val logger = LoggerFactory.getLogger(GuildPrestigeService::class.java)

    fun overview(guildId: UUID): PrestigeOverview? {
        return try {
            val settings = config.loadConfig()
            val run = progression.getGuildProgression(guildId) ?: return null
            val snapshot = (owners.read(guildId) as? RewardOwnershipRead.Found)?.snapshot ?: return null
            val prestige = settings.progression.prestige
            val choices = if (run.currentLevel == 100 && snapshot.ownership.prestigeCount < prestige.maxCount) {
                resolver.resolve(
                    run.currentLevel,
                    snapshot.ownership,
                    settings.bank.maxBankBalance.toLong(),
                    settings.guild.maxMembersPerGuild,
                ).prestigeChoices
            } else {
                emptyList()
            }
            PrestigeOverview(
                enabled = prestige.enabled,
                currentLevel = run.currentLevel,
                prestigeCount = snapshot.ownership.prestigeCount,
                maxPrestigeCount = prestige.maxCount,
                nextFee = if (snapshot.ownership.prestigeCount < prestige.maxCount)
                    prestige.feeFor(snapshot.ownership.prestigeCount) else null,
                choices = choices,
            )
        } catch (e: Exception) {
            net.lumalyte.lg.application.logging.SwallowedErrors.log("GuildPrestigeService:53", e)
            null
        }
    }

    /**
     * REQ-249: what confirming [quote] would give the guild, worked out the same way the
     * prestige itself applies it. Null when the guild's data can't be read.
     */
    fun preview(quote: PrestigeQuote): PrestigePreview? = try {
        previewOrThrow(quote)
    } catch (e: Exception) {
        logger.warn("Could not preview prestige for guild {}", quote.guildId, e)
        null
    }

    private fun previewOrThrow(quote: PrestigeQuote): PrestigePreview? {
        val settings = config.loadConfig()
        val prestige = settings.progression.prestige
        val run = progression.getGuildProgression(quote.guildId) ?: return null
        val snapshot = (owners.read(quote.guildId) as? RewardOwnershipRead.Found)?.snapshot ?: return null
        val tier = snapshot.ownership.prestigeCount + 1
        val ceiling = settings.bank.maxBankBalance.toLong()
        val members = settings.guild.maxMembersPerGuild
        val before = resolver.resolve(run.currentLevel, snapshot.ownership, ceiling, members)
        val next = snapshot.ownership.copy(
            currentRun = emptySet(),
            permanent = snapshot.ownership.permanent + quote.retainedRewardId,
            prestigeCount = tier,
        )
        val curve = net.lumalyte.lg.domain.values.ProgressionCurve.from(settings.progression)
        val carried = PrestigeCarryOver.experience(run.totalExperience, curve, prestige.carryOver)
        val after = resolver.resolve(curve.levelFromExperience(carried), next, ceiling, members)
        val firstPrestige = tier == 1
        val memberCapacity = settings.guild.memberCapacity
        val roles = settings.discordGuildRoles
        return PrestigePreview(
            tier = tier,
            finalTier = tier >= prestige.maxCount,
            homeSlotsBefore = before.homeCapacity,
            homeSlotsAfter = after.homeCapacity,
            perks = PrestigePerks(prestige.perks).unlockedAt(tier),
            carry = if (prestige.carryOver.enabled) {
                PrestigePreview.Carry(carried, curve.levelFromExperience(carried))
            } else null,
            memberLimitKept = if (firstPrestige && memberCapacity.enabled) {
                net.lumalyte.lg.domain.values.GuildMemberCapacity.limit(1, tier, null, memberCapacity, members)
            } else null,
            discordRoleMinimumLevel = if (firstPrestige && roles.enabled && roles.minimumLevel > 1) roles.minimumLevel else null,
            memberCreditTenureHours = prestige.memberCredits.takeIf { it.enabled }?.minTenureHours,
            firstOnServer = history != null && history.firstToReach(tier) == null,
            fanfare = prestige.fanfare.enabled,
            discordPost = prestige.discord.enabled && prestige.discord.webhookUrl.isNotBlank(),
            fee = quote.quotedFee,
        )
    }

    fun quote(actorId: UUID, guildId: UUID, retainedRewardId: String): PrestigeQuote? {
        return try {
            val settings = config.loadConfig()
            if (!settings.progression.prestige.enabled || !access.allowed(actorId, guildId)) return null
            val run = progression.getGuildProgression(guildId) ?: return null
            if (run.currentLevel != 100) return null
            val snapshot = (owners.read(guildId) as? RewardOwnershipRead.Found)?.snapshot ?: return null
            val prestige = settings.progression.prestige
            if (snapshot.ownership.prestigeCount >= prestige.maxCount) return null
            val choices = resolver.resolve(
                run.currentLevel,
                snapshot.ownership,
                settings.bank.maxBankBalance.toLong(),
                settings.guild.maxMembersPerGuild,
            ).prestigeChoices
            if (choices.none { it.id == retainedRewardId }) return null
            PrestigeQuote(
                transactionId = UUID.randomUUID(),
                guildId = guildId,
                actorId = actorId,
                retainedRewardId = retainedRewardId,
                quotedFee = prestige.feeFor(snapshot.ownership.prestigeCount),
                expectedOwnershipVersion = snapshot.version,
                expectedPrestigeCount = snapshot.ownership.prestigeCount,
            )
        } catch (e: Exception) {
            net.lumalyte.lg.application.logging.SwallowedErrors.log("GuildPrestigeService:135", e)
            null
        }
    }

    fun confirm(actorId: UUID, quote: PrestigeQuote, hallTheme: HallTheme? = null): PrestigeResult {
        if (actorId != quote.actorId) return PrestigeResult.Rejected(PrestigeRejection.UNAUTHORIZED)
        val confirmation = repository.confirmWithCommitStatus(quote) {
            val current = runCatching { config.loadConfig() }.getOrNull()
            when {
                current == null -> PrestigeRejection.UNAVAILABLE
                !current.progression.prestige.enabled -> PrestigeRejection.UNAVAILABLE
                !access.allowed(actorId, quote.guildId) -> PrestigeRejection.UNAUTHORIZED
                else -> null
            }
        }
        return settle(quote, confirmation, hallTheme)
    }

    /** REQ-186: the prestige choices a staff grant may keep (those of a level 100 guild). */
    fun grantChoices(guildId: UUID): List<RewardDefinition> = try {
        val settings = config.loadConfig()
        val snapshot = (owners.read(guildId) as? RewardOwnershipRead.Found)?.snapshot
        if (snapshot == null || snapshot.ownership.prestigeCount >= settings.progression.prestige.maxCount) {
            emptyList()
        } else {
            resolver.resolve(100, snapshot.ownership, settings.bank.maxBankBalance.toLong(), settings.guild.maxMembersPerGuild)
                .prestigeChoices
        }
    } catch (e: Exception) {
        net.lumalyte.lg.application.logging.SwallowedErrors.log("GuildPrestigeService:164", e)
        emptyList()
    }

    /**
     * REQ-186: a staff prestige with no fee and no level requirement. Announcements,
     * fanfare, credits and every listener run as for a guild's own prestige.
     */
    fun staffGrant(actorId: UUID, guildId: UUID, retainedRewardId: String): PrestigeResult {
        val snapshot = (runCatching { owners.read(guildId) }.getOrNull() as? RewardOwnershipRead.Found)?.snapshot
            ?: return PrestigeResult.Rejected(PrestigeRejection.UNINITIALIZED)
        val quote = PrestigeQuote(
            transactionId = UUID.randomUUID(),
            guildId = guildId,
            actorId = actorId,
            retainedRewardId = retainedRewardId,
            quotedFee = 0,
            expectedOwnershipVersion = snapshot.version,
            expectedPrestigeCount = snapshot.ownership.prestigeCount,
        )
        return settle(quote, repository.grantWithCommitStatus(quote))
    }

    private fun settle(
        quote: PrestigeQuote,
        confirmation: net.lumalyte.lg.application.persistence.PrestigeConfirmation,
        hallTheme: HallTheme? = null,
    ): PrestigeResult {
        val result = confirmation.result
        if (result is PrestigeResult.Applied) {
            // A retry of an already applied prestige still grants the theme; a theme that can't be
            // granted stays as an open pick in Hall Display.
            if (hallTheme != null) {
                runCatching { grantHallTheme(quote.guildId, hallTheme) }
                    .onFailure { logger.warn("Failed to grant Hall theme $hallTheme to guild ${quote.guildId}", it) }
            }
            if (confirmation.newlyCommitted) {
                runCatching { announcement.announce(quote.guildId, result.prestigeCount) }
                    .onFailure { logger.warn("Failed to announce prestige for guild ${quote.guildId}", it) }
                publish(quote, result)
            }
            progression.refreshGuildProgression(quote.guildId)
            runCatching { refreshGuildLevel(quote.guildId) }
                .onFailure { logger.warn("Failed to refresh the cached level of guild ${quote.guildId}", it) }
        }
        return result
    }

    /** REQ-153: one completion per new commit; each listener is isolated from the others. */
    private fun publish(quote: PrestigeQuote, result: PrestigeResult.Applied) {
        if (notifiers.isEmpty()) return
        val record = runCatching { history?.history(quote.guildId)?.firstOrNull { it.transactionId == quote.transactionId } }
            .getOrNull()
        val first = runCatching { history?.firstToReach(result.prestigeCount)?.transactionId == quote.transactionId }
            .getOrDefault(false)
        val completion = PrestigeCompletion(
            transactionId = quote.transactionId,
            guildId = quote.guildId,
            actorId = quote.actorId,
            tier = result.prestigeCount,
            retainedRewardId = result.retainedRewardId,
            appliedAt = record?.appliedAt ?: Instant.now(),
            firstToReachTier = first,
        )
        for (notifier in notifiers) {
            runCatching { notifier.prestiged(completion) }
                .onFailure { logger.warn("Prestige listener failed for guild ${quote.guildId}", it) }
        }
    }
}
