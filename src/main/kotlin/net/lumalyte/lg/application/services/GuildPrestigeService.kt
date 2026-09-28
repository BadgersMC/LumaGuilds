package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.PrestigeRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.persistence.RewardOwnershipRepository
import net.lumalyte.lg.domain.rewards.*
import java.util.UUID

class GuildPrestigeService(
    private val owners: RewardOwnershipRepository,
    private val progression: ProgressionRepository,
    catalog: RewardCatalog,
    private val repository: PrestigeRepository,
    private val access: GuildRewardPurchaseAccess,
    private val config: ConfigService,
) {
    private val resolver = RewardEntitlementResolver(catalog)

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
        } catch (_: Exception) {
            null
        }
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
        } catch (_: Exception) {
            null
        }
    }

    fun confirm(actorId: UUID, quote: PrestigeQuote): PrestigeResult {
        if (actorId != quote.actorId) return PrestigeResult.Rejected(PrestigeRejection.UNAUTHORIZED)
        val result = repository.confirm(quote) {
            val current = runCatching { config.loadConfig() }.getOrNull()
            when {
                current == null -> PrestigeRejection.UNAVAILABLE
                !current.progression.prestige.enabled -> PrestigeRejection.UNAVAILABLE
                !access.allowed(actorId, quote.guildId) -> PrestigeRejection.UNAUTHORIZED
                else -> null
            }
        }
        if (result is PrestigeResult.Applied) {
            progression.refreshGuildProgression(quote.guildId)
        }
        return result
    }
}