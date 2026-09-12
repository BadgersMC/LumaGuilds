package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ExternalTransferResult
import net.lumalyte.lg.application.services.PersonalEconomyPort
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.OfflinePlayer
import java.util.UUID

class VaultPersonalEconomyAdapter(
    private val economyProvider: () -> Economy?,
    private val playerLookup: (UUID) -> OfflinePlayer
) : PersonalEconomyPort {
    override fun isAvailable(): Boolean = economyProvider() != null

    override fun balance(playerId: UUID): Long? {
        return runCatching {
            val economy = economyProvider() ?: return null
            exactLong(economy.getBalance(playerLookup(playerId)))
        }.getOrNull()
    }

    override fun debit(playerId: UUID, amount: Long): ExternalTransferResult {
        return transfer(playerId, amount) { economy, player, value -> economy.withdrawPlayer(player, value) }
    }

    override fun credit(playerId: UUID, amount: Long): ExternalTransferResult {
        return transfer(playerId, amount) { economy, player, value -> economy.depositPlayer(player, value) }
    }

    private fun transfer(
        playerId: UUID,
        amount: Long,
        action: (Economy, OfflinePlayer, Double) -> EconomyResponse,
    ): ExternalTransferResult {
        val economy = runCatching { economyProvider() }.getOrNull() ?: return ExternalTransferResult.Unavailable
        val vaultAmount = exactDouble(amount) ?: return inexactAmount()
        val player = runCatching { playerLookup(playerId) }.getOrNull()
            ?: return ExternalTransferResult.Rejected("Player lookup failed before transfer")
        val before = runCatching { exactLong(economy.getBalance(player)) }.getOrNull()
            ?: return ExternalTransferResult.Rejected("Balance unavailable before transfer")
        return try {
            action(economy, player, vaultAmount).toTransferResult()
        } catch (error: Exception) {
            val after = runCatching { exactLong(economy.getBalance(player)) }.getOrNull()
            if (after == before) ExternalTransferResult.Rejected("Provider threw without balance change: ${error.message}")
            else ExternalTransferResult.Failed("Uncertain provider outcome: ${error.message}")
        }
    }

    private fun EconomyResponse.toTransferResult(): ExternalTransferResult =
        if (transactionSuccess()) {
            ExternalTransferResult.Applied
        } else {
            ExternalTransferResult.Rejected(errorMessage ?: "Vault Economy rejected the transaction")
        }

    private fun exactDouble(amount: Long): Double? {
        if (amount < 0 || amount > MAX_SAFE_DOUBLE_INTEGER) return null
        val converted = amount.toDouble()
        return if (converted.toLong() == amount) converted else null
    }

    private fun exactLong(amount: Double): Long? {
        if (!amount.isFinite() || amount < 0 || amount > MAX_SAFE_DOUBLE_INTEGER.toDouble()) return null
        val converted = amount.toLong()
        return if (converted.toDouble() == amount) converted else null
    }

    private fun inexactAmount() = ExternalTransferResult.Rejected(
        "Amount cannot be represented exactly by Vault Economy"
    )

    private companion object {
        const val MAX_SAFE_DOUBLE_INTEGER = 9_007_199_254_740_992L
    }
}
