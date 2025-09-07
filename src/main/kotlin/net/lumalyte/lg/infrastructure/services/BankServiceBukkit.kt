package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankStats
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.ExperienceSource
import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.MemberContribution
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.domain.entities.AuditAction
import net.lumalyte.lg.domain.entities.RankPermission
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.plugin.RegisteredServiceProvider
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.min

class BankServiceBukkit(
    private val bankRepository: BankRepository,
    private val memberService: MemberService,
    private val configService: ConfigService,
    private val progressionService: ProgressionService
) : BankService {

    private val logger = LoggerFactory.getLogger(BankServiceBukkit::class.java)

    // Vault Economy integration
    private var economy: Economy? = null

    init {
        setupEconomy()
    }

    // Get configuration instance
    private fun getConfig() = configService.loadConfig()

    /**
     * Setup Vault economy integration
     */
    private fun setupEconomy() {
        if (Bukkit.getServer().pluginManager.getPlugin("Vault") == null) {
            logger.error("Vault plugin not found! Guild Bank will not function without Vault.")
            return
        }

        val rsp: RegisteredServiceProvider<Economy>? = Bukkit.getServer().servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            logger.error("No economy provider found! Guild Bank will not function without an economy plugin (Essentials, iConomy, etc.)")
            return
        }

        economy = rsp.provider
        logger.info("Successfully hooked into economy provider: ${economy?.javaClass?.simpleName}")
    }

    /**
     * Get the Vault economy instance
     */
    private fun getEconomy(): Economy? {
        if (economy == null) {
            setupEconomy()
        }
        return economy
    }

    /**
     * Check if Vault economy is available and working
     */
    fun isEconomyAvailable(): Boolean {
        val economy = getEconomy()
        return economy != null
    }

    /**
     * Get the name of the current economy provider
     */
    fun getEconomyProviderName(): String? {
        return getEconomy()?.javaClass?.simpleName ?: "None"
    }

    override fun deposit(guildId: UUID, playerId: UUID, amount: Int, description: String?): BankTransaction? {
        try {
            // Check Vault economy availability
            val economy = getEconomy()
            if (economy == null) {
                logger.error("Cannot process deposit: Vault economy not available")
                return null
            }

            // Get player
            val player = Bukkit.getPlayer(playerId)
            if (player == null) {
                logger.warn("Player $playerId not found online for deposit")
                return null
            }

            // Validate permissions
            if (!canDeposit(playerId, guildId)) {
                logger.warn("Player $playerId cannot deposit to guild $guildId")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Deposit permission denied"
                ))
                return null
            }

            // Validate amount
            if (!isValidAmount(amount)) {
                logger.warn("Invalid deposit amount: $amount by player $playerId")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Invalid deposit amount: $amount"
                ))
                return null
            }

            // Check if player has sufficient funds
            if (!economy.has(player, amount.toDouble())) {
                logger.warn("Player $playerId has insufficient funds for deposit of $amount")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.INSUFFICIENT_FUNDS,
                    details = "Player has insufficient funds: $amount required"
                ))
                return null
            }

            // Withdraw money from player's account
            val withdrawResult = economy.withdrawPlayer(player, amount.toDouble())
            if (!withdrawResult.transactionSuccess()) {
                logger.error("Failed to withdraw $amount from player $playerId")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Failed to withdraw money from player account"
                ))
                return null
            }

            // Create transaction
            val transaction = BankTransaction.deposit(guildId, playerId, amount, description)

            // Record transaction in guild bank database
            val success = bankRepository.recordTransaction(transaction)

            if (success) {
                val newBalance = bankRepository.getGuildBalance(guildId)
                recordAudit(BankAudit(
                    transactionId = transaction.id,
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.DEPOSIT,
                    details = "Deposit of $amount",
                    newBalance = newBalance
                ))

                logger.info("Player $playerId deposited $amount to guild $guildId (balance: $newBalance)")
                
                // Award progression XP for bank deposits
                try {
                    val config = getConfig()
                    val xpPerHundred = config.progression.bankDepositXpPer100
                    val xpAmount = (amount / 100.0 * xpPerHundred).toInt()
                    if (xpAmount > 0) {
                        progressionService.awardExperience(guildId, xpAmount, ExperienceSource.BANK_DEPOSIT)
                        logger.info("Awarded $xpAmount XP to guild $guildId for bank deposit of $amount")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to award progression XP for bank deposit", e)
                }
                
                return transaction
            } else {
                // Refund player if database recording failed
                logger.error("Failed to record deposit transaction, refunding player")
                economy.depositPlayer(player, amount.toDouble())
                return null
            }
        } catch (e: Exception) {
            logger.error("Error processing deposit", e)
            return null
        }
    }

    override fun withdraw(guildId: UUID, playerId: UUID, amount: Int, description: String?): BankTransaction? {
        try {
            // Check Vault economy availability
            val economy = getEconomy()
            if (economy == null) {
                logger.error("Cannot process withdrawal: Vault economy not available")
                return null
            }

            // Get player
            val player = Bukkit.getPlayer(playerId)
            if (player == null) {
                logger.warn("Player $playerId not found online for withdrawal")
                return null
            }

            // Validate permissions
            if (!canWithdraw(playerId, guildId)) {
                logger.warn("Player $playerId cannot withdraw from guild $guildId")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Withdrawal permission denied"
                ))
                return null
            }

            // Validate amount
            if (!isValidAmount(amount)) {
                logger.warn("Invalid withdrawal amount: $amount by player $playerId")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Invalid withdrawal amount: $amount"
                ))
                return null
            }

            // Check sufficient funds including fee
            val fee = calculateWithdrawalFee(guildId, amount)
            if (!hasSufficientFunds(guildId, amount, true)) {
                logger.warn("Insufficient funds for withdrawal of $amount (+$fee fee) from guild $guildId")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.INSUFFICIENT_FUNDS,
                    details = "Insufficient funds for withdrawal of $amount (+$fee fee)"
                ))
                return null
            }

            // Calculate final amount player receives (after fee)
            val finalAmount = amount

            // Deposit money to player's account (from guild bank withdrawal)
            val depositResult = economy.depositPlayer(player, finalAmount.toDouble())
            if (!depositResult.transactionSuccess()) {
                logger.error("Failed to deposit $finalAmount to player $playerId")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Failed to deposit money to player account"
                ))
                return null
            }

            // Create transaction
            val transaction = BankTransaction.withdraw(guildId, playerId, amount, fee, description)

            // Record transaction in guild bank database
            val success = bankRepository.recordTransaction(transaction)

            if (success) {
                val newBalance = bankRepository.getGuildBalance(guildId)
                recordAudit(BankAudit(
                    transactionId = transaction.id,
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.WITHDRAWAL,
                    details = "Withdrawal of $amount (fee: $fee)",
                    newBalance = newBalance
                ))

                // Record fee if applicable
                if (fee > 0) {
                    val feeTransaction = BankTransaction(
                        guildId = guildId,
                        actorId = playerId,
                        type = TransactionType.FEE,
                        amount = fee,
                        description = "Withdrawal fee"
                    )
                    bankRepository.recordTransaction(feeTransaction)

                    recordAudit(BankAudit(
                        transactionId = feeTransaction.id,
                        guildId = guildId,
                        actorId = playerId,
                        action = AuditAction.FEE_CHARGED,
                        details = "Fee charged: $fee",
                        newBalance = newBalance
                    ))
                }

                logger.info("Player $playerId withdrew $amount from guild $guildId (fee: $fee, balance: $newBalance)")
                return transaction
            } else {
                // Reverse the deposit if database recording failed
                logger.error("Failed to record withdrawal transaction, reversing player deposit")
                economy.withdrawPlayer(player, finalAmount.toDouble())
                return null
            }
        } catch (e: Exception) {
            logger.error("Error processing withdrawal", e)
            return null
        }
    }

    override fun getBalance(guildId: UUID): Int {
        return bankRepository.getGuildBalance(guildId)
    }

    override fun getPlayerBalance(playerId: UUID): Int {
        try {
            val economy = getEconomy()
            if (economy == null) {
                logger.error("Cannot get player balance: Vault economy not available")
                return 0
            }

            val player = Bukkit.getPlayer(playerId)
            if (player == null) {
                logger.warn("Player $playerId not found online for balance check")
                return 0
            }

            return economy.getBalance(player).toInt()
        } catch (e: Exception) {
            logger.error("Error getting player balance for $playerId", e)
            return 0
        }
    }

    override fun canWithdraw(playerId: UUID, guildId: UUID): Boolean {
        return memberService.hasPermission(playerId, guildId, RankPermission.WITHDRAW_FROM_BANK)
    }

    override fun canDeposit(playerId: UUID, guildId: UUID): Boolean {
        return memberService.hasPermission(playerId, guildId, RankPermission.DEPOSIT_TO_BANK)
    }

    override fun getTransactionHistory(guildId: UUID, limit: Int?): List<BankTransaction> {
        return bankRepository.getTransactionsForGuild(guildId, limit)
    }

    override fun getAuditLog(guildId: UUID, limit: Int?): List<BankAudit> {
        return bankRepository.getAuditForGuild(guildId, limit)
    }

    override fun getMemberContributions(guildId: UUID): List<MemberContribution> {
        val members = memberService.getGuildMembers(guildId)

        return members.map { member ->
            val playerId = member.playerId
            val totalDeposits = bankRepository.getPlayerTotalDeposits(playerId, guildId)
            val totalWithdrawals = bankRepository.getPlayerTotalWithdrawals(playerId, guildId)

            // Get transaction count and last transaction for this member
            val memberTransactions = bankRepository.getTransactionsForGuild(guildId)
                .filter { it.actorId == playerId }
            val transactionCount = memberTransactions.size
            val lastTransaction = memberTransactions.maxByOrNull { it.timestamp }?.timestamp

            // Try to get player name from online players
            val playerName = Bukkit.getPlayer(playerId)?.name

            MemberContribution(
                playerId = playerId,
                playerName = playerName,
                totalDeposits = totalDeposits,
                totalWithdrawals = totalWithdrawals,
                transactionCount = transactionCount,
                lastTransaction = lastTransaction
            )
        }.sortedByDescending { it.netContribution }
    }

    override fun getPlayerDeposits(playerId: UUID, guildId: UUID): Int {
        return bankRepository.getPlayerTotalDeposits(playerId, guildId)
    }

    override fun getPlayerWithdrawals(playerId: UUID, guildId: UUID): Int {
        return bankRepository.getPlayerTotalWithdrawals(playerId, guildId)
    }

    override fun calculateWithdrawalFee(guildId: UUID, amount: Int): Int {
        val config = getConfig()
        val feePercent = config.bank.withdrawalFeePercent
        val maxFee = config.bank.maxWithdrawalFee

        val calculatedFee = (amount * feePercent).toInt()
        return min(calculatedFee, maxFee)
    }

    override fun getMaxWithdrawalAmount(guildId: UUID, playerId: UUID): Int {
        val config = getConfig()
        val balance = getBalance(guildId)
        val maxPercent = config.bank.maxWithdrawalPercent
        val maxAmount = (balance * maxPercent).toInt()

        // Also respect daily withdrawal limits
        val dailyLimit = config.bank.dailyWithdrawalLimit
        return min(maxAmount, dailyLimit)
    }

    override fun getMinDepositAmount(): Int {
        return getConfig().bank.minDepositAmount
    }

    override fun getMaxDepositAmount(): Int {
        return getConfig().bank.maxDepositAmount
    }

    override fun hasSufficientFunds(guildId: UUID, amount: Int, includeFee: Boolean): Boolean {
        val balance = getBalance(guildId)
        val totalNeeded = if (includeFee) {
            amount + calculateWithdrawalFee(guildId, amount)
        } else {
            amount
        }
        return balance >= totalNeeded
    }

    override fun getBankStats(guildId: UUID): BankStats {
        val currentBalance = getBalance(guildId)
        val totalTransactions = bankRepository.getTransactionCountForGuild(guildId)
        val transactionVolume = bankRepository.getTotalVolumeForGuild(guildId)

        // Calculate total deposits and withdrawals from transaction history
        val transactions = getTransactionHistory(guildId)
        val totalDeposits = transactions.filter { it.type == TransactionType.DEPOSIT }.sumOf { it.amount }
        val totalWithdrawals = transactions.filter { it.type == TransactionType.WITHDRAWAL }.sumOf { it.amount + it.fee }

        return BankStats(
            currentBalance = currentBalance,
            totalDeposits = totalDeposits,
            totalWithdrawals = totalWithdrawals,
            totalTransactions = totalTransactions,
            transactionVolume = transactionVolume
        )
    }

    override fun processExpiredItems(): Int {
        // For now, this is a no-op as bank transactions don't expire
        // Could be used for cleanup of old audit logs in the future
        return 0
    }

    override fun isValidAmount(amount: Int): Boolean {
        return amount >= getMinDepositAmount() && amount <= getMaxDepositAmount()
    }

    private fun recordAudit(audit: BankAudit): Boolean {
        return try {
            bankRepository.recordAudit(audit)
        } catch (e: Exception) {
            logger.error("Failed to record audit entry", e)
            false
        }
    }

    override fun withdrawPlayer(playerId: UUID, amount: Int, reason: String?): Boolean {
        try {
            val economy = getEconomy()
            if (economy == null) {
                logger.error("Cannot withdraw from player balance: Vault economy not available")
                return false
            }

            val player = Bukkit.getPlayer(playerId)
            if (player == null) {
                logger.error("Cannot withdraw from player balance: Player $playerId not online")
                return false
            }

            val withdrawResult = economy.withdrawPlayer(player, amount.toDouble())
            if (withdrawResult.transactionSuccess()) {
                logger.info("Withdrew $amount coins from player ${player.name} (${if (reason != null) "Reason: $reason" else "No reason"})")
                return true
            } else {
                logger.warn("Failed to withdraw $amount coins from player ${player.name}: ${withdrawResult.errorMessage}")
                return false
            }
        } catch (e: Exception) {
            logger.error("Error withdrawing $amount coins from player $playerId", e)
            return false
        }
    }

    override fun deductFromGuildBank(guildId: UUID, amount: Int, reason: String?): Boolean {
        try {
            // Check if guild has sufficient balance
            val currentBalance = getBalance(guildId)
            if (currentBalance < amount) {
                logger.warn("Guild $guildId has insufficient funds for deduction of $amount")
                return false
            }

            // Record the transaction as a deduction (no player involved)
            val transaction = BankTransaction(
                guildId = guildId,
                actorId = UUID.randomUUID(), // Use random UUID for system deductions
                type = TransactionType.DEDUCTION,
                amount = amount, // Positive amount (will be negated in balance calculation)
                description = reason ?: "Guild bank deduction"
            )

            return bankRepository.recordTransaction(transaction)
        } catch (e: Exception) {
            logger.error("Error processing guild bank deduction", e)
            return false
        }
    }

    override fun depositPlayer(playerId: UUID, amount: Int, reason: String?): Boolean {
        try {
            val economy = getEconomy()
            if (economy == null) {
                logger.error("Cannot deposit to player balance: Vault economy not available")
                return false
            }

            val player = Bukkit.getPlayer(playerId)
            if (player == null) {
                logger.error("Cannot deposit to player balance: Player $playerId not online")
                return false
            }

            val depositResult = economy.depositPlayer(player, amount.toDouble())
            if (depositResult.transactionSuccess()) {
                logger.info("Deposited $amount coins to player ${player.name} (${if (reason != null) "Reason: $reason" else "No reason"})")
                return true
            } else {
                logger.warn("Failed to deposit $amount coins to player ${player.name}: ${depositResult.errorMessage}")
                return false
            }
        } catch (e: Exception) {
            logger.error("Error depositing $amount coins to player $playerId", e)
            return false
        }
    }

}
