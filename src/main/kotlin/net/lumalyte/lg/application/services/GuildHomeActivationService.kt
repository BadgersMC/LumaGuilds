package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildHomeActivationRepository
import net.lumalyte.lg.application.persistence.GuildHomeAccessState
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.GuildHome
import java.util.UUID

/** Separates a saved guild-home location from its paid Chapter 2 activation. */
class GuildHomeActivationService(
    private val repository: GuildHomeActivationRepository,
    private val costs: GuildCostService,
    private val maySetHome: (UUID, String, UUID, Boolean, GuildHome?) -> Boolean,
    private val homeAccessState: (UUID, String) -> GuildHomeAccessState = { _, _ -> GuildHomeAccessState.UNRECONCILED },
    private val paidOutpostForNewHome: (UUID, String) -> Boolean = { _, _ -> false },
    private val config: () -> MainConfig,
) {
    fun isActive(guildId: UUID, homeName: String): Boolean {
        val state = homeAccessState(guildId, homeName)
        if (state.locked) return false
        // Verified Chapter 1 locations and restored paid Outposts are already
        // accessible. Do not sell them a separate home-activation payment.
        if (state == GuildHomeAccessState.ACTIVE_BASE || state == GuildHomeAccessState.ACTIVE_OUTPOST) return true
        return !config().chapterTwoGoldCostsEnabled || repository.isActive(guildId, homeName)
    }

    fun activeCount(guildId: UUID): Int = repository.activeCount(guildId)
    fun availableLegacyCredits(guildId: UUID): Int = repository.availableCredits(guildId)

    fun activateSavedHome(
        transactionId: UUID,
        guildId: UUID,
        homeName: String,
        actorId: UUID,
    ): HomeActivationCostResult {
        if (homeAccessState(guildId, homeName).locked) {
            return HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        // Recheck the actor at execution time, including when the UI was opened before a rank change.
        if (!allowedToSet(guildId, homeName, actorId, existedBefore = true, proposedHome = null)) {
            return HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        if (isActive(guildId, homeName)) return HomeActivationCostResult.Applied(0)
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
        proposedHome: GuildHome,
        persist: () -> Boolean,
    ): HomeActivationCostResult {
        if (existedBefore && homeAccessState(guildId, homeName).locked) {
            return HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        // All Java/Bedrock entry points share this guard before the guild-gold debit.
        // The authoritative GuildService.setHome check still handles concurrent changes.
        if (!allowedToSet(guildId, homeName, actorId, existedBefore, proposedHome)) {
            return HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        // Verified original homes and restored paid Outposts are already usable,
        // even when they have no legacy activation-ledger row. Moving their
        // coordinates must not create another debit or synthetic activation.
        if (existedBefore && homeAccessState(guildId, homeName) in setOf(
                GuildHomeAccessState.ACTIVE_BASE, GuildHomeAccessState.ACTIVE_OUTPOST,
            )) {
            return if (persist()) HomeActivationCostResult.Applied(0)
            else HomeActivationCostResult.ActivationFailed(compensated = true)
        }
        // Outpost acquisition already charged gold in the reward purchase
        // transaction. Assign this newly purchased extra slot without a second
        // per-home activation debit (and verify the actual stored grant).
        if (!existedBefore && paidOutpostForNewHome(guildId, homeName)) {
            if (!persist()) return HomeActivationCostResult.ActivationFailed(compensated = true)
            return if (homeAccessState(guildId, homeName) == GuildHomeAccessState.ACTIVE_OUTPOST) {
                HomeActivationCostResult.Applied(0)
            } else HomeActivationCostResult.ActivationFailed(compensated = true)
        }
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

    private fun allowedToSet(guildId: UUID, homeName: String, actorId: UUID, existedBefore: Boolean, proposedHome: GuildHome?): Boolean =
        try { maySetHome(guildId, homeName, actorId, existedBefore, proposedHome) } catch (_: Exception) { false }

    fun removeActivation(guildId: UUID, homeName: String): Boolean = repository.remove(guildId, homeName)
    fun removeAllActivations(guildId: UUID): Boolean = repository.removeAll(guildId)
}
