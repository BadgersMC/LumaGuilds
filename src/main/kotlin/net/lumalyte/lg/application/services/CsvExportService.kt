package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.BankTransaction
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
}
