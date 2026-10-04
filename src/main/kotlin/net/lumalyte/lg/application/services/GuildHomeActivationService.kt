package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildHomeActivationRepository
import net.lumalyte.lg.config.MainConfig
import java.util.UUID

/** Separates a saved guild-home location from its paid Chapter 2 activation. */
class GuildHomeActivationService(
    private val repository: GuildHomeActivationRepository,
    private val costs: GuildCostService,
    private val config: () -> MainConfig,
) {
    fun isActive(guildId: UUID, homeName: String): Boolean =
        !config().chapterTwoGoldCostsEnabled || repository.isActive(guildId, homeName)

    fun activeCount(guildId: UUID): Int = repository.activeCount(guildId)
    fun availableLegacyCredits(guildId: UUID): Int = repository.availableCredits(guildId)

    fun activateSavedHome(
        transactionId: UUID,
        guildId: UUID,
        homeName: String,
        actorId: UUID,
    ): HomeActivationCostResult {
        if (repository.isActive(guildId, homeName)) return HomeActivationCostResult.Applied(0)
        if (!config().chapterTwoGoldCostsEnabled) {
            return if (repository.activate(guildId, homeName, null)) HomeActivationCostResult.Applied(0)
            else HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        if (repository.availableCredits(guildId) > 0 && repository.activateUsingCredit(guildId, homeName)) {
            return HomeActivationCostResult.Applied(0)
        }
        val ordinal = repository.activeCount(guildId) + 1
        return costs.activateHome(transactionId, guildId, actorId, ordinal, alreadyActivated = false) {
            repository.activate(guildId, homeName, transactionId)
        }
    }

    /**
     * Persists a new/moved location and ensures it has paid activation.
     * Existing active homes can move for free. Legacy migration credits only apply to homes that already existed.
     */
    fun persistLocation(
        transactionId: UUID,
        guildId: UUID,
        homeName: String,
        actorId: UUID,
        existedBefore: Boolean,
        persist: () -> Boolean,
    ): HomeActivationCostResult {
        val alreadyActive = repository.isActive(guildId, homeName)
        if (!config().chapterTwoGoldCostsEnabled) {
            if (!persist()) return HomeActivationCostResult.ActivationFailed(compensated = true)
            return if (repository.activate(guildId, homeName, null)) HomeActivationCostResult.Applied(0)
            else HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        if (alreadyActive) {
            return costs.activateHome(transactionId, guildId, actorId, repository.activeCount(guildId).coerceAtLeast(1), true, persist)
        }
        if (existedBefore && repository.availableCredits(guildId) > 0) {
            if (!persist()) return HomeActivationCostResult.ActivationFailed(compensated = true)
            return if (repository.activateUsingCredit(guildId, homeName)) HomeActivationCostResult.Applied(0)
            else HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        val ordinal = repository.activeCount(guildId) + 1
        return costs.activateHome(transactionId, guildId, actorId, ordinal, alreadyActivated = false) {
            persist() && repository.activate(guildId, homeName, transactionId)
        }
    }

    fun removeActivation(guildId: UUID, homeName: String): Boolean = repository.remove(guildId, homeName)
    fun removeAllActivations(guildId: UUID): Boolean = repository.removeAll(guildId)
}
