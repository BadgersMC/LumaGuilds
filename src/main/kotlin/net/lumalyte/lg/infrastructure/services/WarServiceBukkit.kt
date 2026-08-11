package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.events.GuildWarDeclaredEvent
import net.lumalyte.lg.domain.events.GuildWarEndEvent
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class WarServiceBukkit(
    private val configService: ConfigService,
    private val bankService: net.lumalyte.lg.application.services.BankService,
    private val progressionRepository: ProgressionRepository,
    private val progressionConfigService: ProgressionConfigService
) : WarService {

    private val logger = LoggerFactory.getLogger(WarServiceBukkit::class.java)

    // In-memory storage for now - would need database persistence in production
    // Thread-safe collections for concurrent access from multiple guilds/players
    private val wars = ConcurrentHashMap<UUID, War>()
    private val warDeclarations = ConcurrentHashMap<UUID, WarDeclaration>()
    private val warStats = ConcurrentHashMap<UUID, WarStats>()
    private val warWagers = ConcurrentHashMap<UUID, WarWager>()

    // Tracks per-killer kill timestamps per victim, for anti-farming enforcement
    // (REQ-008: kill_cooldown_minutes + same_player_kill_limit).
    private val killTracking = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, MutableList<Instant>>>()
    private val peaceAgreements = ConcurrentHashMap<UUID, PeaceAgreement>()
    private val warFarmingCooldowns = ConcurrentHashMap<UUID, Instant>()
    private val warDeclarationCooldowns = ConcurrentHashMap<UUID, Instant>()

    override fun declareWar(
        declaringGuildId: UUID,
        defendingGuildId: UUID,
        duration: Duration,
        objectives: Set<WarObjective>,
        actorId: UUID
    ): WarDeclaration? {
        // REQ-024: no auto-accept — a declaration requires the defending guild's
        // accept/decline. This is exactly the createWarDeclaration flow.
        return createWarDeclaration(
            declaringGuildId = declaringGuildId,
            defendingGuildId = defendingGuildId,
            duration = duration,
            objectives = objectives,
            wagerAmount = 0,
            terms = null,
            actorId = actorId
        )
    }

    /**
     * Creates a war declaration that requires acceptance
     */
    override fun createWarDeclaration(
        declaringGuildId: UUID,
        defendingGuildId: UUID,
        duration: Duration,
        objectives: Set<WarObjective>,
        wagerAmount: Int,
        terms: String?,
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

            // Check war slot limit (REQ-008): config max, refined upward by progression
            val currentWars = getWarsForGuild(declaringGuildId).filter { it.isActive }
            val config = configService.loadConfig()
            val maxWars = maxWarsForGuild(declaringGuildId, config.combat.maxSimultaneousWars)

            if (currentWars.size >= maxWars) {
                logger.warn("Guild $declaringGuildId has reached war limit: ${currentWars.size}/$maxWars")
                return null
            }

            val declaration = WarDeclaration(
                declaringGuildId = declaringGuildId,
                defendingGuildId = defendingGuildId,
                proposedDuration = effectiveWarDuration(duration),
                objectives = objectives,
                terms = terms,
                wagerAmount = wagerAmount
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
            if (!declaration.isValid) {
                logger.warn("Cannot accept expired/already-responded declaration $declarationId")
                return null
            }

            // REQ-024: acceptance activates the war — status ACTIVE, startedAt set,
            // declaration objectives carried over, stats initialized.
            val war = War.create(
                declaringGuildId = declaration.declaringGuildId,
                defendingGuildId = declaration.defendingGuildId,
                duration = declaration.proposedDuration
            ).copy(
                status = WarStatus.ACTIVE,
                startedAt = Instant.now(),
                objectives = declaration.objectives
            )

            wars[war.id] = war
            warStats[war.id] = WarStats(warId = war.id)
            warDeclarations.remove(declarationId)

            logger.info("War accepted and ACTIVE: ${war.id} (${war.declaringGuildId} vs ${war.defendingGuildId})")
            Bukkit.getPluginManager().callEvent(GuildWarDeclaredEvent(war.declaringGuildId, war.defendingGuildId, actorId))
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

            // Award configured win/lose XP (REQ-008)
            awardWarExperience(winnerGuildId, loserGuildId)

            logger.info("War ended: $warId, winner: $winnerGuildId")
            Bukkit.getPluginManager().callEvent(GuildWarEndEvent(warId, winnerGuildId, loserGuildId, war.declaringGuildId, war.defendingGuildId))
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

    /**
     * Enforce the configured war duration cap (REQ-008): a war cannot last
     * longer than `combat.war_duration_hours`.
     */
    private fun effectiveWarDuration(requested: Duration): Duration =
        effectiveWarDuration(requested, configService.loadConfig().combat.warDurationHours)

    /**
     * The effective max simultaneous wars for a guild (REQ-008): the config
     * `max_simultaneous_wars` base, refined upward by progression war-slot
     * rewards at the guild's reached levels.
     */
    private fun maxWarsForGuild(guildId: UUID, configMax: Int): Int {
        val progression = progressionRepository.getGuildProgression(guildId)
        val progressionConfig = progressionConfigService.getProgressionConfig()
        return maxWarsForGuild(
            currentLevel = progression?.currentLevel,
            configMax = configMax,
            levelRewards = progressionConfig.getActiveLevelRewards()
        )
    }

    /**
     * Awards the configured war XP (REQ-008): `war_win_experience` to the
     * winner, `war_lose_experience` to the loser. Best-effort; progression DB
     * failures are logged, not thrown.
     */
    private fun awardWarExperience(winnerGuildId: UUID?, loserGuildId: UUID?) {
        val combat = configService.loadConfig().combat
        if (winnerGuildId != null && combat.warWinExperience > 0) {
            awardGuildExperience(winnerGuildId, combat.warWinExperience)
        }
        if (loserGuildId != null && combat.warLoseExperience > 0) {
            awardGuildExperience(loserGuildId, combat.warLoseExperience)
        }
    }

    private fun awardGuildExperience(guildId: UUID, amount: Int) {
        try {
            val progression = progressionRepository.getGuildProgression(guildId) ?: return
            progressionRepository.saveGuildProgression(
                progression.copy(totalExperience = progression.totalExperience + amount)
            )
        } catch (e: Exception) {
            logger.error("Failed to award $amount XP to guild $guildId", e)
        }
    }

    /**
     * Awards the configured kill XP (REQ-008) to the killer's guild for a war kill.
     */
    fun awardWarKillExperience(killerGuildId: UUID) {
        val killXp = configService.loadConfig().combat.killExperience
        if (killXp > 0) {
            awardGuildExperience(killerGuildId, killXp)
        }
    }

    /**
     * Records a war kill for anti-farming enforcement and returns whether the
     * kill should be treated as farming (REQ-008: `kill_cooldown_minutes` +
     * `same_player_kill_limit`). Returns true when the same killer has already
     * exceeded the per-victim kill limit within the cooldown window — callers
     * should suppress XP/rewards for such kills.
     */
    fun recordWarKillAndCheckFarming(killerId: UUID, victimId: UUID): Boolean {
        val combat = configService.loadConfig().combat
        if (combat.killCooldownMinutes <= 0 || combat.samePlayerKillLimit <= 0) {
            // Anti-farming disabled — always allow
            return false
        }

        val now = Instant.now()
        val cooldownStart = now.minusSeconds(combat.killCooldownMinutes * 60L)

        val byVictim = killTracking.computeIfAbsent(killerId) { ConcurrentHashMap() }
        val kills = byVictim.computeIfAbsent(victimId) { mutableListOf() }

        synchronized(kills) {
            // Drop timestamps outside the cooldown window
            kills.removeAll { it.isBefore(cooldownStart) }
            kills.add(now)

            // Farming when the kill limit is exceeded within the window
            return kills.size > combat.samePlayerKillLimit
        }
    }

    /**
     * Checks whether a guild is at or above the configured max simultaneous wars (REQ-008).
     */
    override fun canGuildDeclareWar(guildId: UUID): Boolean {
        val activeWars = wars.values.filter {
            it.isActive && (it.declaringGuildId == guildId || it.defendingGuildId == guildId)
        }.size
        return activeWars < configService.loadConfig().combat.maxSimultaneousWars
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

        // Process expired wars with draw logic.
        // REQ-008: honour `war_end_grace_period_minutes` — a war is not force-ended
        // until duration + grace period have both elapsed.
        val graceSeconds = configService.loadConfig().combat.warEndGracePeriodMinutes * 60L
        val expiredWars = wars.values.filter { war ->
            war.isActive && war.startedAt != null &&
                war.startedAt!!.plus(war.duration).plusSeconds(graceSeconds).isBefore(now)
        }
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

            // Validate that war is pending/active and doesn't already have a wager
            if (warWagers.containsKey(warId)) {
                logger.warn("Cannot create wager - war $warId already has a wager")
                return null
            }

            // Validate wager amounts are positive
            if (declaringGuildWager < 0 || defendingGuildWager < 0) {
                logger.warn("Cannot create wager - negative amounts not allowed")
                return null
            }

            // Check if both guilds have sufficient funds (store B: unified guild balance)
            val declaringBalance = bankService.getBalance(war.declaringGuildId)
            val defendingBalance = bankService.getBalance(war.defendingGuildId)

            if (declaringBalance < declaringGuildWager) {
                logger.warn("Declaring guild ${war.declaringGuildId} has insufficient funds for wager: $declaringBalance < $declaringGuildWager")
                return null
            }

            if (defendingBalance < defendingGuildWager) {
                logger.warn("Defending guild ${war.defendingGuildId} has insufficient funds for wager: $defendingBalance < $defendingGuildWager")
                return null
            }

            val wagerDesc = "War wager for war ${warId.toString().substring(0, 8)}"

            // Deduct wager from declaring guild
            val declaringDeductSuccess = if (declaringGuildWager > 0) {
                bankService.deductFromGuildBank(war.declaringGuildId, declaringGuildWager, wagerDesc)
            } else true

            if (!declaringDeductSuccess) {
                logger.error("Failed to deduct wager from declaring guild ${war.declaringGuildId}")
                return null
            }

            // Deduct wager from defending guild
            val defendingDeductSuccess = if (defendingGuildWager > 0) {
                bankService.deductFromGuildBank(war.defendingGuildId, defendingGuildWager, wagerDesc)
            } else true

            if (!defendingDeductSuccess) {
                logger.error("Failed to deduct wager from defending guild ${war.defendingGuildId}")
                // ROLLBACK: Refund declaring guild
                if (declaringGuildWager > 0) {
                    val rollbackSuccess = bankService.creditToGuildBank(
                        war.declaringGuildId,
                        declaringGuildWager,
                        "Wager rollback - defending guild deduction failed"
                    )
                    if (!rollbackSuccess) {
                        logger.error("CRITICAL: Failed to rollback wager for declaring guild ${war.declaringGuildId}! Manual intervention required.")
                    }
                }
                return null
            }

            // Create wager object
            val wager = WarWager(
                warId = warId,
                declaringGuildId = war.declaringGuildId,
                defendingGuildId = war.defendingGuildId,
                declaringGuildWager = declaringGuildWager,
                defendingGuildWager = defendingGuildWager
            )
            warWagers[warId] = wager
            logger.info("Wager created for war $warId: ${wager.totalPot} coins total (${declaringGuildWager} + ${defendingGuildWager})")
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

            // Prevent resolving already-resolved wagers
            if (wager.status != WagerStatus.ESCROWED) {
                logger.warn("Cannot resolve wager - already resolved with status ${wager.status}")
                return null
            }

            if (winnerGuildId != null) {
                // War ended with winner - pay out total pot to winner
                val totalPot = wager.totalPot

                if (totalPot > 0) {
                    val depositSuccess = bankService.creditToGuildBank(
                        winnerGuildId,
                        totalPot,
                        "War wager winnings from war ${warId.toString().substring(0, 8)}"
                    )

                    if (!depositSuccess) {
                        logger.error("CRITICAL: Failed to deposit wager winnings of $totalPot to winner guild $winnerGuildId! Manual intervention required.")
                        // Don't mark as resolved if payout failed
                        return null
                    }

                    logger.info("Paid out $totalPot coins to winner guild $winnerGuildId from war wager")
                }

                val resolvedWager = wager.copy(
                    status = WagerStatus.WON,
                    resolvedAt = Instant.now(),
                    winnerGuildId = winnerGuildId
                )
                warWagers[warId] = resolvedWager
                logger.info("Wager resolved for war $warId: Winner $winnerGuildId received ${wager.totalPot} coins")
                resolvedWager
            } else {
                // War ended in draw - refund both guilds
                var refundSuccess = true

                // Refund declaring guild
                if (wager.declaringGuildWager > 0) {
                    if (!bankService.creditToGuildBank(
                            wager.declaringGuildId,
                            wager.declaringGuildWager,
                            "War wager refund (draw) from war ${warId.toString().substring(0, 8)}"
                    )) {
                        logger.error("CRITICAL: Failed to refund wager of ${wager.declaringGuildWager} to declaring guild ${wager.declaringGuildId}! Manual intervention required.")
                        refundSuccess = false
                    } else {
                        logger.info("Refunded ${wager.declaringGuildWager} coins to declaring guild ${wager.declaringGuildId}")
                    }
                }

                // Refund defending guild
                if (wager.defendingGuildWager > 0) {
                    if (!bankService.creditToGuildBank(
                            wager.defendingGuildId,
                            wager.defendingGuildWager,
                            "War wager refund (draw) from war ${warId.toString().substring(0, 8)}"
                    )) {
                        logger.error("CRITICAL: Failed to refund wager of ${wager.defendingGuildWager} to defending guild ${wager.defendingGuildId}! Manual intervention required.")
                        refundSuccess = false
                    } else {
                        logger.info("Refunded ${wager.defendingGuildWager} coins to defending guild ${wager.defendingGuildId}")
                    }
                }

                if (!refundSuccess) {
                    // Don't mark as resolved if refunds failed
                    return null
                }

                val resolvedWager = wager.copy(
                    status = WagerStatus.DRAW,
                    resolvedAt = Instant.now()
                )
                warWagers[warId] = resolvedWager
                logger.info("Wager resolved for war $warId: Draw - both guilds refunded")
                resolvedWager
            }
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
    // NOTE: This method is deprecated - DailyWarCostsServiceBukkit handles actual cost deduction
    // This just returns the count of affected guilds for backward compatibility
    override fun applyDailyWarCosts(): Int {
        return try {
            val activeWars = wars.values.filter { it.isActive }
            val affectedGuilds = mutableSetOf<UUID>()

            for (war in activeWars) {
                affectedGuilds.add(war.declaringGuildId)
                affectedGuilds.add(war.defendingGuildId)
            }

            logger.debug("${affectedGuilds.size} guilds in active wars (actual costs applied by DailyWarCostsService)")

            affectedGuilds.size
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error counting guilds in active wars", e)
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
        val config = configService.loadConfig()
        return config.combat.warFarmingCooldownHours * 3600L
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
        val config = configService.loadConfig()
        val cooldownHours = config.combat.warDeclarationCooldownHours.toLong()
        val cooldownEnd = Instant.now().plusSeconds(cooldownHours * 3600)
        warDeclarationCooldowns[guildId] = cooldownEnd
        logger.info("Guild $guildId declared war - cooldown until $cooldownEnd")
    }

    companion object {
        /**
         * REQ-008: caps a requested war duration at `combat.war_duration_hours`.
         */
        fun effectiveWarDuration(requested: Duration, configWarDurationHours: Int): Duration {
            val configDuration = Duration.ofHours(configWarDurationHours.toLong())
            return if (requested > configDuration) configDuration else requested
        }

        /**
         * REQ-008: effective max simultaneous wars = config `max_simultaneous_wars`
         * base, refined upward by the highest progression war-slot reward among the
         * levels the guild has reached. `levelRewards` is keyed by level number.
         */
        fun maxWarsForGuild(currentLevel: Int?, configMax: Int, levelRewards: Map<Int, net.lumalyte.lg.config.LevelRewardConfig>): Int {
            if (currentLevel == null) return configMax
            var maxWars = configMax
            for (level in 1..currentLevel) {
                val wars = levelRewards[level]?.warSlots ?: configMax
                if (wars > maxWars) maxWars = wars
            }
            return maxWars
        }
    }
}
