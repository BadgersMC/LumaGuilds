package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

class WarServiceBukkit : WarService {

    private val logger = LoggerFactory.getLogger(WarServiceBukkit::class.java)

    // In-memory storage for now - would need database persistence in production
    private val wars = mutableMapOf<UUID, War>()
    private val warDeclarations = mutableMapOf<UUID, WarDeclaration>()
    private val warStats = mutableMapOf<UUID, WarStats>()
    private val warWagers = mutableMapOf<UUID, WarWager>()
    private val peaceAgreements = mutableMapOf<UUID, PeaceAgreement>()
    private val warFarmingCooldowns = mutableMapOf<UUID, Instant>()
    private val warDeclarationCooldowns = mutableMapOf<UUID, Instant>()

    override fun declareWar(
        declaringGuildId: UUID,
        defendingGuildId: UUID,
        duration: Duration,
        objectives: Set<WarObjective>,
        actorId: UUID
    ): War? {
        return try {
            // Check if war already exists between these guilds
            val existingWar = getCurrentWarBetweenGuilds(declaringGuildId, defendingGuildId)
            if (existingWar != null) {
                logger.warn("Cannot declare war - active war already exists between guilds $declaringGuildId and $defendingGuildId")
                return null
            }

            // Check if pending declaration already exists
            val existingDeclaration = warDeclarations.values.find {
                (it.declaringGuildId == declaringGuildId && it.defendingGuildId == defendingGuildId) ||
                (it.declaringGuildId == defendingGuildId && it.defendingGuildId == declaringGuildId)
            }
            if (existingDeclaration != null) {
                logger.warn("Cannot declare war - pending declaration already exists between guilds $declaringGuildId and $defendingGuildId")
                return null
            }

            // For now, create immediate war (legacy behavior)
            // TODO: This should be updated to create WarDeclarations for proper acceptance flow
            val war = War.create(
                declaringGuildId = declaringGuildId,
                defendingGuildId = defendingGuildId,
                duration = duration
            ).copy(
                status = WarStatus.ACTIVE,  // Auto-accepted wars are immediately active
                startedAt = Instant.now(),
                objectives = objectives
            )

            wars[war.id] = war

            // Initialize war stats
            warStats[war.id] = WarStats(warId = war.id)

            // Record war declaration for cooldown tracking
            recordWarDeclaration(declaringGuildId)

            logger.info("War declared and ACTIVE by guild $declaringGuildId against guild $defendingGuildId")
            war
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error declaring war between $declaringGuildId and $defendingGuildId", e)
            null
        }
    }

    /**
     * Creates a war declaration that requires acceptance
     */
    fun createWarDeclaration(
        declaringGuildId: UUID,
        defendingGuildId: UUID,
        duration: Duration,
        objectives: Set<WarObjective>,
        wagerAmount: Int = 0,
        terms: String? = null,
        actorId: UUID
    ): WarDeclaration? {
        return try {
            // Check if war already exists between these guilds
            val existingWar = getCurrentWarBetweenGuilds(declaringGuildId, defendingGuildId)
            if (existingWar != null) {
                logger.warn("Cannot create war declaration - active war already exists between guilds $declaringGuildId and $defendingGuildId")
                return null
            }

            // Check if pending declaration already exists
            val existingDeclaration = warDeclarations.values.find {
                (it.declaringGuildId == declaringGuildId && it.defendingGuildId == defendingGuildId) ||
                (it.declaringGuildId == defendingGuildId && it.defendingGuildId == declaringGuildId)
            }
            if (existingDeclaration != null) {
                logger.warn("Cannot create war declaration - pending declaration already exists between guilds $declaringGuildId and $defendingGuildId")
                return null
            }

            val declaration = WarDeclaration(
                declaringGuildId = declaringGuildId,
                defendingGuildId = defendingGuildId,
                proposedDuration = duration,
                objectives = objectives,
                terms = terms
            )

            warDeclarations[declaration.id] = declaration

            // Record war declaration for cooldown tracking
            recordWarDeclaration(declaringGuildId)

            logger.info("War declaration created by guild $declaringGuildId against guild $defendingGuildId with wager $wagerAmount")
            declaration
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error creating war declaration between $declaringGuildId and $defendingGuildId", e)
            null
        }
    }

