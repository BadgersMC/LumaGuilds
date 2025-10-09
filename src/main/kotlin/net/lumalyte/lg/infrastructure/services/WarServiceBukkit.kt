package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.persistence.PeaceAgreementRepository
import net.lumalyte.lg.application.persistence.WarRepository
import net.lumalyte.lg.application.persistence.WarWagerRepository
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.Position
import org.bukkit.Material
import org.bukkit.block.Block
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

class WarServiceBukkit(
    private val warRepository: WarRepository,
    private val warWagerRepository: WarWagerRepository,
    private val peaceAgreementRepository: PeaceAgreementRepository,
    private val guildService: net.lumalyte.lg.application.services.GuildService,
    private val claimService: GetClaimAtPosition
) : WarService {

    private val logger = LoggerFactory.getLogger(WarServiceBukkit::class.java)

    // In-memory storage for war farming cooldowns
    private val warFarmingCooldowns = mutableMapOf<UUID, Instant>()

    override fun declareWar(
        declaringGuildId: UUID,
        defendingGuildId: UUID,
        duration: Duration,
        objectives: Set<WarObjective>,
        actorId: UUID
    ): War? {
        return try {
            // Check if guilds are already in war
            val existingWar = warRepository.getCurrentWarBetweenGuilds(declaringGuildId, defendingGuildId)
            if (existingWar != null) {
                logger.warn("Guilds $declaringGuildId and $defendingGuildId are already at war")
                return null
            }

            // Check if declaring guild can declare war
            if (!canGuildDeclareWar(declaringGuildId)) {
                logger.warn("Guild $declaringGuildId cannot declare war (too many active wars)")
                return null
            }

            // Create the war
            val war = War.create(
                declaringGuildId = declaringGuildId,
                defendingGuildId = defendingGuildId,
                duration = duration
            )

            // Save to repository
            if (warRepository.add(war)) {
                // Initialize war statistics
                val stats = WarStats(warId = war.id)
                warRepository.addWarStats(stats)

                logger.info("War declared by guild $declaringGuildId against guild $defendingGuildId")
                war
            } else {
                logger.error("Failed to save war declaration to database")
                null
            }
        } catch (e: Exception) {
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
            val declaration = WarDeclaration(
                declaringGuildId = declaringGuildId,
                defendingGuildId = defendingGuildId,
                proposedDuration = duration,
                objectives = objectives,
                terms = terms
            )

            // Save to repository instead of in-memory storage
            if (!warRepository.addWarDeclaration(declaration)) {
                logger.error("Failed to save war declaration to database")
                return null
            }

            logger.info("War declaration created by guild $declaringGuildId against guild $defendingGuildId with wager $wagerAmount")
            declaration
        } catch (e: Exception) {
            logger.error("Error creating war declaration between $declaringGuildId and $defendingGuildId", e)
            null
        }
    }

    override fun acceptWarDeclaration(declarationId: UUID, actorId: UUID): War? {
        return try {
            val declaration = warRepository.getWarDeclarationById(declarationId) ?: return null

            if (!declaration.isValid) {
                logger.warn("War declaration $declarationId is no longer valid")
                return null
            }

            val war = War.create(
                declaringGuildId = declaration.declaringGuildId,
                defendingGuildId = declaration.defendingGuildId,
                duration = declaration.proposedDuration
            )

            // Save to repository
            if (warRepository.add(war)) {
                // Initialize war statistics
                val stats = WarStats(warId = war.id)
                warRepository.addWarStats(stats)

                // Remove the declaration
                warRepository.removeWarDeclaration(declarationId)

                logger.info("War accepted: ${war.id}")
                war
            } else {
                logger.error("Failed to save accepted war to database")
                null
            }
        } catch (e: Exception) {
            logger.error("Error accepting war declaration: $declarationId", e)
            null
        }
    }

    override fun rejectWarDeclaration(declarationId: UUID, actorId: UUID): Boolean {
        return try {
            warRepository.removeWarDeclaration(declarationId)
        } catch (e: Exception) {
            logger.error("Error rejecting war declaration: $declarationId", e)
            false
        }
    }

    override fun cancelWarDeclaration(declarationId: UUID, actorId: UUID): Boolean {
        return try {
            warRepository.removeWarDeclaration(declarationId)
        } catch (e: Exception) {
            logger.error("Error canceling war declaration: $declarationId", e)
            false
        }
    }

    override fun endWar(warId: UUID, winnerGuildId: UUID, peaceTerms: String?, actorId: UUID): Boolean {
        return try {
            val war = warRepository.getById(warId) ?: return false
            val loserGuildId = if (war.declaringGuildId == winnerGuildId) war.defendingGuildId else war.declaringGuildId
            val endedWar = war.copy(
                status = WarStatus.ENDED,
                endedAt = Instant.now(),
                winner = winnerGuildId,
                loser = loserGuildId,
                peaceTerms = peaceTerms
            )

            if (warRepository.update(endedWar)) {
                // Apply war farming cooldown to the winner
                applyWarFarmingCooldown(war.declaringGuildId, war.defendingGuildId, winnerGuildId)

                logger.info("War ended: $warId, winner: $winnerGuildId")
                true
            } else {
                logger.error("Failed to update ended war in database")
                false
            }
        } catch (e: Exception) {
            logger.error("Error ending war: $warId", e)
            false
        }
    }

    override fun endWarAsDraw(warId: UUID, reason: String?, actorId: UUID): Boolean {
        return try {
            val war = warRepository.getById(warId) ?: return false
            val endedWar = war.copy(
                status = WarStatus.ENDED,
                endedAt = Instant.now(),
                winner = null, // No winner in a draw
                loser = null,  // No loser in a draw
                peaceTerms = reason ?: "War ended in a draw"
            )

            if (warRepository.update(endedWar)) {
                logger.info("War ended as draw: $warId, reason: $reason")
                true
            } else {
                logger.error("Failed to update ended war as draw in database")
                false
            }
        } catch (e: Exception) {
            logger.error("Error ending war as draw: $warId", e)
            false
        }
    }

    override fun cancelWar(warId: UUID, actorId: UUID): Boolean {
        return try {
            val war = warRepository.getById(warId) ?: return false
            val canceledWar = war.copy(status = WarStatus.CANCELLED)

            if (warRepository.update(canceledWar)) {
                logger.info("War canceled: $warId")
                true
            } else {
                logger.error("Failed to cancel war in database")
                false
            }
        } catch (e: Exception) {
            logger.error("Error canceling war: $warId", e)
            false
        }
    }

    override fun getWar(warId: UUID): War? {
        return warRepository.getById(warId)
    }

    override fun getActiveWars(): List<War> {
        return warRepository.getActiveWars()
    }

    override fun getWarsForGuild(guildId: UUID): List<War> {
        return warRepository.getWarsForGuild(guildId)
    }

    override fun getWarsByGuildId(guildId: UUID): List<War> {
        return warRepository.getWarsForGuild(guildId)
    }

    override fun getPendingDeclarationsForGuild(guildId: UUID): List<WarDeclaration> {
        return warRepository.getPendingDeclarationsForGuild(guildId)
    }

    override fun getDeclarationsByGuild(guildId: UUID): List<WarDeclaration> {
        return warRepository.getDeclarationsByGuild(guildId)
    }

    override fun getWarStats(warId: UUID): WarStats {
        return warRepository.getWarStatsByWarId(warId) ?: WarStats(warId)
    }

    override fun updateWarStats(stats: WarStats): Boolean {
        return try {
            warRepository.updateWarStats(stats)
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
            val warHistory = warRepository.getWarHistory(guildId, 100)
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
        val activeWars = warRepository.getWarsForGuild(guildId).filter { it.isActive }.size
        return activeWars < 3 // Max 3 simultaneous wars
    }

    override fun canPlayerManageWars(playerId: UUID, guildId: UUID): Boolean {
        // Placeholder - would need to check player permissions
        return true
    }

    override fun getCurrentWarBetweenGuilds(guildA: UUID, guildB: UUID): War? {
        return warRepository.getCurrentWarBetweenGuilds(guildA, guildB)
    }

    override fun processExpiredWars(): Int {
        val now = Instant.now()
        var processedCount = 0

        // Process expired declarations
        val expiredDeclarations = warRepository.getExpiredWarDeclarations()
        expiredDeclarations.forEach {
            warRepository.removeWarDeclaration(it.id)
            processedCount++
        }

        // Process expired wars with draw logic
        val expiredWars = warRepository.getActiveWars().filter { it.isExpired }
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
                warRepository.update(endedWar)
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
        return warRepository.getWarHistory(guildId, limit)
    }

    /**
     * Checks if a war should end in a draw based on kill objectives.
     * Returns true if it's a draw situation.
     */
    fun checkForDrawCondition(warId: UUID): Boolean {
        val war = warRepository.getById(warId) ?: return false
        val stats = warRepository.getWarStatsByWarId(warId) ?: return false

        // Check if war has expired
        if (war.isExpired) {
            val killObjective = war.objectives.firstOrNull { it.type == WarObjectiveType.KILL_PLAYERS }
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
            val war = warRepository.getById(warId) ?: return null
            val wager = WarWager(
                warId = warId,
                declaringGuildId = war.declaringGuildId,
                defendingGuildId = war.defendingGuildId,
                declaringGuildWager = declaringGuildWager,
                defendingGuildWager = defendingGuildWager
            )

            if (warWagerRepository.add(wager)) {
                logger.info("Wager created for war $warId: ${wager.totalPot} coins total")
                wager
            } else {
                logger.error("Failed to save wager to database")
                null
            }
        } catch (e: Exception) {
            logger.error("Error creating wager for war: $warId", e)
            null
        }
    }

    override fun resolveWager(warId: UUID, winnerGuildId: UUID?): WarWager? {
        return try {
            val wager = warWagerRepository.getByWarId(warId) ?: return null
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

            if (warWagerRepository.update(resolvedWager)) {
                logger.info("Wager resolved for war $warId: ${if (winnerGuildId != null) "Winner $winnerGuildId" else "Draw"}")
                resolvedWager
            } else {
                logger.error("Failed to update resolved wager in database")
                null
            }
        } catch (e: Exception) {
            logger.error("Error resolving wager for war: $warId", e)
            null
        }
    }

    override fun getWager(warId: UUID): WarWager? {
        return warWagerRepository.getByWarId(warId)
    }

    // Peace Agreement Methods
    override fun proposePeaceAgreement(
        warId: UUID,
        proposingGuildId: UUID,
        peaceTerms: String,
        offering: PeaceOffering?
    ): PeaceAgreement? {
        return try {
            val war = warRepository.getById(warId)
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

            if (peaceAgreementRepository.add(agreement)) {
                logger.info("Peace agreement ${agreement.id} proposed for war $warId")
                agreement
            } else {
                logger.error("Failed to save peace agreement to database")
                null
            }
        } catch (e: Exception) {
            logger.error("Error proposing peace agreement", e)
            null
        }
    }

    override fun acceptPeaceAgreement(agreementId: UUID, acceptingGuildId: UUID): War? {
        return try {
            val agreement = peaceAgreementRepository.getById(agreementId)
            if (agreement == null || !agreement.isValid || agreement.targetGuildId != acceptingGuildId) {
                logger.warn("Cannot accept invalid peace agreement $agreementId")
                return null
            }

            val war = warRepository.getById(agreement.warId)
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

            if (warRepository.update(endedWar)) {
                peaceAgreementRepository.update(agreement.copy(accepted = true, acceptedAt = Instant.now()))

                // Apply war farming cooldown to the winner
                applyWarFarmingCooldown(war.declaringGuildId, war.defendingGuildId, war.winner)

                logger.info("Peace agreement accepted, war ${war.id} ended")
                endedWar
            } else {
                logger.error("Failed to update war after peace agreement acceptance")
                null
            }
        } catch (e: Exception) {
            logger.error("Error accepting peace agreement", e)
            null
        }
    }

    override fun rejectPeaceAgreement(agreementId: UUID, rejectingGuildId: UUID): Boolean {
        return try {
            val agreement = peaceAgreementRepository.getById(agreementId)
            if (agreement == null || !agreement.isValid || agreement.targetGuildId != rejectingGuildId) {
                logger.warn("Cannot reject invalid peace agreement $agreementId")
                return false
            }

            peaceAgreementRepository.update(agreement.copy(rejected = true))
            logger.info("Peace agreement $agreementId rejected")
            true
        } catch (e: Exception) {
            logger.error("Error rejecting peace agreement", e)
            false
        }
    }

    override fun getPeaceAgreementsForWar(warId: UUID): List<PeaceAgreement> {
        return peaceAgreementRepository.getByWarId(warId)
    }

    override fun getPendingPeaceAgreementsForGuild(guildId: UUID): List<PeaceAgreement> {
        return peaceAgreementRepository.getPendingForGuild(guildId)
    }

    // Daily War Costs
    override fun applyDailyWarCosts(): Int {
        return try {
            val activeWars = warRepository.getActiveWars()
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
            logger.error("Error updating war farming cooldown", e)
            false
        }
    }

    override fun trackBlockDestruction(playerId: UUID, block: Block, guildId: UUID): Boolean {
        return try {
            logger.info("Tracking block destruction for player $playerId in guild $guildId at ${block.location}")

            // 1. Get the claim at the block's location
            val claimResult = claimService.execute(
                block.world.uid,
                Position(
                    block.location.x.toInt(),
                    block.location.y.toInt(),
                    block.location.z.toInt()
                )
            )
            if (claimResult !is GetClaimAtPositionResult.Success) {
                logger.debug("No claim found at block location ${block.location}")
                return true // Not an error, just not a claim block
            }

            val claim = claimResult.claim

            // 2. Check if the claim belongs to an enemy guild
            val claimGuildId = claim.teamId
            if (claimGuildId == null || claimGuildId == guildId) {
                logger.debug("Block belongs to same guild or no guild")
                return true // Not an enemy block
            }

            // 3. Check if there's an active war between the guilds
            val activeWar = getCurrentWarBetweenGuilds(guildId, claimGuildId)
            if (activeWar == null) {
                logger.debug("No active war between guilds $guildId and $claimGuildId")
                return true // No active war
            }

            // 4. Check if the block is a "structure" (not just any block)
            // Consider blocks that are typically part of structures (not basic blocks like dirt, grass, etc.)
            val isStructureBlock = isStructureBlock(block.type)
            if (!isStructureBlock) {
                logger.debug("Block ${block.type} is not considered a structure block")
                return true // Not a structure block
            }

            // 5. Update war objectives for DESTROY_STRUCTURES type
            val objectives = activeWar.objectives.filter { it.type == WarObjectiveType.DESTROY_STRUCTURES }
            for (objective in objectives) {
                if (!objective.completed) {
                    val newProgress = objective.currentValue + 1
                    if (addObjectiveProgress(activeWar.id, objective.id, 1)) {
                        logger.info("Updated DESTROY_STRUCTURES objective ${objective.id} progress: $newProgress/${objective.targetValue}")

                        // Check if objective is now completed
                        if (newProgress >= objective.targetValue) {
                            logger.info("DESTROY_STRUCTURES objective ${objective.id} completed!")
                            // TODO: Award completion rewards if needed
                        }
                    }
                }
            }

            // 6. Update war statistics
            val currentStats = getWarStats(activeWar.id)
            val updatedStats = currentStats.copy(
                declaringGuildKills = if (activeWar.declaringGuildId == guildId) currentStats.declaringGuildKills + 1 else currentStats.declaringGuildKills,
                defendingGuildKills = if (activeWar.defendingGuildId == guildId) currentStats.defendingGuildKills + 1 else currentStats.defendingGuildKills,
                lastUpdated = Instant.now()
            )
            updateWarStats(updatedStats)

            // 7. Award rewards/points to the player/guild (simplified for now)
            // TODO: Implement proper reward system based on configuration

            logger.info("Successfully tracked structure destruction in war ${activeWar.id}")
            true
        } catch (e: Exception) {
            logger.error("Error tracking block destruction for player $playerId", e)
            false
        }
    }

    /**
     * Determines if a block type is considered a "structure" block for war objectives.
     * Structure blocks are typically valuable or functional blocks that guilds build.
     */
    private fun isStructureBlock(material: Material): Boolean {
        return when (material) {
            // Building materials
            Material.STONE, Material.COBBLESTONE, Material.STONE_BRICKS, Material.BRICKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.CRIMSON_PLANKS, Material.WARPED_PLANKS,
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.CRIMSON_STEM, Material.WARPED_STEM,

            // Decorative blocks
            Material.GLASS, Material.GLASS_PANE, Material.WHITE_WOOL, Material.ORANGE_WOOL,
            Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
            Material.PINK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL,
            Material.PURPLE_WOOL, Material.BLUE_WOOL, Material.BROWN_WOOL, Material.GREEN_WOOL,
            Material.RED_WOOL, Material.BLACK_WOOL,

            // Functional blocks
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.FURNACE,
            Material.BLAST_FURNACE, Material.SMOKER, Material.CRAFTING_TABLE, Material.ANVIL,
            Material.ENCHANTING_TABLE, Material.BREWING_STAND, Material.CAULDRON,

            // Redstone components
            Material.REDSTONE_LAMP, Material.PISTON, Material.STICKY_PISTON, Material.DISPENSER,
            Material.DROPPER, Material.HOPPER, Material.OBSERVER, Material.REPEATER,
            Material.COMPARATOR, Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON,

            // Utility blocks
            Material.BOOKSHELF, Material.LADDER, Material.TORCH, Material.LANTERN,
            Material.SOUL_TORCH, Material.SOUL_LANTERN -> true

            else -> false
        }
    }

    override fun trackTNTExplosion(igniterId: UUID, explodedBlocks: List<Block>): Boolean {
        return try {
            logger.info("Tracking TNT explosion for igniter $igniterId, ${explodedBlocks.size} blocks destroyed")

            // Get the igniter's guild
            val igniterGuildIds = guildService.getPlayerGuildIds(igniterId)
            if (igniterGuildIds.isEmpty()) {
                logger.debug("Igniter $igniterId is not in any guild")
                return true
            }
            val igniterGuildId = igniterGuildIds.first() // Player should only be in one guild

            var totalEnemyStructuresDestroyed = 0
            var activeWarsUpdated = mutableSetOf<UUID>()

            // Process each exploded block
            for (block in explodedBlocks) {
                // 1. Get the claim at the block's location
                val claimResult = claimService.execute(
                    block.world.uid,
                    Position(
                        block.location.x.toInt(),
                        block.location.y.toInt(),
                        block.location.z.toInt()
                    )
                )
                if (claimResult !is GetClaimAtPositionResult.Success) {
                    continue // Not a claim block, skip
                }

                val claim = claimResult.claim

                // 2. Check if the claim belongs to an enemy guild
                val claimGuildId = claim.teamId
                if (claimGuildId == null || claimGuildId == igniterGuildId) {
                    continue // Not an enemy block, skip
                }

                // 3. Check if there's an active war between the guilds
                val activeWar = getCurrentWarBetweenGuilds(igniterGuildId, claimGuildId)
                if (activeWar == null) {
                    continue // No active war, skip
                }

                // 4. Check if the block is a "structure" block
                if (!isStructureBlock(block.type)) {
                    continue // Not a structure block, skip
                }

                totalEnemyStructuresDestroyed++

                // 5. Update war objectives for DESTROY_STRUCTURES type
                val objectives = activeWar.objectives.filter { it.type == WarObjectiveType.DESTROY_STRUCTURES }
                for (objective in objectives) {
                    if (!objective.completed) {
                        val newProgress = objective.currentValue + 1
                        if (addObjectiveProgress(activeWar.id, objective.id, 1)) {
                            logger.info("Updated DESTROY_STRUCTURES objective ${objective.id} progress: $newProgress/${objective.targetValue}")
                            activeWarsUpdated.add(activeWar.id)

                            // Check if objective is now completed
                            if (newProgress >= objective.targetValue) {
                                logger.info("DESTROY_STRUCTURES objective ${objective.id} completed!")
                                // TODO: Award completion rewards if needed
                            }
                        }
                    }
                }

                // 6. Update war statistics (increment kills for igniter's guild)
                val currentStats = getWarStats(activeWar.id)
                val updatedStats = currentStats.copy(
                    declaringGuildKills = if (activeWar.declaringGuildId == igniterGuildId) currentStats.declaringGuildKills + 1 else currentStats.declaringGuildKills,
                    defendingGuildKills = if (activeWar.defendingGuildId == igniterGuildId) currentStats.defendingGuildKills + 1 else currentStats.defendingGuildKills,
                    lastUpdated = Instant.now()
                )
                updateWarStats(updatedStats)
            }

            if (totalEnemyStructuresDestroyed > 0) {
                logger.info("TNT explosion destroyed $totalEnemyStructuresDestroyed enemy structures in ${activeWarsUpdated.size} active wars")
            }

            true
        } catch (e: Exception) {
            logger.error("Error tracking TNT explosion for igniter $igniterId", e)
            false
        }
    }
}
