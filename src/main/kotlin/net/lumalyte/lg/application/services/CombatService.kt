package net.lumalyte.lg.application.services

import java.util.UUID

/**
 * Service interface for combat and PvP validation.
 * This service integrates with the server's PvP system to enforce guild mode restrictions.
 */
interface CombatService {

    /**
     * Checks if PvP is allowed between two players.
     * This method considers guild modes, relations, and other guild-based restrictions.
     *
     * @param attackerId The UUID of the attacking player.
     * @param victimId The UUID of the victim player.
     * @return true if PvP is allowed, false otherwise.
     */
    fun isPvpAllowed(attackerId: UUID, victimId: UUID): Boolean

    /**
     * Checks if PvP is allowed in a specific guild's territory.
     * This method considers the territory owner's guild mode and relations.
     *
     * @param playerId The UUID of the player attempting PvP.
     * @param territoryGuildId The UUID of the guild that owns the territory.
     * @return true if PvP is allowed in the territory, false otherwise.
     */
    fun isPvpAllowedInTerritory(playerId: UUID, territoryGuildId: UUID): Boolean

    /**
     * Checks if a player can attack another player in a specific location.
     * This combines territory ownership and guild relation checks.
     *
     * @param attackerId The UUID of the attacking player.
     * @param victimId The UUID of the victim player.
     * @param territoryGuildId The UUID of the guild that owns the territory (null if wilderness).
     * @return true if the attack is allowed, false otherwise.
     */
    fun canAttack(attackerId: UUID, victimId: UUID, territoryGuildId: UUID?): Boolean

    /**
     * Gets the reason why PvP is not allowed between two players.
     *
     * @param attackerId The UUID of the attacking player.
     * @param victimId The UUID of the victim player.
     * @return The reason why PvP is not allowed, or null if PvP is allowed.
     */
    fun getPvpBlockReason(attackerId: UUID, victimId: UUID): String?

    /**
     * Gets the reason why PvP is not allowed in a territory.
     *
     * @param playerId The UUID of the player.
     * @param territoryGuildId The UUID of the guild that owns the territory.
     * @return The reason why PvP is not allowed, or null if PvP is allowed.
     */
    fun getTerritoryPvpBlockReason(playerId: UUID, territoryGuildId: UUID): String?
}