    override fun acceptWarDeclaration(declarationId: UUID, actorId: UUID): War? {
        return try {
            val declaration = warDeclarations[declarationId] ?: return null

            val war = War.create(
                declaringGuildId = declaration.declaringGuildId,
                defendingGuildId = declaration.defendingGuildId,
                duration = declaration.proposedDuration
            )

            wars[war.id] = war
            warDeclarations.remove(declarationId)

            logger.info("War accepted: ${war.id}")
            war
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error accepting war declaration: $declarationId", e)
            null
        }
    }

    override fun rejectWarDeclaration(declarationId: UUID, actorId: UUID): Boolean {
        return try {
            warDeclarations.remove(declarationId) != null
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error rejecting war declaration: $declarationId", e)
            false
        }
    }

    override fun cancelWarDeclaration(declarationId: UUID, actorId: UUID): Boolean {
        return try {
            warDeclarations.remove(declarationId) != null
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error canceling war declaration: $declarationId", e)
            false
        }
    }

    override fun endWar(warId: UUID, winnerGuildId: UUID, peaceTerms: String?, actorId: UUID): Boolean {
        return try {
            val war = wars[warId] ?: return false
            val loserGuildId = if (war.declaringGuildId == winnerGuildId) war.defendingGuildId else war.declaringGuildId
            val endedWar = war.copy(
                status = WarStatus.ENDED,
                endedAt = Instant.now(),
                winner = winnerGuildId,
                loser = loserGuildId,
                peaceTerms = peaceTerms
            )
            wars[warId] = endedWar

            // Apply war farming cooldown to the winner
            applyWarFarmingCooldown(war.declaringGuildId, war.defendingGuildId, winnerGuildId)

            logger.info("War ended: $warId, winner: $winnerGuildId")
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error ending war: $warId", e)
            false
        }
    }

    override fun endWarAsDraw(warId: UUID, reason: String?, actorId: UUID): Boolean {
        return try {
            val war = wars[warId] ?: return false
            val endedWar = war.copy(
                status = WarStatus.ENDED,
                endedAt = Instant.now(),
                winner = null, // No winner in a draw
                loser = null,  // No loser in a draw
                peaceTerms = reason ?: "War ended in a draw"
            )
            wars[warId] = endedWar
            logger.info("War ended as draw: $warId, reason: $reason")
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error ending war as draw: $warId", e)
            false
        }
    }

    override fun cancelWar(warId: UUID, actorId: UUID): Boolean {
        return try {
            val war = wars[warId] ?: return false
            val canceledWar = war.copy(status = WarStatus.CANCELLED)
            wars[warId] = canceledWar
            logger.info("War canceled: $warId")
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error canceling war: $warId", e)
            false
        }
    }

    override fun getWar(warId: UUID): War? {
        return wars[warId]
    }

    override fun getActiveWars(): List<War> {
        return wars.values.filter { it.isActive }
    }

    override fun getWarsForGuild(guildId: UUID): List<War> {
        return wars.values.filter { it.declaringGuildId == guildId || it.defendingGuildId == guildId }
    }

    override fun getPendingDeclarationsForGuild(guildId: UUID): List<WarDeclaration> {
        return warDeclarations.values.filter { it.defendingGuildId == guildId }
    }

    override fun getDeclarationsByGuild(guildId: UUID): List<WarDeclaration> {
        return warDeclarations.values.filter { it.declaringGuildId == guildId }
    }

    override fun getWarStats(warId: UUID): WarStats {
        return warStats[warId] ?: WarStats(warId)
    }

