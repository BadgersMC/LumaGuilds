package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.JoinRequirement
import java.util.UUID

/**
 * Service interface for LFG (Looking For Guild) operations.
 * Handles guild discovery, join validation, and fee collection.
 */
interface LfgService {

    /**
     * Gets all guilds available for LFG (open + has slots).
     *
     * @return List of guilds available for joining, sorted by name.
     */
    fun getAvailableGuilds(): List<Guild>

    /**
     * Checks if a player can join a guild via LFG.
     *
     * @param playerId The player's UUID.
     * @param guild The guild to check.
     * @return LfgJoinResult indicating success or failure reason.
     */
    fun canJoinGuild(playerId: UUID, guild: Guild): LfgJoinResult

    /**
     * Joins a player to a guild via LFG, handling join fee collection.
     *
     * @param playerId The player's UUID.
     * @param guild The guild to join.
     * @return LfgJoinResult with success or failure details.
     */
    fun joinGuild(playerId: UUID, guild: Guild): LfgJoinResult

    /** Admits a durably reserved applicant through the same fee pipeline after each eligibility recheck. */
    fun joinApprovedGuild(playerId: UUID, guild: Guild, eligible: () -> Boolean): LfgJoinResult =
        LfgJoinResult.Error("Application admission is unavailable")

    /**
     * Gets the join requirement details for a guild.
     *
     * @param guild The guild to check.
     * @return JoinRequirement or null if no requirements.
     */
    fun getJoinRequirement(guild: Guild): JoinRequirement?
}

/**
 * Sealed class representing the result of an LFG join operation.
 */
sealed class LfgJoinResult {

    /** Definitive rejection before payment.
     * @property reason Canonical reason no payment was applied.
     */
    class PaymentRejected(val reason: net.lumalyte.lg.domain.gold.GuildGoldRejection) : LfgJoinResult()

    /** Payment restoration is confirmed; no membership was completed.
     * @property transactionId Reference for the confirmed restored payment.
     */
    class PaymentRefunded(val transactionId: UUID) : LfgJoinResult()

    /** Payment or admission outcome needs reconciliation; never invite a retry.
     * @property transactionId Reference staff must reconcile before another payment.
     */
    class PaymentUncertain(val transactionId: UUID) : LfgJoinResult()

    /**
     * Player successfully joined the guild.
     *
     * @property message Success message to display to the player.
     */
    data class Success(val message: String) : LfgJoinResult()

    /**
     * Player does not have enough currency to pay the join fee.
     *
     * @property required The amount of currency required.
     * @property current The amount of currency the player has.
     * @property currencyType The type of currency (e.g., "RAW_GOLD", "Coins").
     */
    data class InsufficientFunds(
        val required: Int,
        val current: Int,
        val currencyType: String
    ) : LfgJoinResult()

    /**
     * The guild has reached its maximum member capacity.
     *
     * @property message Error message to display to the player.
     */
    data class GuildFull(val message: String) : LfgJoinResult()

    /**
     * The player is already a member of a guild.
     *
     * @property message Error message to display to the player.
     */
    data class AlreadyInGuild(val message: String) : LfgJoinResult()

    /**
     * The guild vault is not available for physical currency collection.
     *
     * @property message Error message to display to the player.
     */
    data class VaultUnavailable(val message: String) : LfgJoinResult()

    /**
     * A generic error occurred during the join operation.
     *
     * @property message Error message to display to the player.
     */
    data class Error(val message: String) : LfgJoinResult()
}
