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

    override fun declareWar(
        declaringGuildId: UUID,
        defendingGuildId: UUID,
        duration: Duration,
        objectives: Set<WarObjective>,
        actorId: UUID
    ): War? {
        return try {
            val war = War.create(
                declaringGuildId = declaringGuildId,
                defendingGuildId = defendingGuildId,
                duration = duration
            )

            wars[war.id] = war

            logger.info("War declared by guild $declaringGuildId against guild $defendingGuildId")
            war
        } catch (e: Exception) {
            logger.error("Error declaring war between $declaringGuildId and $defendingGuildId", e)
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
            logger.error("Error accepting war declaration: $declarationId", e)
            null
        }
    }

    override fun rejectWarDeclaration(declarationId: UUID, actorId: UUID): Boolean {
        return try {
            warDeclarations.remove(declarationId) != null
        } catch (e: Exception) {
            logger.error("Error rejecting war declaration: $declarationId", e)
            false
        }
    }

    override fun cancelWarDeclaration(declarationId: UUID, actorId: UUID): Boolean {
        return try {
            warDeclarations.remove(declarationId) != null
        } catch (e: Exception) {
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
            logger.info("War ended: $warId, winner: $winnerGuildId")
            true
        } catch (e: Exception) {
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
            logger.error("Error resolving wager for war: $warId", e)
            null
        }
    }

    override fun getWager(warId: UUID): WarWager? {
        return warWagers[warId]
    }
}
