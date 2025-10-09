package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.GuildChest
import net.lumalyte.lg.domain.entities.GuildChestAccessLog
import net.lumalyte.lg.domain.entities.MemberContribution
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Service for generating CSV exports of guild data.
 * Ensures data sanitization and safe CSV formatting.
 */
class CsvExportService {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Generate CSV for transaction history
     */
    fun generateTransactionHistoryCsv(transactions: List<BankTransaction>): String {
        val csv = StringBuilder()

        // CSV Header
        csv.append("Transaction ID,Guild ID,Player Name,Player UUID,Type,Amount,Fee,Total,Description,Timestamp\n")

        // CSV Data rows
        transactions.forEach { transaction ->
            val playerName = getPlayerName(transaction.actorId)
            val formattedTimestamp = formatTimestamp(transaction.timestamp)

            // Sanitize data to prevent CSV injection
            val sanitizedDescription = sanitizeCsvField(transaction.description ?: "")

            csv.append("${transaction.id},")
            csv.append("${transaction.guildId},")
            csv.append("$playerName,")
            csv.append("${transaction.actorId},")
            csv.append("${transaction.type},")
            csv.append("${transaction.amount},")
            csv.append("${transaction.fee},")
            csv.append("${transaction.totalAmount},")
            csv.append("$sanitizedDescription,")
            csv.append("$formattedTimestamp\n")
        }

        return csv.toString()
    }

    /**
     * Generate CSV for member contributions
     */
    fun generateMemberContributionsCsv(contributions: List<MemberContribution>): String {
        val csv = StringBuilder()

        // CSV Header
        csv.append("Player Name,Player UUID,Total Deposits,Total Withdrawals,Net Contribution,Transaction Count,Contribution Status,Last Transaction\n")

        // CSV Data rows
        contributions.forEach { contribution ->
            val playerName = contribution.playerName ?: "Unknown"
            val lastTransaction = contribution.lastTransaction?.let { formatTimestamp(it) } ?: "Never"
            val status = contribution.contributionStatus.name

            // Sanitize player name
            val sanitizedName = sanitizeCsvField(playerName)

            csv.append("$sanitizedName,")
            csv.append("${contribution.playerId},")
            csv.append("${contribution.totalDeposits},")
            csv.append("${contribution.totalWithdrawals},")
            csv.append("${contribution.netContribution},")
            csv.append("${contribution.transactionCount},")
            csv.append("$status,")
            csv.append("$lastTransaction\n")
        }

        return csv.toString()
    }

    /**
     * Generate summary CSV with guild statistics
     */
    fun generateGuildSummaryCsv(
        guildName: String,
        totalBalance: Int,
        totalTransactions: Int,
        totalVolume: Int,
        memberCount: Int,
        contributors: List<MemberContribution>
    ): String {
        val csv = StringBuilder()

        // Guild Summary Header
        csv.append("Guild Summary\n")
        csv.append("Guild Name,$guildName\n")
        csv.append("Current Balance,$totalBalance\n")
        csv.append("Total Transactions,$totalTransactions\n")
        csv.append("Total Volume,$totalVolume\n")
        csv.append("Member Count,$memberCount\n")
        csv.append("Export Date,${formatTimestamp(Instant.now())}\n\n")

        // Member Contributions Section
        csv.append("Member Contributions\n")
        csv.append(generateMemberContributionsCsv(contributors))

        return csv.toString()
    }

    /**
     * Get player name (placeholder - would integrate with player service)
     */
    private fun getPlayerName(playerId: UUID): String {
        // TODO: Integrate with player service to get actual names
        // For now, return UUID as fallback
        return "Player_$playerId"
    }

    /**
     * Format timestamp for CSV
     */
    private fun formatTimestamp(timestamp: Instant): String {
        return dateFormatter.format(timestamp)
    }

