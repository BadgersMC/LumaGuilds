package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.application.services.VaultInventoryManager
import net.lumalyte.lg.application.services.VaultResult
import net.lumalyte.lg.application.services.WithdrawalInfo
import net.lumalyte.lg.domain.entities.BankMode
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Shop purchase deposit/withdraw paths for [GuildVaultServiceBukkit].
 * Kept separate to limit cyclomatic complexity on the main vault service.
 */
internal class GuildVaultShopTransactions(
    private val guildRepository: GuildRepository,
    private val configService: ConfigService,
    private val vaultInventoryManager: VaultInventoryManager,
    private val physicalCurrencyService: PhysicalCurrencyService,
    private val capacityForLevel: (Int) -> Int
) {
    private val logger = LoggerFactory.getLogger(GuildVaultShopTransactions::class.java)
    private val systemActorId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    fun withdrawForShopPurchase(guild: Guild, amount: Double, reason: String): VaultResult<WithdrawalInfo> {
        val currentGuild = guildRepository.getById(guild.id) ?: guild
        checkVaultAccess(currentGuild)?.let { return VaultResult.Failure(it) }

        return when (resolveBankMode()) {
            BankMode.VIRTUAL -> withdrawVirtual(guild, amount, reason)
            BankMode.PHYSICAL -> withdrawPhysical(guild, amount, reason)
            BankMode.BOTH -> {
                val virtualResult = withdrawVirtual(guild, amount, reason)
                if (virtualResult is VaultResult.Success) virtualResult else withdrawPhysical(guild, amount, reason)
            }
        }
    }

    fun depositToVault(guild: Guild, amount: Double, reason: String): VaultResult<Double> {
        val currentGuild = guildRepository.getById(guild.id) ?: guild
        checkVaultAccess(currentGuild)?.let { return VaultResult.Failure(it) }

        return when (resolveBankMode()) {
            BankMode.VIRTUAL, BankMode.BOTH -> depositVirtual(guild, amount, reason)
            BankMode.PHYSICAL -> depositPhysical(guild, amount, reason)
        }
    }

    private fun resolveBankMode(): BankMode {
        val bankModeStr = configService.loadConfig().vault.bankMode
        return try {
            BankMode.valueOf(bankModeStr.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid bank mode '$bankModeStr', defaulting to BOTH")
            BankMode.BOTH
        }
    }

    private fun checkVaultAccess(guild: Guild): String? = when {
        guild.bankFrozen -> "Guild bank is emergency-frozen — shop transactions are blocked"
        guild.vaultLocked -> "Guild vault is locked by a server administrator — shop transactions are blocked"
        else -> null
    }

    private fun depositVirtual(guild: Guild, amount: Double, reason: String): VaultResult<Double> {
        val depositAmount = amount.toLong()
        if (depositAmount <= 0) {
            return VaultResult.Failure("Deposit amount must be positive")
        }

        val newBalance = vaultInventoryManager.depositGold(guild.id, systemActorId, depositAmount)
        vaultInventoryManager.forceFlush(guild.id)
        logger.info("Guild ${guild.name} deposited $amount virtual currency for: $reason")
        return VaultResult.Success(newBalance.toDouble())
    }

    private fun depositPhysical(guild: Guild, amount: Double, reason: String): VaultResult<Double> {
        if (!physicalCurrencyService.isPhysicalCurrencyEnabled()) {
            return VaultResult.Failure("Physical currency is not enabled")
        }

        val currencyAmount = amount.toInt()
        if (currencyAmount <= 0) {
            return VaultResult.Failure("Deposit amount must be positive")
        }

        val itemValue = physicalCurrencyService.getItemValue()
        if (currencyAmount % itemValue != 0) {
            return VaultResult.Failure(
                "Deposit amount $currencyAmount is not divisible by item value $itemValue"
            )
        }

        val itemsToAdd = currencyAmount / itemValue
        val currencyMaterial = resolveCurrencyMaterial()
            ?: return VaultResult.Failure("Invalid physical currency material")

        val capacity = capacityForLevel(guild.level)
        val inventory = vaultInventoryManager.getAllSlots(guild.id)
            .filterKeys { it in 1 until capacity }
            .mapNotNull { (slot, item) -> item?.let { slot to it } }
            .toMap()
            .toMutableMap()

        val added = addItemsToInventory(inventory, currencyMaterial, itemsToAdd, guild.level)
        if (!added && itemsToAdd > 0) {
            return VaultResult.Failure("Vault is full - cannot add items")
        }

        inventory.forEach { (slot, item) ->
            vaultInventoryManager.updateSlot(guild.id, slot, item, systemActorId)
        }
        vaultInventoryManager.forceFlush(guild.id)

        val newBalance = physicalCurrencyService.calculateItemsCurrencyValue(
            inventory.values.filterNotNull().toList()
        ).toDouble()

        logger.info("Guild ${guild.name} deposited $amount ${currencyMaterial.name} for: $reason")
        return VaultResult.Success(newBalance)
    }

    private fun withdrawVirtual(guild: Guild, amount: Double, reason: String): VaultResult<WithdrawalInfo> {
        val withdrawAmount = amount.toLong()
        val currentBalance = vaultInventoryManager.getGoldBalance(guild.id)

        if (currentBalance < withdrawAmount) {
            return VaultResult.Failure(
                "Insufficient virtual currency: $currentBalance < $withdrawAmount"
            )
        }

        val newBalance = vaultInventoryManager.withdrawGold(guild.id, systemActorId, withdrawAmount)
        if (newBalance == -1L) {
            return VaultResult.Failure("Failed to withdraw virtual currency from guild vault")
        }

        vaultInventoryManager.forceFlush(guild.id)
        logger.info("Guild ${guild.name} withdrew $amount virtual currency for: $reason")
        return VaultResult.Success(
            WithdrawalInfo(
                withdrawnAmount = amount,
                remainingBalance = newBalance.toDouble(),
                mode = BankMode.VIRTUAL
            )
        )
    }

    private fun withdrawPhysical(guild: Guild, amount: Double, reason: String): VaultResult<WithdrawalInfo> {
        if (!physicalCurrencyService.isPhysicalCurrencyEnabled()) {
            return VaultResult.Failure("Physical currency is not enabled")
        }

        val currencyAmount = amount.toInt()
        if (currencyAmount <= 0) {
            return VaultResult.Failure("Withdrawal amount must be positive")
        }

        val itemValue = physicalCurrencyService.getItemValue()
        if (currencyAmount % itemValue != 0) {
            return VaultResult.Failure(
                "Withdrawal amount $currencyAmount is not divisible by item value $itemValue"
            )
        }

        val availableCurrency = physicalCurrencyService.calculateItemsCurrencyValue(
            vaultInventoryManager.getAllSlots(guild.id).values.filterNotNull().toList()
        )

        if (availableCurrency < currencyAmount) {
            return VaultResult.Failure(
                "Insufficient physical currency: $availableCurrency < $currencyAmount"
            )
        }

        val itemsToRemove = currencyAmount / itemValue
        val currencyMaterial = resolveCurrencyMaterial()
            ?: return VaultResult.Failure("Invalid physical currency material")

        val capacity = capacityForLevel(guild.level)
        val inventory = vaultInventoryManager.getAllSlots(guild.id)
            .filterKeys { it in 1 until capacity }
            .mapNotNull { (slot, item) -> item?.let { slot to it } }
            .toMap()
            .toMutableMap()

        val remaining = removeCurrencyItems(inventory, currencyMaterial, itemsToRemove)
        if (remaining > 0) {
            return VaultResult.Failure("Failed to remove physical currency items from vault")
        }

        val allSlots = vaultInventoryManager.getAllSlots(guild.id).toMutableMap()
        for (slot in 1 until capacity) {
            val item = inventory[slot]
            vaultInventoryManager.updateSlot(guild.id, slot, item, systemActorId)
            if (item == null) allSlots.remove(slot) else allSlots[slot] = item
        }
        vaultInventoryManager.forceFlush(guild.id)

        val newBalance = physicalCurrencyService.calculateItemsCurrencyValue(
            allSlots.values.filterNotNull().toList()
        ).toDouble()

        logger.info("Guild ${guild.name} withdrew $amount ${currencyMaterial.name} for: $reason")
        return VaultResult.Success(
            WithdrawalInfo(
                withdrawnAmount = amount,
                remainingBalance = newBalance,
                mode = BankMode.PHYSICAL
            )
        )
    }

    private fun resolveCurrencyMaterial(): Material? {
        val materialName = physicalCurrencyService.getCurrencyMaterialName()
        return try {
            Material.valueOf(materialName.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun addItemsToInventory(
        inventory: MutableMap<Int, ItemStack>,
        material: Material,
        amount: Int,
        guildLevel: Int
    ): Boolean {
        var remaining = amount
        val maxStackSize = material.maxStackSize

        for (slot in inventory.keys.sorted()) {
            if (remaining <= 0) break
            val item = inventory[slot] ?: continue
            if (item.type == material && item.amount < maxStackSize) {
                val toAdd = minOf(remaining, maxStackSize - item.amount)
                inventory[slot] = item.clone().apply { this.amount = item.amount + toAdd }
                remaining -= toAdd
            }
        }

        val vaultCapacity = capacityForLevel(guildLevel)
        for (slot in 1 until vaultCapacity) {
            if (remaining <= 0) break
            if (inventory.containsKey(slot)) continue
            val toAdd = minOf(remaining, maxStackSize)
            inventory[slot] = ItemStack.of(material, toAdd)
            remaining -= toAdd
        }

        return remaining < amount
    }

    private fun removeCurrencyItems(
        inventory: MutableMap<Int, ItemStack>,
        material: Material,
        itemsNeeded: Int
    ): Int {
        var remaining = itemsNeeded
        val slots = inventory.filter { it.value.type == material }.keys.toList()
        for (slot in slots) {
            if (remaining <= 0) break
            val item = inventory[slot] ?: continue
            if (item.amount <= remaining) {
                remaining -= item.amount
                inventory.remove(slot)
            } else {
                inventory[slot] = item.clone().apply { amount = item.amount - remaining }
                remaining = 0
            }
        }
        return remaining
    }
}