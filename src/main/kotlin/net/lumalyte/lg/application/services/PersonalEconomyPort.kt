package net.lumalyte.lg.application.services

import java.util.UUID

interface PersonalEconomyPort {
    fun isAvailable(): Boolean
    fun balance(playerId: UUID): Long?
    fun debit(playerId: UUID, amount: Long): ExternalTransferResult
    fun credit(playerId: UUID, amount: Long): ExternalTransferResult

    data object Unavailable : PersonalEconomyPort {
        override fun isAvailable() = false
        override fun balance(playerId: UUID): Long? = null
        override fun debit(playerId: UUID, amount: Long) = ExternalTransferResult.Unavailable
        override fun credit(playerId: UUID, amount: Long) = ExternalTransferResult.Unavailable
    }
}

sealed interface ExternalTransferResult {
    data object Applied : ExternalTransferResult
    data object Unavailable : ExternalTransferResult
    data class Rejected(val reason: String) : ExternalTransferResult
    data class Failed(val reason: String) : ExternalTransferResult
}

interface GuildGoldAuthorizationPort {
    fun canDeposit(playerId: UUID, guildId: UUID): Boolean
    fun canWithdraw(playerId: UUID, guildId: UUID): Boolean

    data object AllowAll : GuildGoldAuthorizationPort {
        override fun canDeposit(playerId: UUID, guildId: UUID) = true
        override fun canWithdraw(playerId: UUID, guildId: UUID) = true
    }
}

data class PersonalGoldRequest(
    val transactionId: UUID,
    val guildId: UUID,
    val playerId: UUID,
    val amount: Long,
    val description: String
)
