package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.CurrencyResult
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID

class PhysicalCurrencyServiceBukkit(
    private val configService: ConfigService,
    private val bankService: BankService
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

        return bankService.getBalance(guild.id)
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

    override fun deductCurrency(guild: Guild, amount: Int, reason: String?): Boolean =
        deductCurrency(UUID.randomUUID(), guild, amount, reason)

    override fun deductCurrency(transactionId: UUID, guild: Guild, amount: Int, reason: String?): Boolean =
        isPhysicalCurrencyEnabled() && amount > 0 &&
            bankService.deductFromGuildBank(transactionId, guild.id, amount, reason)

    override fun addCurrency(guild: Guild, amount: Int, reason: String?): Boolean =
        addCurrency(UUID.randomUUID(), guild, amount, reason)

    override fun addCurrency(transactionId: UUID, guild: Guild, amount: Int, reason: String?): Boolean =
        isPhysicalCurrencyEnabled() && amount > 0 &&
            bankService.creditToGuildBank(transactionId, guild.id, amount, reason)

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

        // Validate material name and ensure it is a holdable item (not AIR, WATER, etc.)
        val materialName = config.physicalCurrencyMaterial
        val material = try {
            Material.valueOf(materialName)
        } catch (e: IllegalArgumentException) {
            return CurrencyResult.Failure(
                "Invalid physical currency material: '$materialName' is not a valid Bukkit Material"
            )
        }
        if (!material.isItem) {
            return CurrencyResult.Failure(
                "Invalid physical currency material: '$materialName' is not a holdable item (e.g. AIR or WATER cannot be currency)"
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

        return ItemStack.of(currencyMaterial, amount)
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
