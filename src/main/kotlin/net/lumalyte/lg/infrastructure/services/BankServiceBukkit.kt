package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankStats
import net.lumalyte.lg.application.services.ConfigService
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
import java.sql.SQLException
import java.util.UUID
import kotlin.math.min

class BankServiceBukkit(
    private val bankRepository: BankRepository,
    private val memberRepository: net.lumalyte.lg.application.persistence.MemberRepository,
    private val rankRepository: net.lumalyte.lg.application.persistence.RankRepository,
    private val progressionRepository: ProgressionRepository,
    private val progressionConfigService: ProgressionConfigService,
    private val configService: ConfigService,
    private val guildRepository: net.lumalyte.lg.application.persistence.GuildRepository,
    private val vaultInventoryManager: net.lumalyte.lg.application.services.VaultInventoryManager
) : BankService {

    private val logger = LoggerFactory.getLogger(BankServiceBukkit::class.java)

    // Vault Economy integration
    private var economy: Economy? = null

    // --- Balance leaderboard cache ---
    // The underlying repository already keeps balances in memory, so this cache only amortizes the
    // sort. It is invalidated immediately whenever a balance changes (deposit/withdraw/deduct), and
    // also carries a short TTL as a safety net against any balance path that bypasses this service.
    private val balanceLeaderboardLock = Any()
    @Volatile private var cachedTopBalances: List<Pair<UUID, Int>> = emptyList()
    @Volatile private var balanceLeaderboardExpiresAtMs: Long = 0L
    private val balanceLeaderboardTtlMs = 30_000L
    private val balanceLeaderboardCacheSize = 100

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

            // Check emergency freeze
            val guild = guildRepository.getById(guildId)
            if (guild?.bankFrozen == true) {
                logger.warn("Deposit blocked for guild $guildId: emergency freeze is active")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Deposit blocked: emergency bank freeze is active"
                ))
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

            // Check guild bank balance limit (progression-based)
            val currentBalance = getBalance(guildId)
            val progression = progressionRepository.getGuildProgression(guildId)
            val progressionConfig = progressionConfigService.getProgressionConfig()
            val levelRewards = progressionConfig.getActiveLevelRewards()
            var maxBalance = 100000 // Default starting balance limit
            if (progression != null) {
                for (level in 1..progression.currentLevel) {
                    val balance = levelRewards[level]?.bankLimit ?: 0
                    if (balance > maxBalance) maxBalance = balance
                }
            }
            if (currentBalance + amount > maxBalance) {
                logger.warn("Deposit would exceed guild bank limit: $currentBalance + $amount > $maxBalance")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Deposit would exceed bank balance limit ($maxBalance)"
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

            // Build the audit/history transaction record up front so it carries a stable id.
            val transaction = BankTransaction.deposit(guildId, playerId, amount, description)

            // Take money from the player's economy account FIRST.
            val withdrawResult = economy.withdrawPlayer(player, amount.toDouble())
            if (!withdrawResult.transactionSuccess()) {
                logger.warn("Failed to withdraw $amount from player $playerId - aborting deposit")
                recordAudit(BankAudit(
                    transactionId = transaction.id,
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.INSUFFICIENT_FUNDS,
                    details = "Failed to withdraw money from player account"
                ))
                return null
            }

            // Credit the unified guild balance (store B: vault gold). This is atomic and the
            // balance is immediately visible to all readers via VaultInventoryManager.
            val creditedBalance = try {
                vaultInventoryManager.depositGold(guildId, playerId, amount.toLong())
            } catch (e: Exception) {
                // Credit failed AFTER taking the player's money - refund to avoid loss.
                logger.error("Failed to credit guild balance for guild $guildId - refunding player $playerId", e)
                economy.depositPlayer(player, amount.toDouble())
                recordAudit(BankAudit(
                    transactionId = transaction.id,
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Failed to credit guild balance - player refunded"
                ))
                return null
            }

            // Record the transaction in the ledger as audit/history (best-effort; the balance
            // no longer depends on it, so a failure here does not corrupt funds).
            try {
                bankRepository.recordTransaction(transaction)
            } catch (e: Exception) {
                logger.warn("Failed to record deposit transaction history for ${transaction.id} (balance already updated)", e)
            }

            // SUCCESS: player debited and guild balance credited
            invalidateBalanceLeaderboard()
            val newBalance = creditedBalance.toInt()
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
                    val progression = progressionRepository.getGuildProgression(guildId)
                    if (progression != null) {
                        val updatedProgression = progression.copy(
                            totalExperience = progression.totalExperience + xpAmount
                        )
                        progressionRepository.saveGuildProgression(updatedProgression)
                        logger.info("Awarded $xpAmount XP to guild $guildId for bank deposit of $amount")
                    }
                }
            } catch (e: SQLException) {
                // Database error awarding XP - log but don't fail deposit
                logger.warn("Failed to award progression XP for bank deposit (database error)", e)
            } catch (e: IllegalStateException) {
                // Service not initialized or invalid state
                logger.warn("Failed to award progression XP for bank deposit (service error)", e)
            }

            return transaction
        } catch (e: SQLException) {
            logger.error("Database error processing deposit for player $playerId to guild $guildId", e)
            return null
        } catch (e: IllegalStateException) {
            logger.error("Service error processing deposit (Vault economy unavailable?)", e)
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

            // Check emergency freeze
            val guild = guildRepository.getById(guildId)
            if (guild?.bankFrozen == true) {
                logger.warn("Withdrawal blocked for guild $guildId: emergency freeze is active")
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Withdrawal blocked: emergency bank freeze is active"
                ))
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
            val totalDebit = amount + fee

            // Build the audit/history transaction record up front so it carries a stable id.
            val transaction = BankTransaction.withdraw(guildId, playerId, amount, fee, description)

            // Debit the unified guild balance (store B: vault gold) FIRST, including the fee.
            // withdrawGold is atomic and returns -1 if funds are insufficient.
            val debitedBalance = vaultInventoryManager.withdrawGold(guildId, playerId, totalDebit.toLong())
            if (debitedBalance == -1L) {
                logger.warn("Insufficient guild balance for withdrawal of $totalDebit from guild $guildId")
                recordAudit(BankAudit(
                    transactionId = transaction.id,
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.INSUFFICIENT_FUNDS,
                    details = "Insufficient guild balance for withdrawal of $amount (+$fee fee)"
                ))
                return null
            }

            // Now pay the player from the guild withdrawal.
            val depositResult = economy.depositPlayer(player, finalAmount.toDouble())
            if (!depositResult.transactionSuccess()) {
                logger.error("Failed to deposit $finalAmount to player $playerId - re-crediting guild balance")
                // Player payout failed AFTER debiting the guild - re-credit to avoid loss.
                vaultInventoryManager.depositGold(guildId, playerId, totalDebit.toLong())
                recordAudit(BankAudit(
                    transactionId = transaction.id,
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.PERMISSION_DENIED,
                    details = "Failed to deposit money to player account - withdrawal reverted"
                ))
                return null
            }

            // Record the ledger history (best-effort; balance no longer depends on it).
            try {
                bankRepository.recordTransaction(transaction)
                if (fee > 0) {
                    bankRepository.recordTransaction(BankTransaction(
                        guildId = guildId,
                        actorId = playerId,
                        type = TransactionType.FEE,
                        amount = fee,
                        description = "Withdrawal fee"
                    ))
                }
            } catch (e: Exception) {
                logger.warn("Failed to record withdrawal transaction history for ${transaction.id} (balance already updated)", e)
            }

            // SUCCESS: guild balance debited and player paid
            invalidateBalanceLeaderboard()
            val newBalance = debitedBalance.toInt()
            recordAudit(BankAudit(
                transactionId = transaction.id,
                guildId = guildId,
                actorId = playerId,
                action = AuditAction.WITHDRAWAL,
                details = "Withdrawal of $amount (fee: $fee)",
                newBalance = newBalance
            ))

            if (fee > 0) {
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = playerId,
                    action = AuditAction.FEE_CHARGED,
                    details = "Fee charged: $fee",
                    newBalance = newBalance
                ))
            }

            logger.info("Player $playerId withdrew $amount from guild $guildId (fee: $fee, balance: $newBalance)")
            return transaction
        } catch (e: SQLException) {
            logger.error("Database error processing withdrawal for player $playerId from guild $guildId", e)
            return null
        } catch (e: IllegalStateException) {
            logger.error("Service error processing withdrawal (Vault economy unavailable?)", e)
            return null
        }
    }

    override fun getBalance(guildId: UUID): Int {
        // Store B (guild vault gold balance) is the single source of truth for guild funds.
        // The bank_transactions ledger is retained only as an audit/history trail.
        return vaultInventoryManager.getGoldBalance(guildId).toInt()
    }

    override fun getTopBalances(limit: Int): List<Pair<UUID, Int>> {
        if (limit <= 0) return emptyList()
        // Cache the largest requested page so callers asking for a smaller N reuse it.
        val cacheSize = maxOf(limit, balanceLeaderboardCacheSize)
        val now = System.currentTimeMillis()
        synchronized(balanceLeaderboardLock) {
            if (now >= balanceLeaderboardExpiresAtMs || cachedTopBalances.size < limit) {
                // Ranked by store B (vault gold balance), the unified source of truth.
                cachedTopBalances = vaultInventoryManager.getTopGoldBalances(cacheSize)
                    .map { (id, balance) -> id to balance.toInt() }
                balanceLeaderboardExpiresAtMs = now + balanceLeaderboardTtlMs
            }
            return cachedTopBalances.take(limit)
        }
    }

    /** Invalidates the cached balance leaderboard so the next read reflects the change. */
    private fun invalidateBalanceLeaderboard() {
        balanceLeaderboardExpiresAtMs = 0L
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
        } catch (e: IllegalStateException) {
            logger.error("Vault economy unavailable when getting balance for $playerId", e)
            return 0
        }
    }

    override fun canWithdraw(playerId: UUID, guildId: UUID): Boolean {
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        return rank.permissions.contains(RankPermission.WITHDRAW_FROM_BANK)
    }

    override fun canDeposit(playerId: UUID, guildId: UUID): Boolean {
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        return rank.permissions.contains(RankPermission.DEPOSIT_TO_BANK)
    }

    override fun getTransactionHistory(guildId: UUID, limit: Int?): List<BankTransaction> {
        return bankRepository.getTransactionsForGuild(guildId, limit)
    }

    override fun getAuditLog(guildId: UUID, limit: Int?): List<BankAudit> {
        return bankRepository.getAuditForGuild(guildId, limit)
    }

    override fun getMemberContributions(guildId: UUID): List<MemberContribution> {
        val members = memberRepository.getByGuild(guildId)

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

        // Apply progression-based fee multiplier
        val progression = progressionRepository.getGuildProgression(guildId)
        val progressionConfig = progressionConfigService.getProgressionConfig()
        val levelRewards = progressionConfig.getActiveLevelRewards()
        var feeMultiplier = 1.0
        if (progression != null) {
            for (level in 1..progression.currentLevel) {
                val multiplier = levelRewards[level]?.withdrawalFeeMultiplier ?: 1.0
                if (multiplier < feeMultiplier) feeMultiplier = multiplier
            }
        }

        val calculatedFee = (amount * feePercent * feeMultiplier).toInt()
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
        } catch (e: SQLException) {
            logger.error("Database error recording audit entry", e)
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
        } catch (e: IllegalStateException) {
            logger.error("Vault economy unavailable when withdrawing $amount from player $playerId", e)
            return false
        }
    }

    override fun deductFromGuildBank(guildId: UUID, amount: Int, reason: String?): Boolean {
        try {
            // Reuse the sentinel UUID(0,0) defined by GuildVaultServiceBukkit so every system-driven
            // gold movement is correlatable in the audit log (grep "all system ops" stays possible).
            // A fresh randomUUID() per call would scatter system rows across orphan-looking ids.
            val systemActor = GuildVaultServiceBukkit.SYSTEM_ACTOR

            // Debit the unified guild balance (store B). Atomic; -1 if insufficient.
            val debitedBalance = vaultInventoryManager.withdrawGold(guildId, systemActor, amount.toLong())
            if (debitedBalance == -1L) {
                logger.warn("Guild $guildId has insufficient funds for deduction of $amount")
                return false
            }

            // Record the deduction in the ledger as audit/history (best-effort).
            try {
                bankRepository.recordTransaction(BankTransaction(
                    guildId = guildId,
                    actorId = systemActor,
                    type = TransactionType.DEDUCTION,
                    amount = amount,
                    description = reason ?: "Guild bank deduction"
                ))
            } catch (e: Exception) {
                logger.warn("Failed to record deduction history for guild $guildId (balance already updated)", e)
            }

            invalidateBalanceLeaderboard()
            return true
        } catch (e: SQLException) {
            logger.error("Database error processing guild bank deduction for guild $guildId", e)
            return false
        }
    }

    override fun creditToGuildBank(guildId: UUID, amount: Int, reason: String?): Boolean {
        if (amount <= 0) return false
        try {
            // Same shared sentinel as deductFromGuildBank above — see comment there.
            val systemActor = GuildVaultServiceBukkit.SYSTEM_ACTOR

            // Credit the unified guild balance (store B). Atomic and immediately visible.
            val newBalance = try {
                vaultInventoryManager.depositGold(guildId, systemActor, amount.toLong())
            } catch (e: Exception) {
                logger.error("Failed to credit guild balance for guild $guildId", e)
                return false
            }

            // Record in the ledger as audit/history (best-effort).
            try {
                bankRepository.recordTransaction(BankTransaction.deposit(guildId, systemActor, amount, reason))
                recordAudit(BankAudit(
                    guildId = guildId,
                    actorId = systemActor,
                    action = AuditAction.DEPOSIT,
                    details = reason ?: "Guild bank credit",
                    newBalance = newBalance.toInt()
                ))
            } catch (e: Exception) {
                logger.warn("Failed to record credit history for guild $guildId (balance already updated)", e)
            }

            invalidateBalanceLeaderboard()
            return true
        } catch (e: SQLException) {
            logger.error("Database error processing guild bank credit for guild $guildId", e)
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
        } catch (e: IllegalStateException) {
            logger.error("Vault economy unavailable when depositing $amount to player $playerId", e)
            return false
        }
    }

}
