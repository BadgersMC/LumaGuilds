package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.BankSecuritySettingsRepository
import net.lumalyte.lg.application.persistence.GuildBudgetRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankStats
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.ExperienceSource
import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankSecuritySettings
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.BudgetAnalytics
import net.lumalyte.lg.domain.entities.BudgetCategory
import net.lumalyte.lg.domain.entities.BudgetStatus
import net.lumalyte.lg.domain.entities.GuildBudget
import net.lumalyte.lg.domain.entities.MemberContribution
import net.lumalyte.lg.domain.entities.TransactionType
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.AuditAction
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
    private val progressionService: ProgressionService,
    private val securitySettingsRepository: BankSecuritySettingsRepository,
    private val budgetRepository: GuildBudgetRepository
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
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    description = "Deposit permission denied"
                ))
                return null
            }

            // Validate amount
            if (!isValidAmount(amount)) {
                logger.warn("Invalid deposit amount: $amount by player $playerId")
                recordAudit(BankAudit(
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    description = "Invalid deposit amount: $amount"
                ))
                return null
            }

            // Check if player has sufficient funds
            if (!economy.has(player, amount.toDouble())) {
                logger.warn("Player $playerId has insufficient funds for deposit of $amount")
                recordAudit(BankAudit(
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.INSUFFICIENT_FUNDS,
                    amount = amount,
                    description = "Player has insufficient funds: $amount required"
                ))
                return null
            }

            // Withdraw money from player's account
            val withdrawResult = economy.withdrawPlayer(player, amount.toDouble())
            if (!withdrawResult.transactionSuccess()) {
                logger.error("Failed to withdraw $amount from player $playerId")
                recordAudit(BankAudit(
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    amount = amount,
                    description = "Failed to withdraw money from player account"
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
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.DEPOSIT,
                    amount = amount,
                    description = "Deposit of $amount"
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
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    amount = amount,
                    description = "Withdrawal permission denied"
                ))
                return null
            }

            // Validate amount
            if (!isValidAmount(amount)) {
                logger.warn("Invalid withdrawal amount: $amount by player $playerId")
                recordAudit(BankAudit(
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    amount = amount,
                    description = "Invalid withdrawal amount: $amount"
                ))
                return null
            }

            // Check sufficient funds including fee
            val fee = calculateWithdrawalFee(guildId, amount)
            if (!hasSufficientFunds(guildId, amount, true)) {
                logger.warn("Insufficient funds for withdrawal of $amount (+$fee fee) from guild $guildId")
                recordAudit(BankAudit(
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.INSUFFICIENT_FUNDS,
                    amount = amount,
                    description = "Insufficient funds for withdrawal of $amount (+$fee fee)"
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
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    amount = finalAmount,
                    description = "Failed to deposit money to player account"
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
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    playerId = playerId,
                    action = AuditAction.WITHDRAWAL,
                    amount = amount,
                    description = "Withdrawal of $amount (fee: $fee)"
                ))

                // Record fee if applicable
                if (fee > 0) {
                    val feeTransaction = BankTransaction(
                        id = UUID.randomUUID(),
                        guildId = guildId,
                        actorId = playerId,
                        type = TransactionType.FEE,
                        amount = fee,
                        description = "Withdrawal fee",
                        timestamp = java.time.Instant.now()
                    )
                    bankRepository.recordTransaction(feeTransaction)

                    recordAudit(BankAudit(
                        id = UUID.randomUUID(),
                        guildId = guildId,
                        playerId = playerId,
                        action = AuditAction.FEE_CHARGED,
                        amount = fee,
                        description = "Fee charged: $fee"
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

    override fun recordAudit(audit: BankAudit): Boolean {
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
                id = UUID.randomUUID(),
                guildId = guildId,
                actorId = UUID.randomUUID(), // Use random UUID for system deductions
                type = TransactionType.DEDUCTION,
                amount = amount, // Positive amount (will be negated in balance calculation)
                description = reason ?: "Guild bank deduction",
                timestamp = java.time.Instant.now()
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

    // === SECURITY ENHANCEMENTS IMPLEMENTATION ===

    override fun getSecuritySettings(guildId: UUID): BankSecuritySettings? {
        return securitySettingsRepository.findByGuildId(guildId)
    }

    override fun updateSecuritySettings(guildId: UUID, settings: BankSecuritySettings): Boolean {
        try {
            // Validate settings
            if (settings.dualAuthThreshold < 0) {
                logger.warn("Invalid dual authorization threshold: ${settings.dualAuthThreshold}")
                return false
            }

            if (settings.multiSignatureCount < 1) {
                logger.warn("Invalid multi-signature count: ${settings.multiSignatureCount}")
                return false
            }

            val updated = securitySettingsRepository.save(settings.copy(updatedAt = java.time.Instant.now()))

            if (updated) {
                logSecurityEvent(guildId, settings.id, AuditAction.SECURITY_SETTING_CHANGE,
                    description = "Security settings updated")
                logger.info("Updated security settings for guild $guildId")
            }

            return updated
        } catch (e: Exception) {
            logger.error("Error updating security settings for guild $guildId", e)
            return false
        }
    }

    override fun requiresDualAuth(guildId: UUID, amount: Int): Boolean {
        val settings = getSecuritySettings(guildId) ?: return false
        return settings.requiresDualAuth(amount)
    }

    override fun requiresMultiSignature(guildId: UUID, amount: Int): Boolean {
        val settings = getSecuritySettings(guildId) ?: return false
        return settings.requiresMultiSignature(amount)
    }

    override fun logSecurityEvent(guildId: UUID, playerId: UUID, action: AuditAction, amount: Int?, description: String?): BankAudit? {
        try {
            val audit = BankAudit(
                id = UUID.randomUUID(),
                guildId = guildId,
                playerId = playerId,
                action = action,
                amount = amount,
                description = description,
                timestamp = java.time.Instant.now()
            )

            val saved = bankRepository.recordAudit(audit)
            if (saved) {
                logger.debug("Recorded security audit event: ${action.name} for guild $guildId by player $playerId")
                return audit
            }

            return null
        } catch (e: Exception) {
            logger.error("Error logging security event for guild $guildId", e)
            return null
        }
    }

    override fun detectSuspiciousActivity(guildId: UUID): List<String> {
        val alerts = mutableListOf<String>()

        try {
            val settings = getSecuritySettings(guildId)
            if (settings == null || !settings.fraudDetectionEnabled) {
                return alerts
            }

            val auditLog = getAuditLog(guildId, 50)
            val transactions = getTransactionHistory(guildId, null)

            // Check for unusual patterns
            val recentAudits = auditLog.filter {
                it.timestamp.isAfter(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS))
            }

            val failedAuths = recentAudits.count { it.action == AuditAction.PERMISSION_DENIED }
            if (failedAuths >= settings.suspiciousActivityThreshold) {
                alerts.add("High number of authentication failures")
            }

            // Check for large transactions
            val balance = getBalance(guildId)
            val largeTransactions = transactions.filter {
                it.type == TransactionType.WITHDRAWAL && it.amount > balance * 0.8
            }

            if (largeTransactions.isNotEmpty()) {
                alerts.add("Large withdrawal detected (${largeTransactions.last().amount} coins)")
            }

            // Check for rapid transactions
            val recentWithdrawals = transactions.filter {
                it.type == TransactionType.WITHDRAWAL &&
                it.timestamp.isAfter(java.time.Instant.now().minus(10, java.time.temporal.ChronoUnit.MINUTES))
            }

            if (recentWithdrawals.size >= 5) {
                alerts.add("Rapid withdrawal pattern detected")
            }

        } catch (e: Exception) {
            logger.error("Error detecting suspicious activity for guild $guildId", e)
        }

        return alerts
    }

    override fun activateEmergencyFreeze(guildId: UUID, activatedBy: UUID, reason: String): Boolean {
        try {
            val settings = getSecuritySettings(guildId)
                ?: BankSecuritySettings(
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    emergencyFreeze = true
                )

            val updatedSettings = settings.copy(
                emergencyFreeze = true,
                updatedAt = java.time.Instant.now()
            )

            val updated = securitySettingsRepository.save(updatedSettings)

            if (updated) {
                logSecurityEvent(guildId, activatedBy, AuditAction.EMERGENCY_FREEZE_ACTIVATED,
                    description = reason)
                logger.warn("Emergency freeze activated for guild $guildId by $activatedBy: $reason")
            }

            return updated
        } catch (e: Exception) {
            logger.error("Error activating emergency freeze for guild $guildId", e)
            return false
        }
    }

    override fun deactivateEmergencyFreeze(guildId: UUID, deactivatedBy: UUID): Boolean {
        try {
            val settings = getSecuritySettings(guildId) ?: return false

            val updatedSettings = settings.copy(
                emergencyFreeze = false,
                updatedAt = java.time.Instant.now()
            )

            val updated = securitySettingsRepository.save(updatedSettings)

            if (updated) {
                logSecurityEvent(guildId, deactivatedBy, AuditAction.EMERGENCY_FREEZE_DEACTIVATED)
                logger.info("Emergency freeze deactivated for guild $guildId by $deactivatedBy")
            }

            return updated
        } catch (e: Exception) {
            logger.error("Error deactivating emergency freeze for guild $guildId", e)
            return false
        }
    }

    // === BUDGET MANAGEMENT IMPLEMENTATION ===

    override fun getGuildBudgets(guildId: UUID): List<GuildBudget> {
        try {
            return budgetRepository.findByGuildId(guildId)
        } catch (e: Exception) {
            logger.error("Error getting budgets for guild $guildId", e)
            return emptyList()
        }
    }

    override fun getBudgetByCategory(guildId: UUID, category: BudgetCategory): GuildBudget? {
        try {
            return budgetRepository.findByGuildIdAndCategory(guildId, category)
        } catch (e: Exception) {
            logger.error("Error getting budget for guild $guildId, category $category", e)
            return null
        }
    }

    override fun setBudget(guildId: UUID, category: BudgetCategory, allocatedAmount: Int, periodStart: java.time.Instant, periodEnd: java.time.Instant): GuildBudget? {
        try {
            if (allocatedAmount < 0) {
                logger.warn("Invalid budget allocation amount: $allocatedAmount")
                return null
            }

            val existingBudget = budgetRepository.findByGuildIdAndCategory(guildId, category)

            val budget = if (existingBudget != null) {
                existingBudget.copy(
                    allocatedAmount = allocatedAmount,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    updatedAt = java.time.Instant.now()
                )
            } else {
                GuildBudget(
                    id = UUID.randomUUID(),
                    guildId = guildId,
                    category = category,
                    allocatedAmount = allocatedAmount,
                    periodStart = periodStart,
                    periodEnd = periodEnd
                )
            }

            val saved = budgetRepository.save(budget)
            if (saved) {
                logger.info("Set budget for guild $guildId, category $category: $allocatedAmount coins")
                return budget
            }

            return null
        } catch (e: Exception) {
            logger.error("Error setting budget for guild $guildId, category $category", e)
            return null
        }
    }

    override fun updateBudgetSpent(guildId: UUID, category: BudgetCategory, amount: Int): Boolean {
        try {
            val budget = budgetRepository.findByGuildIdAndCategory(guildId, category)
                ?: return false

            val newSpentAmount = budget.spentAmount + amount
            if (newSpentAmount < 0) {
                logger.warn("Budget spent amount would be negative: $newSpentAmount")
                return false
            }

            return budgetRepository.updateSpentAmount(guildId, category, newSpentAmount)
        } catch (e: Exception) {
            logger.error("Error updating budget spent for guild $guildId, category $category", e)
            return false
        }
    }

    override fun getBudgetAnalytics(guildId: UUID, periodDays: Int): Map<BudgetCategory, BudgetAnalytics> {
        try {
            val endDate = java.time.Instant.now()
            val startDate = endDate.minus(periodDays.toLong(), java.time.temporal.ChronoUnit.DAYS)

            val analyticsData = budgetRepository.getBudgetAnalytics(guildId, startDate, endDate)
            val analytics = mutableMapOf<BudgetCategory, BudgetAnalytics>()

            for ((category, data) in analyticsData) {
                val budget = budgetRepository.findByGuildIdAndCategory(guildId, category)
                    ?: continue

                analytics[category] = BudgetAnalytics(
                    category = category,
                    allocatedAmount = budget.allocatedAmount,
                    spentAmount = data.spentAmount,
                    remainingAmount = budget.allocatedAmount - data.spentAmount,
                    usagePercentage = if (budget.allocatedAmount > 0) (data.spentAmount.toDouble() / budget.allocatedAmount) * 100 else 0.0,
                    status = budget.getStatus(),
                    periodStart = budget.periodStart,
                    periodEnd = budget.periodEnd,
                    transactionCount = data.transactionCount,
                    averageTransactionAmount = data.averageTransactionAmount,
                    alertsTriggered = emptyList() // TODO: Fix based on actual data type
                )
            }

            return analytics
        } catch (e: Exception) {
            logger.error("Error getting budget analytics for guild $guildId", e)
            return emptyMap()
        }
    }

    override fun isWithinBudget(guildId: UUID, category: BudgetCategory, amount: Int): Boolean {
        try {
            val budget = budgetRepository.findByGuildIdAndCategory(guildId, category)
                ?: return true // No budget set, allow transaction

            return (budget.spentAmount + amount) <= budget.allocatedAmount
        } catch (e: Exception) {
            logger.error("Error checking budget for guild $guildId, category $category", e)
            return true // Default to allowing transaction on error
        }
    }

    override fun getTransactionsByPlayerId(playerId: UUID): List<BankTransaction> {
        try {
            return bankRepository.getTransactionsByPlayerId(playerId)
        } catch (e: Exception) {
            logger.error("Error getting transactions for player $playerId", e)
            return emptyList()
        }
    }

    override fun getBalanceAtTime(guildId: UUID, timestamp: java.time.Instant): Int? {
        try {
            // Get all transactions for the guild up to the specified time
            val transactions = bankRepository.getTransactionsForGuild(guildId)
                .filter { it.timestamp <= timestamp }
                .sortedBy { it.timestamp }

            // Calculate balance at the specified time
            var balance = 0
            for (transaction in transactions) {
                when (transaction.type) {
                    TransactionType.DEPOSIT -> balance += transaction.amount
                    TransactionType.WITHDRAWAL -> balance -= (transaction.amount + transaction.fee)
                    TransactionType.FEE -> balance -= transaction.amount
                    TransactionType.DEDUCTION -> balance -= transaction.amount
                    else -> { /* Handle other transaction types if needed */ }
                }
            }

            return balance
        } catch (e: Exception) {
            logger.error("Error getting balance at time for guild $guildId", e)
            return null
        }
    }

}
