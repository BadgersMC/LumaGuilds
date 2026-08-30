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
        val economy = economyProvider() ?: return null
        return exactLong(economy.getBalance(playerLookup(playerId)))
    }

    override fun debit(playerId: UUID, amount: Long): ExternalTransferResult {
        val economy = economyProvider() ?: return ExternalTransferResult.Unavailable
        val vaultAmount = exactDouble(amount) ?: return inexactAmount()
        return economy.withdrawPlayer(playerLookup(playerId), vaultAmount).toTransferResult()
    }

    override fun credit(playerId: UUID, amount: Long): ExternalTransferResult {
        val economy = economyProvider() ?: return ExternalTransferResult.Unavailable
        val vaultAmount = exactDouble(amount) ?: return inexactAmount()
        return economy.depositPlayer(playerLookup(playerId), vaultAmount).toTransferResult()
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
