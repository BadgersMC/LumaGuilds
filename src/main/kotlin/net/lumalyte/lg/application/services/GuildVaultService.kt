package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.VaultStatus
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Service interface for managing guild vault operations.
 * Handles physical chest placement, item storage, and capacity management.
 */
interface GuildVaultService {

    /**
     * Places a guild vault chest at the specified location.
     *
     * @param guild The guild placing the vault.
     * @param location The location where the chest is placed.
     * @param player The player placing the chest.
     * @return Result indicating success or failure with message.
     */
    fun placeVaultChest(guild: Guild, location: Location, player: Player): VaultResult<Guild>

    /**
     * Removes the guild vault chest, optionally dropping items.
     *
     * @param guild The guild whose vault is being removed.
     * @param dropItems Whether to drop all items at the chest location.
     * @return Result indicating success or failure.
     */
    fun removeVaultChest(guild: Guild, dropItems: Boolean): VaultResult<Guild>

    /**
     * Opens the vault inventory for a player.
     *
     * @param player The player opening the vault.
     * @param guild The guild whose vault is being accessed.
     * @return Result indicating success or failure.
     */
    fun openVaultInventory(player: Player, guild: Guild): VaultResult<Unit>

    /**
     * Gets the vault inventory items for a guild.
     *
     * @param guild The guild whose vault items to retrieve.
     * @return Map of slot index to ItemStack.
     */
    fun getVaultInventory(guild: Guild): Map<Int, ItemStack>

    /**
     * Updates the vault inventory for a guild.
     *
     * @param guild The guild whose vault is being updated.
     * @param items Map of slot index to ItemStack.
     * @return Result indicating success or failure.
     */
    fun updateVaultInventory(guild: Guild, items: Map<Int, ItemStack>): VaultResult<Unit>

    /**
     * Calculates the total gold value of items in the vault.
     * Counts GOLD_NUGGET (1), GOLD_INGOT (9), GOLD_BLOCK (81).
     *
     * @param items The items to calculate value for.
     * @return Total gold value in nuggets.
     */
    fun calculateGoldValue(items: List<ItemStack>): Int

    /**
     * Gets the vault capacity for a given guild level.
     *
     * @param level The guild level.
     * @return Number of available slots.
     */
    fun getCapacityForLevel(level: Int): Int

    /**
     * Checks if a location is valid for vault placement.
     *
     * @param location The location to check.
     * @param guild The guild placing the vault.
     * @return Result indicating if location is valid.
     */
    fun isValidVaultLocation(location: Location, guild: Guild): VaultResult<Boolean>

    /**
     * Gets the location of a guild's vault chest.
     *
     * @param guild The guild.
     * @return The vault location, or null if not placed.
     */
    fun getVaultLocation(guild: Guild): Location?

    /**
     * Checks if a chest at a location is a guild vault.
     *
     * @param location The location to check.
     * @return The guild that owns this vault, or null if not a vault.
     */
    fun getGuildForVaultChest(location: Location): Guild?

    /**
     * Updates the vault status for a guild.
     *
     * @param guild The guild.
     * @param status The new vault status.
     * @return The updated guild.
     */
    fun updateVaultStatus(guild: Guild, status: VaultStatus): Guild

    /**
     * Drops all items from a vault at its location.
     *
     * @param guild The guild whose vault items to drop.
     * @return Result indicating success or failure.
     */
    fun dropAllVaultItems(guild: Guild): VaultResult<Unit>

    /**
     * Checks if a player has permission to access the vault.
     *
     * @param player The player.
     * @param guild The guild.
     * @param requireWithdraw Whether withdraw permission is required.
     * @return true if player has permission.
     */
    fun hasVaultPermission(player: Player, guild: Guild, requireWithdraw: Boolean = false): Boolean

    /**
     * Restores all vault chests on server startup.
     * Recreates chest blocks at saved locations for all guilds with AVAILABLE vault status.
     *
     * @return Number of vaults successfully restored.
     */
    fun restoreAllVaultChests(): Int
}

/**
 * Simple result wrapper for service operations.
 */
sealed class VaultResult<out T> {
    data class Success<T>(val data: T) : VaultResult<T>()
    data class Failure(val message: String) : VaultResult<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun getMessageOrNull(): String? = when (this) {
        is Success -> null
        is Failure -> message
    }
}