    /**
     * Sanitize CSV field to prevent injection attacks
     */
    private fun sanitizeCsvField(field: String): String {
        // Remove or escape dangerous characters
        return field
            .replace("\"", "\"\"")  // Escape quotes by doubling them
            .replace(",", ";")      // Replace commas with semicolons
            .replace("\n", " ")     // Replace newlines with spaces
            .replace("\r", " ")     // Replace carriage returns with spaces
            .let { if (it.contains(",") || it.contains("\"") || it.contains("\n") || it.contains("\r")) "\"$it\"" else it }
    }

    /**
     * Validate CSV content size
     */
    fun validateCsvSize(csv: String, maxSizeBytes: Int = 1024 * 1024): Boolean { // 1MB default
        return csv.toByteArray().size <= maxSizeBytes
    }

    /**
     * Generate CSV for guild chests
     */
    fun generateGuildChestsCsv(chests: List<GuildChest>): String {
        val csv = StringBuilder()

        // CSV Header
        csv.append("Chest ID,Guild ID,World ID,X,Y,Z,Chest Size,Max Size,Is Locked,Last Accessed,Created At\n")

        // CSV Data rows
        chests.forEach { chest ->
            val formattedLastAccessed = formatTimestamp(chest.lastAccessed)
            val formattedCreatedAt = formatTimestamp(chest.createdAt)

            csv.append("${chest.id},")  // Chest ID
            csv.append("${chest.guildId},")  // Guild ID
            csv.append("${chest.worldId},")  // World ID
            csv.append("${chest.location.x},")  // X
            csv.append("${chest.location.y},")  // Y
            csv.append("${chest.location.z},")  // Z
            csv.append("${chest.chestSize},")  // Chest Size
            csv.append("${chest.maxSize},")  // Max Size
            csv.append("${chest.isLocked},")  // Is Locked
            csv.append("$formattedLastAccessed,")  // Last Accessed
            csv.append("$formattedCreatedAt\n")  // Created At
        }

        return csv.toString()
    }

    /**
     * Generate CSV for guild chest access logs
     */
    fun generateGuildChestAccessLogsCsv(logs: List<GuildChestAccessLog>): String {
        val csv = StringBuilder()

        // CSV Header
        csv.append("Log ID,Chest ID,Player ID,Player Name,Action,Timestamp,Item Type,Item Amount\n")

        // CSV Data rows
        logs.forEach { log ->
            val playerName = getPlayerName(log.playerId)
            val formattedTimestamp = formatTimestamp(log.timestamp)

            // Sanitize item type
            val sanitizedItemType = log.itemType?.let { sanitizeCsvField(it) } ?: ""

            csv.append("${log.id},")  // Log ID
            csv.append("${log.chestId},")  // Chest ID
            csv.append("${log.playerId},")  // Player ID
            csv.append("$playerName,")  // Player Name
            csv.append("${log.action},")  // Action
            csv.append("$formattedTimestamp,")  // Timestamp
            csv.append("$sanitizedItemType,")  // Item Type
            csv.append("${log.itemAmount}\n")  // Item Amount
        }

        return csv.toString()
    }

    /**
     * Generate comprehensive item banking report CSV
     */
    fun generateItemBankingReportCsv(
        guildId: UUID,
        guildName: String,
        chests: List<GuildChest>,
        accessLogs: List<GuildChestAccessLog>,
        totalItems: Int,
        totalValue: Double
    ): String {
        val csv = StringBuilder()

        // Guild Summary Header
        csv.append("Item Banking Report - $guildName\n")
        csv.append("Guild ID,$guildId\n")
        csv.append("Total Chests,${chests.size}\n")
        csv.append("Total Items,$totalItems\n")
        csv.append("Total Value,$$totalValue\n")
        csv.append("Export Date,${formatTimestamp(Instant.now())}\n\n")

        // Guild Chests Section
        csv.append("Guild Chests\n")
        csv.append(generateGuildChestsCsv(chests))
        csv.append("\n")

        // Access Logs Section (last 100 entries)
        csv.append("Recent Access Logs (Last 100)\n")
        csv.append(generateGuildChestAccessLogsCsv(accessLogs.take(100)))

        return csv.toString()
    }
}
