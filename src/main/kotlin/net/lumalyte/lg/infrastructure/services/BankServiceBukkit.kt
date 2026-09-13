package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.BankRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.BankWithdrawalResult
import net.lumalyte.lg.application.services.BankStats
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardService
import net.lumalyte.lg.api.events.GuildBankDepositEvent
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
    private val guildService: net.lumalyte.lg.application.services.GuildService,
    private val vaultInventoryManager: net.lumalyte.lg.infrastructure.vault.VaultInventoryManager,
    private val chapterTwoGuildAwardService: ChapterTwoGuildAwardService? = null,
    private val goldService: net.lumalyte.lg.application.services.GuildGoldService,
) : BankService {

    companion object {
        /** Actor UUID for system-initiated actions (auto-lock, interest accrual). */
        val SYSTEM_ACTOR: UUID = UUID(0L, 0L)

        /**
         * Effective deposit ceiling (REQ-009): `bank.max_bank_balance` is the hard cap;
         * the progression-derived limit refines it downward when present.
         */
        fun effectiveMaxBalance(configCap: Int, progressionLimit: Int?): Int =
            if (progressionLimit == null) configCap else minOf(configCap, progressionLimit)

        /**
         * Highest bankLimit across the levels a guild has reached, or null when no
         * level actually grants one (then the config cap applies alone).
         *
         * NOTE: `bankLimit` defaults to 0 in the config model when a level has no
         * explicit `bank_limit`, and 0 means "no bank limit granted at this level".
         * Treating 0 as a real limit would collapse the ceiling to 0 for guilds
         * whose progression levels carry no bank_limit rewards, and then
         * effectiveMaxBalance(cap, 0) = 0 would block EVERY deposit.
         */
        fun computeProgressionBankLimit(
            levelRewards: Map<Int, net.lumalyte.lg.config.LevelRewardConfig>,
            currentLevel: Int
        ): Int? {
            var limit: Int? = null
            for (level in 1..currentLevel) {
                val balance = levelRewards[level]?.bankLimit ?: continue
                if (balance <= 0) continue // 0 = not granted; skip
                if (limit == null || balance > limit) limit = balance
            }
            return limit
        }

        /**
         * Suspicious-transaction auto-lock decision (REQ-009): triggers at/above
         * `bank.suspicious_transaction_threshold` only when `bank.auto_lock_suspicious_accounts` is enabled.
         */
        fun shouldAutoLock(amount: Int, threshold: Int, autoLockEnabled: Boolean): Boolean =
            autoLockEnabled && amount >= threshold
    }

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

    override fun getMaxPhysicalDeposit(guildId: UUID, playerId: UUID): Long =
        goldService.maximumPhysicalDeposit(guildId, playerId).coerceAtMost(Int.MAX_VALUE.toLong())

    override fun depositPhysical(request: net.lumalyte.lg.application.services.PhysicalGoldRequest) =
        transferPhysical(request, withdrawal = false)

    override fun withdrawPhysical(request: net.lumalyte.lg.application.services.PhysicalGoldRequest) =
        transferPhysical(request, withdrawal = true)

    private fun transferPhysical(request: net.lumalyte.lg.application.services.PhysicalGoldRequest,
        withdrawal: Boolean): net.lumalyte.lg.domain.gold.GuildGoldResult {
        if (request.amount <= 0 || request.amount > Int.MAX_VALUE) {
            return net.lumalyte.lg.domain.gold.GuildGoldResult.Rejected(net.lumalyte.lg.domain.gold.GuildGoldRejection.INVALID_AMOUNT)
        }
        return try {
            if (guildRepository.getById(request.guildId)?.bankFrozen == true) {
                return net.lumalyte.lg.domain.gold.GuildGoldResult.Rejected(net.lumalyte.lg.domain.gold.GuildGoldRejection.FROZEN)
            }
            legacyPendingPayout(request.guildId)?.let {
                return net.lumalyte.lg.domain.gold.GuildGoldResult.Failed(it, false)
            }
            val result = if (withdrawal) goldService.withdrawPhysical(request) else goldService.depositPhysical(request)
            if (result is net.lumalyte.lg.domain.gold.GuildGoldResult.Applied) {
                recordCanonicalHistory(canonicalTransaction(result, request.guildId, request.playerId,
                    request.amount.toInt(), request.description,
                    if (withdrawal) TransactionType.WITHDRAWAL else TransactionType.DEPOSIT))
            }
            result
        } catch (error: Exception) {
            logger.error("Canonical physical transfer ${request.transactionId} requires inspection", error)
            net.lumalyte.lg.domain.gold.GuildGoldResult.Failed(request.transactionId, false)
        }
    }

    override fun deposit(guildId: UUID, playerId: UUID, amount: Int, description: String?): BankTransaction? {
        if (amount <= 0 || guildRepository.getById(guildId)?.bankFrozen == true) return null
        val transactionId = UUID.randomUUID()
        return try {
            if (legacyPendingPayout(guildId) != null) return null
            val result = goldService.depositPersonal(net.lumalyte.lg.application.services.PersonalGoldRequest(
                transactionId, guildId, playerId, amount.toLong(), description ?: "Guild bank deposit"))
            if (result !is net.lumalyte.lg.domain.gold.GuildGoldResult.Applied) return null
            val transaction = canonicalTransaction(result, guildId, playerId, amount, description, TransactionType.DEPOSIT)
            recordCanonicalHistory(transaction)
            runCatching {
                chapterTwoGuildAwardService?.awardBankGrowth(guildId, playerId, result.oldBalance, result.newBalance)
                Bukkit.getPluginManager().callEvent(GuildBankDepositEvent(guildId, playerId, amount))
            }.onFailure { logger.warn("Post-deposit notification failed for $transactionId", it) }
            transaction
        } catch (error: Exception) {
            logger.error("Canonical deposit $transactionId requires inspection", error)
            null
        }
    }

    override fun withdraw(guildId: UUID, playerId: UUID, amount: Int, description: String?): BankTransaction? =
        (withdrawOutcome(guildId, playerId, amount, description) as? BankWithdrawalResult.Completed)?.transaction

    override fun withdrawOutcome(guildId: UUID, playerId: UUID, amount: Int, description: String?): BankWithdrawalResult {
        if (amount <= 0 || guildRepository.getById(guildId)?.bankFrozen == true) return BankWithdrawalResult.Rejected
        val transactionId = UUID.randomUUID()
        return try {
            legacyPendingPayout(guildId)?.let { return BankWithdrawalResult.Ambiguous(it) }
            when (val result = goldService.withdrawPersonal(net.lumalyte.lg.application.services.PersonalGoldRequest(
                transactionId, guildId, playerId, amount.toLong(), description ?: "Guild bank withdrawal"))) {
                is net.lumalyte.lg.domain.gold.GuildGoldResult.Applied -> {
                    val transaction = canonicalTransaction(result, guildId, playerId, amount, description, TransactionType.WITHDRAWAL)
                    recordCanonicalHistory(transaction)
                    BankWithdrawalResult.Completed(transaction)
                }
                is net.lumalyte.lg.domain.gold.GuildGoldResult.Failed ->
                    if (result.compensationSucceeded) BankWithdrawalResult.Rejected
                    else BankWithdrawalResult.Ambiguous(result.transactionId)
                is net.lumalyte.lg.domain.gold.GuildGoldResult.Rejected -> BankWithdrawalResult.Rejected
            }
        } catch (error: Exception) {
            logger.error("Canonical withdrawal $transactionId requires inspection", error)
            BankWithdrawalResult.Ambiguous(transactionId)
        }
    }

    /** Keep PR #142 recovery gates effective while its existing journal is retained. */
    private fun legacyPendingPayout(guildId: UUID): UUID? {
        val history = bankRepository.getAuditForGuild(guildId)
        val resolved = history.filter {
            it.action == AuditAction.PAYOUT_COMPLETED || it.action == AuditAction.PAYOUT_REFUNDED
        }.mapNotNull { it.transactionId }.toSet()
        return history.firstOrNull {
            it.action == AuditAction.PAYOUT_PENDING && it.transactionId !in resolved
        }?.transactionId
    }

    private fun canonicalTransaction(result: net.lumalyte.lg.domain.gold.GuildGoldResult.Applied,
        guildId: UUID, playerId: UUID, amount: Int, description: String?, type: TransactionType) =
        BankTransaction(id = result.transactionId, guildId = guildId, actorId = playerId, type = type,
            amount = amount, fee = Math.toIntExact(result.fee), description = description)

    private fun recordCanonicalHistory(transaction: BankTransaction) {
        runCatching { bankRepository.recordTransaction(transaction) }
            .onFailure { logger.warn("Failed to record compatibility history for ${transaction.id}", it) }
    }

    override fun getBalance(guildId: UUID): Int {
        // Store B (guild vault gold balance) is the single source of truth for guild funds.
        // The bank_transactions ledger is retained only as an audit/history trail.
        return Math.toIntExact(goldService.balance(guildId))
    }

    override fun getTopBalances(limit: Int): List<Pair<UUID, Int>> {
        if (limit <= 0) return emptyList()
        return goldService.topBalances(limit)
            .map { (id, balance) -> id to Math.toIntExact(balance) }
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

    override fun calculateWithdrawalFee(guildId: UUID, amount: Int): Int =
        Math.toIntExact(goldService.withdrawalFee(guildId, amount.toLong()))

    override fun getMaxWithdrawalAmount(guildId: UUID, playerId: UUID): Int =
        goldService.withdrawalLimit(guildId).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

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

    override fun deductFromGuildBank(guildId: UUID, amount: Int, reason: String?): Boolean =
        deductFromGuildBank(UUID.randomUUID(), guildId, amount, reason)

    override fun deductFromGuildBank(transactionId: UUID, guildId: UUID, amount: Int, reason: String?): Boolean =
        applySystemGold(transactionId, guildId, amount, reason, credit = false)

    override fun creditToGuildBank(guildId: UUID, amount: Int, reason: String?): Boolean =
        creditToGuildBank(UUID.randomUUID(), guildId, amount, reason)

    override fun creditToGuildBank(transactionId: UUID, guildId: UUID, amount: Int, reason: String?): Boolean =
        applySystemGold(transactionId, guildId, amount, reason, credit = true)

    private fun applySystemGold(transactionId: UUID, guildId: UUID, amount: Int, reason: String?, credit: Boolean): Boolean {
        if (amount <= 0 || guildRepository.getById(guildId)?.bankFrozen == true) return false
        return try {
            val description = reason ?: if (credit) "Guild bank credit" else "Guild bank deduction"
            val result = if (credit) goldService.creditSystem(transactionId, guildId, SYSTEM_ACTOR,
                amount.toLong(), net.lumalyte.lg.domain.gold.GuildGoldRoute.SYSTEM, description)
            else goldService.debitSystem(transactionId, guildId, SYSTEM_ACTOR, amount.toLong(), description)
            if (result !is net.lumalyte.lg.domain.gold.GuildGoldResult.Applied) return false
            // Compatibility history is secondary; the canonical transaction owns the balance.
            runCatching {
                bankRepository.recordTransaction(BankTransaction(id = transactionId, guildId = guildId,
                    actorId = SYSTEM_ACTOR, amount = amount, description = description,
                    type = if (credit) TransactionType.DEPOSIT else TransactionType.DEDUCTION))
            }.onFailure { logger.warn("Failed to record compatibility history for $transactionId", it) }
            true
        } catch (error: Exception) {
            logger.error("Canonical guild gold operation $transactionId failed", error)
            false
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
