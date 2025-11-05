package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Service interface for managing physical item currency operations.
 * Handles counting, adding, and removing physical items used as guild currency.
 */
interface PhysicalCurrencyService {

    /**
     * Checks if physical currency mode is enabled.
     *
     * @return true if physical currency is enabled, false otherwise.
     */
    fun isPhysicalCurrencyEnabled(): Boolean

    /**
     * Gets the configured physical currency material name (e.g., "RAW_GOLD").
     *
     * @return The Bukkit Material name as a string.
     */
    fun getCurrencyMaterialName(): String

    /**
     * Gets the value of a single currency item in currency units.
     * For simple 1:1 systems, this returns 1.
     *
     * @return The value per item.
     */
    fun getItemValue(): Int

    /**
     * Calculates the total physical currency value in a guild's vault.
     * Counts all items matching the configured currency material.
     *
     * @param guild The guild whose vault to check.
     * @return Total currency value in the vault.
     */
    fun calculateVaultCurrencyValue(guild: Guild): Int

    /**
     * Calculates the total physical currency value in a collection of items.
     *
     * @param items The items to calculate value for.
     * @return Total currency value.
     */
    fun calculateItemsCurrencyValue(items: List<ItemStack>): Int

    /**
     * Checks if a guild's vault has sufficient physical currency for a transaction.
     *
     * @param guild The guild to check.
     * @param amount The currency amount needed.
     * @return true if sufficient funds, false otherwise.
     */
    fun hasSufficientCurrency(guild: Guild, amount: Int): Boolean

    /**
     * Removes physical currency items from a guild's vault.
     * Updates the vault inventory in the database.
     *
     * @param guild The guild to deduct currency from.
     * @param amount The currency amount to deduct.
     * @param reason Optional reason for the transaction.
     * @return true if successful, false if insufficient funds or error.
     */
    fun deductCurrency(guild: Guild, amount: Int, reason: String? = null): Boolean

    /**
     * Adds physical currency items to a guild's vault.
     * Updates the vault inventory in the database.
     *
     * @param guild The guild to add currency to.
     * @param amount The currency amount to add.
     * @param reason Optional reason for the transaction.
     * @return true if successful, false if vault full or error.
     */
    fun addCurrency(guild: Guild, amount: Int, reason: String? = null): Boolean

    /**
     * Gets the withdrawal fee amount for physical currency.
     *
     * @param amount The withdrawal amount.
     * @return The fee amount in currency units.
     */
    fun getWithdrawalFee(amount: Int): Int

    /**
     * Gets the deposit fee amount for physical currency.
     *
     * @param amount The deposit amount.
     * @return The fee amount in currency units.
     */
    fun getDepositFee(amount: Int): Int

    /**
     * Gets the minimum transaction amount for physical currency.
     *
     * @return The minimum currency amount.
     */
    fun getMinimumTransaction(): Int

    /**
     * Gets the daily war cost in physical currency.
     *
     * @return The daily war cost amount.
     */
    fun getDailyWarCost(): Int

    /**
     * Gets the war declaration cost in physical currency.
     *
     * @return The war declaration cost amount.
     */
    fun getWarDeclarationCost(): Int

    /**
     * Validates the physical currency configuration.
     * Checks if the configured material exists and settings are valid.
     *
     * @return Result with error message if invalid, Success if valid.
     */
    fun validateConfiguration(): CurrencyResult<Unit>

    /**
     * Creates ItemStack for the configured currency material.
     *
     * @param amount The number of items to create.
     * @return ItemStack of currency items, or null if invalid material.
     */
    fun createCurrencyItemStack(amount: Int): ItemStack?
}

/**
 * Result wrapper for currency operations.
 */
sealed class CurrencyResult<out T> {
    data class Success<T>(val data: T) : CurrencyResult<T>()
    data class Failure(val message: String) : CurrencyResult<Nothing>()

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
