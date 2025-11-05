package net.lumalyte.lg.application.persistence

import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Repository interface for guild vault item persistence.
 * Manages the physical item storage for guild vaults.
 */
interface GuildVaultRepository {

    /**
     * Saves the entire vault inventory for a guild.
     *
     * @param guildId The ID of the guild.
     * @param items Map of slot index to ItemStack.
     * @return true if successful, false otherwise.
     */
    fun saveVaultInventory(guildId: UUID, items: Map<Int, ItemStack>): Boolean

    /**
     * Gets the vault inventory for a guild.
     *
     * @param guildId The ID of the guild.
     * @return Map of slot index to ItemStack.
     */
    fun getVaultInventory(guildId: UUID): Map<Int, ItemStack>

    /**
     * Saves a single item at a specific slot.
     *
     * @param guildId The ID of the guild.
     * @param slotIndex The slot index (0-based).
     * @param item The ItemStack to save (null to clear slot).
     * @return true if successful, false otherwise.
     */
    fun saveVaultItem(guildId: UUID, slotIndex: Int, item: ItemStack?): Boolean

    /**
     * Gets a single item from a specific slot.
     *
     * @param guildId The ID of the guild.
     * @param slotIndex The slot index (0-based).
     * @return The ItemStack at that slot, or null if empty.
     */
    fun getVaultItem(guildId: UUID, slotIndex: Int): ItemStack?

    /**
     * Clears all items from a guild's vault.
     *
     * @param guildId The ID of the guild.
     * @return true if successful, false otherwise.
     */
    fun clearVault(guildId: UUID): Boolean

    /**
     * Gets the number of items (non-null slots) in a guild's vault.
     *
     * @param guildId The ID of the guild.
     * @return The number of items in the vault.
     */
    fun getVaultItemCount(guildId: UUID): Int

    /**
     * Checks if a guild has any items in their vault.
     *
     * @param guildId The ID of the guild.
     * @return true if the vault has items, false otherwise.
     */
    fun hasVaultItems(guildId: UUID): Boolean
}
