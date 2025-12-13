package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.CurrencyResult
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID

class PhysicalCurrencyServiceBukkit(
    private val configService: ConfigService,
    private val guildVaultService: GuildVaultService
) : PhysicalCurrencyService {

    private val logger = LoggerFactory.getLogger(PhysicalCurrencyServiceBukkit::class.java)

    // Get configuration instance
    private fun getConfig() = configService.loadConfig()

    override fun isPhysicalCurrencyEnabled(): Boolean {
        return getConfig().vault.usePhysicalCurrency
    }

    override fun getCurrencyMaterialName(): String {
        return getConfig().vault.physicalCurrencyMaterial
    }

    override fun getItemValue(): Int {
        return getConfig().vault.physicalCurrencyItemValue
    }

    override fun calculateVaultCurrencyValue(guild: Guild): Int {
        if (!isPhysicalCurrencyEnabled()) {
            return 0
        }

        val items = guildVaultService.getVaultInventory(guild)
        return calculateItemsCurrencyValue(items.values.toList())
    }

    override fun calculateItemsCurrencyValue(items: List<ItemStack>): Int {
        if (!isPhysicalCurrencyEnabled()) {
            return 0
        }

        val materialName = getCurrencyMaterialName()
        val currencyMaterial = try {
            Material.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid currency material: $materialName")
            return 0
        }

        val itemValue = getItemValue()
        var totalValue = 0

        for (item in items) {
            if (item.type == currencyMaterial) {
                totalValue += item.amount * itemValue
            }
        }

        return totalValue
    }

    override fun hasSufficientCurrency(guild: Guild, amount: Int): Boolean {
        if (!isPhysicalCurrencyEnabled()) {
            return false
        }

        val currentValue = calculateVaultCurrencyValue(guild)
        return currentValue >= amount
    }

    override fun deductCurrency(guild: Guild, amount: Int, reason: String?): Boolean {
        if (!isPhysicalCurrencyEnabled()) {
            logger.warn("Cannot deduct currency: Physical currency is disabled")
            return false
        }

        if (amount <= 0) {
            logger.warn("Cannot deduct currency: Amount must be positive")
            return false
        }

        // Check if guild has sufficient funds
        if (!hasSufficientCurrency(guild, amount)) {
            logger.debug("Cannot deduct currency: Insufficient funds (need: $amount, have: ${calculateVaultCurrencyValue(guild)})")
            return false
        }

        val materialName = getCurrencyMaterialName()
        val currencyMaterial = try {
            Material.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid currency material: $materialName")
            return false
        }

        // Get current vault inventory
        val vaultInventory = guildVaultService.getVaultInventory(guild).toMutableMap()

        // Calculate how many items we need to remove
        val itemValue = getItemValue()
        var remainingToRemove = amount / itemValue // Number of items to remove

        if (amount % itemValue != 0) {
            logger.warn("Cannot deduct currency: Amount $amount is not divisible by item value $itemValue")
            return false
        }

        // Remove items from inventory
        val updatedInventory = mutableMapOf<Int, ItemStack>()
        for ((slot, item) in vaultInventory) {
            if (remainingToRemove <= 0) {
                // No more to remove, keep this item
                updatedInventory[slot] = item
                continue
            }

            if (item.type == currencyMaterial) {
                val toRemoveFromStack = minOf(remainingToRemove, item.amount)
                remainingToRemove -= toRemoveFromStack

                val newAmount = item.amount - toRemoveFromStack
                if (newAmount > 0) {
                    // Update item with reduced amount
                    val newItem = item.clone()
                    newItem.amount = newAmount
                    updatedInventory[slot] = newItem
                }
                // If newAmount is 0, we don't add it (removed completely)
            } else {
                // Not currency, keep it
                updatedInventory[slot] = item
            }
        }

        if (remainingToRemove > 0) {
            logger.error("Failed to remove all required currency items (remaining: $remainingToRemove)")
            return false
        }

        // Update vault inventory
        val result = guildVaultService.updateVaultInventory(guild, updatedInventory)
        if (result.isFailure()) {
            logger.error("Failed to update vault inventory: ${result.getMessageOrNull()}")
            return false
        }

        logger.debug("Deducted $amount currency from guild ${guild.id} (reason: ${reason ?: "none"})")
        return true
    }

    override fun addCurrency(guild: Guild, amount: Int, reason: String?): Boolean {
        if (!isPhysicalCurrencyEnabled()) {
            logger.warn("Cannot add currency: Physical currency is disabled")
            return false
        }

        if (amount <= 0) {
            logger.warn("Cannot add currency: Amount must be positive")
            return false
        }

        val materialName = getCurrencyMaterialName()
        val currencyMaterial = try {
            Material.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid currency material: $materialName")
            return false
        }

        val itemValue = getItemValue()
        var remainingToAdd = amount / itemValue

        if (amount % itemValue != 0) {
            logger.warn("Cannot add currency: Amount $amount is not divisible by item value $itemValue")
            return false
        }

        // Get current vault inventory
        val vaultInventory = guildVaultService.getVaultInventory(guild).toMutableMap()

        // Get vault capacity
        val capacity = guildVaultService.getCapacityForLevel(guild.level)

        // Try to add items to existing stacks first
        for ((slot, item) in vaultInventory.toList()) {
            if (remainingToAdd <= 0) break

            if (item.type == currencyMaterial) {
                val maxStackSize = currencyMaterial.maxStackSize
                val spaceInStack = maxStackSize - item.amount

                if (spaceInStack > 0) {
                    val toAdd = minOf(remainingToAdd, spaceInStack)
                    val newItem = item.clone()
                    newItem.amount = item.amount + toAdd
                    vaultInventory[slot] = newItem
                    remainingToAdd -= toAdd
                }
            }
        }

        // Add remaining items to new slots
        while (remainingToAdd > 0) {
            // Find next empty slot
            val emptySlot = (0 until capacity).firstOrNull { it !in vaultInventory.keys }
            if (emptySlot == null) {
                logger.warn("Cannot add currency: Vault is full")
                return false
            }

            val maxStackSize = currencyMaterial.maxStackSize
            val toAdd = minOf(remainingToAdd, maxStackSize)

            val newItem = ItemStack(currencyMaterial, toAdd)
            vaultInventory[emptySlot] = newItem
            remainingToAdd -= toAdd
        }

        // Update vault inventory
        val result = guildVaultService.updateVaultInventory(guild, vaultInventory)
        if (result.isFailure()) {
            logger.error("Failed to update vault inventory: ${result.getMessageOrNull()}")
            return false
        }

        logger.debug("Added $amount currency to guild ${guild.id} (reason: ${reason ?: "none"})")
        return true
    }

    override fun getWithdrawalFee(amount: Int): Int {
        return getConfig().vault.physicalWithdrawalFee
    }

    override fun getDepositFee(amount: Int): Int {
        return getConfig().vault.physicalDepositFee
    }

    override fun getMinimumTransaction(): Int {
        return getConfig().vault.physicalTransactionMinimum
    }

    override fun getDailyWarCost(): Int {
        return getConfig().vault.physicalDailyWarCost
    }

    override fun getWarDeclarationCost(): Int {
        return getConfig().vault.physicalWarDeclarationCost
    }

    override fun validateConfiguration(): CurrencyResult<Unit> {
        val config = getConfig().vault

        // Check if bank mode is set to PHYSICAL when physical currency is enabled
        if (config.usePhysicalCurrency && config.bankMode != "PHYSICAL") {
            return CurrencyResult.Failure(
                "Physical currency requires bank_mode to be set to 'PHYSICAL' (currently: ${config.bankMode})"
            )
        }

        // Validate material name
        val materialName = config.physicalCurrencyMaterial
        try {
            Material.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            return CurrencyResult.Failure(
                "Invalid physical currency material: '$materialName' is not a valid Bukkit Material"
            )
        }

        // Validate item value is positive
        if (config.physicalCurrencyItemValue <= 0) {
            return CurrencyResult.Failure(
                "Physical currency item value must be positive (currently: ${config.physicalCurrencyItemValue})"
            )
        }

        // Validate costs are non-negative
        if (config.physicalDailyWarCost < 0) {
            return CurrencyResult.Failure(
                "Physical daily war cost cannot be negative (currently: ${config.physicalDailyWarCost})"
            )
        }

        if (config.physicalWarDeclarationCost < 0) {
            return CurrencyResult.Failure(
                "Physical war declaration cost cannot be negative (currently: ${config.physicalWarDeclarationCost})"
            )
        }

        return CurrencyResult.Success(Unit)
    }

    override fun createCurrencyItemStack(amount: Int): ItemStack? {
        if (amount <= 0) {
            return null
        }

        val materialName = getCurrencyMaterialName()
        val currencyMaterial = try {
            Material.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid currency material: $materialName")
            return null
        }

        return ItemStack(currencyMaterial, amount)
    }

    override fun calculatePlayerInventoryValue(playerId: UUID): Int {
        if (!isPhysicalCurrencyEnabled()) {
            return 0
        }

        // Get player
        val player = Bukkit.getPlayer(playerId) ?: run {
            logger.debug("Player with UUID $playerId is not online")
            return 0
        }

        val materialName = getCurrencyMaterialName()
        val currencyMaterial = try {
            Material.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid currency material: $materialName")
            return 0
        }

        val itemValue = getItemValue()
        var totalValue = 0

        // Count base currency items in inventory
        for (item in player.inventory.contents) {
            if (item == null) continue

            if (item.type == currencyMaterial) {
                totalValue += item.amount * itemValue
            }
        }

        // Count compressed blocks
        val config = getConfig()
        val compressableBlocks = config.vault.compressableBlocks

        for (blockConfig in compressableBlocks) {
            // Parse format: "COMPRESSED_MATERIAL:BASE_MATERIAL:RATIO"
            val parts = blockConfig.split(":")
            if (parts.size != 3) {
                logger.warn("Invalid compressable block format: $blockConfig (expected COMPRESSED:BASE:RATIO)")
                continue
            }

            val compressedMaterialName = parts[0]
            val baseMaterialName = parts[1]
            val ratioStr = parts[2]

            // Check if this compressed block applies to our currency
            if (baseMaterialName != materialName) {
                continue
            }

            val ratio = try {
                ratioStr.toInt()
            } catch (e: NumberFormatException) {
                logger.warn("Invalid ratio in compressable block config: $blockConfig")
                continue
            }

            val compressedMaterial = try {
                Material.valueOf(compressedMaterialName)
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid compressed material: $compressedMaterialName")
                continue
            }

            // Count compressed blocks in inventory
            for (item in player.inventory.contents) {
                if (item == null) continue

                if (item.type == compressedMaterial) {
                    totalValue += item.amount * ratio * itemValue
                }
            }
        }

        return totalValue
    }
}
