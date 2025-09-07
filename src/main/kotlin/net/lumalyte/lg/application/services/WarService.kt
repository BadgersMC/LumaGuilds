package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.*
import java.time.Duration
import java.util.UUID

/**
 * Service interface for managing wars between guilds.
 */
interface WarService {

    /**
     * Declares war on another guild.
     *
     * @param declaringGuildId The ID of the declaring guild.
     * @param defendingGuildId The ID of the defending guild.
     * @param duration The duration of the war.
     * @param objectives The war objectives.
     * @param actorId The ID of the player declaring war.
     * @return The war declaration if successful, null otherwise.
     */
    fun declareWar(
        declaringGuildId: UUID,
        defendingGuildId: UUID,
        duration: Duration = Duration.ofDays(7),
        objectives: Set<WarObjective> = emptySet(),
        actorId: UUID
    ): War?

    /**
     * Accepts a war declaration.
     *
     * @param declarationId The ID of the war declaration.
     * @param actorId The ID of the player accepting.
     * @return The created war if successful, null otherwise.
     */
    fun acceptWarDeclaration(declarationId: UUID, actorId: UUID): War?

    /**
     * Rejects a war declaration.
     *
     * @param declarationId The ID of the war declaration.
     * @param actorId The ID of the player rejecting.
     * @return true if successful, false otherwise.
     */
    fun rejectWarDeclaration(declarationId: UUID, actorId: UUID): Boolean

    /**
     * Cancels a war declaration.
     *
     * @param declarationId The ID of the war declaration.
     * @param actorId The ID of the player cancelling.
     * @return true if successful, false otherwise.
     */
    fun cancelWarDeclaration(declarationId: UUID, actorId: UUID): Boolean

    /**
     * Ends a war with a winner.
     *
     * @param warId The ID of the war.
     * @param winnerGuildId The ID of the winning guild.
     * @param peaceTerms Optional peace terms.
     * @param actorId The ID of the player ending the war.
     * @return true if successful, false otherwise.
     */
    fun endWar(warId: UUID, winnerGuildId: UUID, peaceTerms: String? = null, actorId: UUID): Boolean

    /**
     * Ends a war as a draw (no winner).
     *
     * @param warId The ID of the war.
     * @param reason Optional reason for the draw.
     * @param actorId The ID of the player/system ending the war.
     * @return true if successful, false otherwise.
     */
    fun endWarAsDraw(warId: UUID, reason: String? = null, actorId: UUID): Boolean

    /**
     * Cancels an active war.
     *
     * @param warId The ID of the war.
     * @param actorId The ID of the player cancelling.
     * @return true if successful, false otherwise.
     */
    fun cancelWar(warId: UUID, actorId: UUID): Boolean

    /**
     * Gets a war by ID.
     *
     * @param warId The ID of the war.
     * @return The war if found, null otherwise.
     */
    fun getWar(warId: UUID): War?

    /**
     * Gets all active wars.
     *
     * @return List of active wars.
     */
    fun getActiveWars(): List<War>

    /**
     * Gets wars for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of wars involving the guild.
     */
    fun getWarsForGuild(guildId: UUID): List<War>

    /**
     * Gets pending war declarations for a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of pending declarations that the guild can respond to.
     */
    fun getPendingDeclarationsForGuild(guildId: UUID): List<WarDeclaration>

    /**
     * Gets war declarations sent by a guild.
     *
     * @param guildId The ID of the guild.
     * @return List of declarations sent by the guild.
     */
    fun getDeclarationsByGuild(guildId: UUID): List<WarDeclaration>

    /**
     * Gets war statistics.
     *
     * @param warId The ID of the war.
     * @return The war statistics.
     */
    fun getWarStats(warId: UUID): WarStats

    /**
     * Updates war statistics.
     *
     * @param stats The updated statistics.
     * @return true if successful, false otherwise.
     */
    fun updateWarStats(stats: WarStats): Boolean

    /**
     * Adds progress to a war objective.
     *
     * @param warId The ID of the war.
     * @param objectiveId The ID of the objective.
     * @param progress The amount of progress to add.
     * @return true if successful, false otherwise.
     */
    fun addObjectiveProgress(warId: UUID, objectiveId: UUID, progress: Int): Boolean

    /**
     * Checks if a guild can declare war.
     *
     * @param guildId The ID of the guild.
     * @return true if the guild can declare war, false otherwise.
     */
    fun canGuildDeclareWar(guildId: UUID): Boolean

    /**
     * Checks if a player can manage wars for their guild.
     *
     * @param playerId The ID of the player.
     * @param guildId The ID of the guild.
     * @return true if the player can manage wars, false otherwise.
     */
    fun canPlayerManageWars(playerId: UUID, guildId: UUID): Boolean

    /**
     * Gets the current war between two guilds.
     *
     * @param guildA The first guild.
     * @param guildB The second guild.
     * @return The active war between the guilds, or null if none exists.
     */
    fun getCurrentWarBetweenGuilds(guildA: UUID, guildB: UUID): War?

    /**
     * Processes expired wars and declarations.
     *
     * @return The number of items processed.
     */
    fun processExpiredWars(): Int

    /**
     * Validates war objectives.
     *
     * @param objectives The objectives to validate.
     * @return true if valid, false otherwise.
     */
    fun validateObjectives(objectives: Set<WarObjective>): Boolean

    /**
     * Creates a wager for a war.
     *
     * @param warId The ID of the war.
     * @param declaringGuildWager The declaring guild's wager amount.
     * @param defendingGuildWager The defending guild's wager amount.
     * @return The created wager if successful, null otherwise.
     */
    fun createWager(warId: UUID, declaringGuildWager: Int, defendingGuildWager: Int): WarWager?

    /**
     * Resolves a wager when a war ends.
     *
     * @param warId The ID of the war.
     * @param winnerGuildId The winner guild ID, or null for draw.
     * @return The resolved wager if successful, null otherwise.
     */
    fun resolveWager(warId: UUID, winnerGuildId: UUID?): WarWager?

    /**
     * Gets a wager for a war.
     *
     * @param warId The ID of the war.
     * @return The wager if found, null otherwise.
     */
    fun getWager(warId: UUID): WarWager?

    /**
     * Gets war history for a guild.
     *
     * @param guildId The ID of the guild.
     * @param limit The maximum number of wars to return.
     * @return List of past wars for the guild.
     */
    fun getWarHistory(guildId: UUID, limit: Int = 20): List<War>

    /**
     * Gets the win/loss ratio for a guild.
     *
     * @param guildId The ID of the guild.
     * @return The win/loss ratio.
     */
    fun getWinLossRatio(guildId: UUID): Double
}