    override fun updateWarStats(stats: WarStats): Boolean {
        return try {
            warStats[stats.warId] = stats
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error updating war stats for war: ${stats.warId}", e)
            false
        }
    }


    override fun addObjectiveProgress(warId: UUID, objectiveId: UUID, progress: Int): Boolean {
        // This is a simplified implementation - would need proper objective tracking
        return try {
            logger.info("Objective progress added: war=$warId, objective=$objectiveId, progress=$progress")
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error adding objective progress for war: $warId", e)
            false
        }
    }

    override fun getWinLossRatio(guildId: UUID): Double {
        return try {
            val warHistory = getWarHistory(guildId, 100)
            val wins = warHistory.count { it.winner == guildId }
            val losses = warHistory.count { it.loser == guildId }

            if (losses == 0) {
                if (wins > 0) Double.MAX_VALUE else 0.0
            } else {
                wins.toDouble() / losses.toDouble()
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error getting win/loss ratio for guild: $guildId", e)
            0.0
        }
    }

    override fun canGuildDeclareWar(guildId: UUID): Boolean {
        // Basic check - guild not already in too many wars
        val activeWars = wars.values.filter { it.isActive && (it.declaringGuildId == guildId || it.defendingGuildId == guildId) }.size
        return activeWars < 3 // Max 3 simultaneous wars
    }

    override fun canPlayerManageWars(playerId: UUID, guildId: UUID): Boolean {
        // Placeholder - would need to check player permissions
        return true
    }

    override fun getCurrentWarBetweenGuilds(guildA: UUID, guildB: UUID): War? {
        return wars.values.find {
            it.isActive &&
            ((it.declaringGuildId == guildA && it.defendingGuildId == guildB) ||
             (it.declaringGuildId == guildB && it.defendingGuildId == guildA))
        }
    }

    override fun processExpiredWars(): Int {
        val now = Instant.now()
        var processedCount = 0

        // Process expired declarations
        val expiredDeclarations = warDeclarations.values.filter { it.expiresAt.isBefore(now) }
        expiredDeclarations.forEach { warDeclarations.remove(it.id) }
        processedCount += expiredDeclarations.size

        // Process expired wars with draw logic
        val expiredWars = wars.values.filter { it.isActive && it.isExpired }
        for (war in expiredWars) {
            if (checkForDrawCondition(war.id)) {
                // End as draw and handle wager refunds
                endWarAsDraw(
                    warId = war.id,
                    reason = "War expired with no clear winner",
                    actorId = UUID.randomUUID() // System UUID
                )
                // Resolve wager as draw (refund both guilds)
                resolveWager(war.id, null)
                logger.info("War ${war.id} ended as draw due to expiration")
            } else {
                // End without winner (shouldn't happen with current logic)
                val endedWar = war.copy(status = WarStatus.ENDED, endedAt = now)
                wars[war.id] = endedWar
            }
            processedCount++
        }

        return processedCount
    }

    override fun validateObjectives(objectives: Set<WarObjective>): Boolean {
        // Basic validation
        return objectives.isNotEmpty() && objectives.size <= 5
    }

    override fun getWarHistory(guildId: UUID, limit: Int): List<War> {
        return wars.values
            .filter { it.declaringGuildId == guildId || it.defendingGuildId == guildId }
            .sortedByDescending { it.declaredAt }
            .take(limit)
    }

    /**
     * Checks if a war should end in a draw based on kill objectives.
     * Returns true if it's a draw situation.
     */
    fun checkForDrawCondition(warId: UUID): Boolean {
        val war = wars[warId] ?: return false
        val stats = warStats[warId] ?: return false
        
        // Check if war has expired
        if (war.isExpired) {
            val killObjective = war.objectives.firstOrNull { it.type == ObjectiveType.KILLS }
            if (killObjective != null) {
                // Check kill counts
                return when {
                    // No kills at all - draw
                    stats.declaringGuildKills == 0 && stats.defendingGuildKills == 0 -> true
                    // Equal kills - draw
                    stats.declaringGuildKills == stats.defendingGuildKills -> true
                    // Neither guild reached target - draw
                    stats.declaringGuildKills < killObjective.targetValue && 
                    stats.defendingGuildKills < killObjective.targetValue -> true
                    else -> false
                }
            }
            // No objectives or expired - draw
            return true
        }
        return false
    }


    override fun createWager(warId: UUID, declaringGuildWager: Int, defendingGuildWager: Int): WarWager? {
        return try {
            val war = wars[warId] ?: return null
            val wager = WarWager(
                warId = warId,
                declaringGuildId = war.declaringGuildId,
                defendingGuildId = war.defendingGuildId,
                declaringGuildWager = declaringGuildWager,
                defendingGuildWager = defendingGuildWager
            )
            warWagers[warId] = wager
            logger.info("Wager created for war $warId: ${wager.totalPot} coins total")
            wager
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error creating wager for war: $warId", e)
            null
        }
    }

    override fun resolveWager(warId: UUID, winnerGuildId: UUID?): WarWager? {
        return try {
            val wager = warWagers[warId] ?: return null
            val resolvedWager = if (winnerGuildId != null) {
                // War ended with winner
                wager.copy(
                    status = WagerStatus.WON,
                    resolvedAt = Instant.now(),
                    winnerGuildId = winnerGuildId
                )
            } else {
                // War ended in draw
                wager.copy(
                    status = WagerStatus.DRAW,
                    resolvedAt = Instant.now()
                )
            }
            warWagers[warId] = resolvedWager
            logger.info("Wager resolved for war $warId: ${if (winnerGuildId != null) "Winner $winnerGuildId" else "Draw"}")
            resolvedWager
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error resolving wager for war: $warId", e)
            null
        }
    }

    override fun getWager(warId: UUID): WarWager? {
        return warWagers[warId]
    }

    // Peace Agreement Methods
    override fun proposePeaceAgreement(
        warId: UUID,
        proposingGuildId: UUID,
        peaceTerms: String,
        offering: PeaceOffering?
    ): PeaceAgreement? {
        return try {
            val war = wars[warId]
            if (war == null || !war.isActive) {
                logger.warn("Cannot propose peace for inactive or non-existent war $warId")
                return null
            }

            // Determine the target guild (the one that didn't propose)
            val targetGuildId = if (war.declaringGuildId == proposingGuildId) {
                war.defendingGuildId
            } else {
                war.declaringGuildId
            }

            val agreement = PeaceAgreement(
                warId = warId,
                proposingGuildId = proposingGuildId,
                targetGuildId = targetGuildId,
                peaceTerms = peaceTerms,
                offering = offering
            )

            peaceAgreements[agreement.id] = agreement
            logger.info("Peace agreement ${agreement.id} proposed for war $warId")
            agreement
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error proposing peace agreement", e)
            null
        }
    }

    override fun acceptPeaceAgreement(agreementId: UUID, acceptingGuildId: UUID): War? {
        return try {
            val agreement = peaceAgreements[agreementId]
            if (agreement == null || !agreement.isValid || agreement.targetGuildId != acceptingGuildId) {
                logger.warn("Cannot accept invalid peace agreement $agreementId")
                return null
            }

            val war = wars[agreement.warId]
            if (war == null || !war.isActive) {
                logger.warn("Cannot accept peace for inactive war ${agreement.warId}")
                return null
            }

            // End the war
            val endedWar = war.copy(
                status = WarStatus.ENDED,
                endedAt = Instant.now(),
                peaceTerms = agreement.peaceTerms
            )

            wars[war.id] = endedWar
            peaceAgreements[agreementId] = agreement.copy(accepted = true, acceptedAt = Instant.now())

            // Apply war farming cooldown to the winner
            applyWarFarmingCooldown(war.declaringGuildId, war.defendingGuildId, war.winner)

            logger.info("Peace agreement accepted, war ${war.id} ended")
            endedWar
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error accepting peace agreement", e)
            null
        }
    }

    override fun rejectPeaceAgreement(agreementId: UUID, rejectingGuildId: UUID): Boolean {
        return try {
            val agreement = peaceAgreements[agreementId]
            if (agreement == null || !agreement.isValid || agreement.targetGuildId != rejectingGuildId) {
                logger.warn("Cannot reject invalid peace agreement $agreementId")
                return false
            }

            peaceAgreements[agreementId] = agreement.copy(rejected = true)
            logger.info("Peace agreement $agreementId rejected")
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error rejecting peace agreement", e)
            false
        }
    }

    override fun getPeaceAgreementsForWar(warId: UUID): List<PeaceAgreement> {
        return peaceAgreements.values.filter { it.warId == warId }
    }

    override fun getPendingPeaceAgreementsForGuild(guildId: UUID): List<PeaceAgreement> {
        return peaceAgreements.values.filter { it.targetGuildId == guildId && it.isValid }
    }

    // Daily War Costs
    override fun applyDailyWarCosts(): Int {
        return try {
            val activeWars = wars.values.filter { it.isActive }
            val affectedGuilds = mutableSetOf<UUID>()

            for (war in activeWars) {
                affectedGuilds.add(war.declaringGuildId)
                affectedGuilds.add(war.defendingGuildId)
            }

            // TODO: Implement actual EXP and money deduction from guilds
            // This would require integration with GuildService and BankService
            logger.info("Applied daily war costs to ${affectedGuilds.size} guilds")

            affectedGuilds.size
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error applying daily war costs", e)
            0
        }
    }

    // War Farming Cooldown Methods
    private fun applyWarFarmingCooldown(declaringGuildId: UUID, defendingGuildId: UUID, winnerGuildId: UUID?) {
        if (winnerGuildId == null) return // No winner for draw

        // Apply cooldown to the winning guild
        val cooldownEnd = Instant.now().plusSeconds(getWarFarmingCooldownSeconds())
        warFarmingCooldowns[winnerGuildId] = cooldownEnd

        logger.info("Applied war farming cooldown to guild $winnerGuildId until $cooldownEnd")
    }

    private fun getWarFarmingCooldownSeconds(): Long {
        // Convert hours from config to seconds
        return 60 * 60L // Default 1 hour in seconds if config not available
        // TODO: Get actual config value when ConfigService is injected
    }

    override fun isGuildInWarFarmingCooldown(guildId: UUID): Boolean {
        val cooldownEnd = warFarmingCooldowns[guildId]
        return cooldownEnd != null && Instant.now().isBefore(cooldownEnd)
    }

    override fun getGuildWarFarmingCooldownEnd(guildId: UUID): java.time.Instant? {
        return warFarmingCooldowns[guildId]
    }

    override fun updateGuildWarFarmingCooldown(guildId: UUID, endTime: java.time.Instant): Boolean {
        return try {
            warFarmingCooldowns[guildId] = endTime
            logger.info("Updated war farming cooldown for guild $guildId until $endTime")
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error updating war farming cooldown", e)
            false
        }
    }

    // War Declaration Cooldown Methods
    override fun isGuildOnWarDeclarationCooldown(guildId: UUID): Boolean {
        val cooldownEnd = warDeclarationCooldowns[guildId]
        return cooldownEnd != null && Instant.now().isBefore(cooldownEnd)
    }

    override fun getWarDeclarationCooldownEnd(guildId: UUID): Instant? {
        return warDeclarationCooldowns[guildId]
    }

    override fun recordWarDeclaration(guildId: UUID) {
        // Default to 24 hours if config not available
        // TODO: Get actual config value when ConfigService is injected
        val cooldownHours = 24L
        val cooldownEnd = Instant.now().plusSeconds(cooldownHours * 3600)
        warDeclarationCooldowns[guildId] = cooldownEnd
        logger.info("Guild $guildId declared war - cooldown until $cooldownEnd")
    }
}
