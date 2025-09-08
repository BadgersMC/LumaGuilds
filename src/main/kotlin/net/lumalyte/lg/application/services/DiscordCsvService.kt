package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.MemberContribution
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

/**
 * Service for delivering CSV files via Discord webhooks
 * Provides actual file downloads while maintaining security
 */
class DiscordCsvService(
    private val webhookUrl: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Send transaction history CSV to Discord
     */
    fun sendTransactionCsvAsync(
        player: Player,
        transactions: List<BankTransaction>,
        guildName: String,
        callback: (Result<String>) -> Unit
    ) {
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task, Runnable {
            try {
                val csvContent = generateTransactionCsv(transactions)
                val fileName = "guild_transactions_${guildName}_${System.currentTimeMillis()}.csv"

                val embed = createTransactionEmbed(player, transactions.size, guildName, csvContent.length)
                val result = sendFileToDiscord(csvContent, fileName, embed)

                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task, Runnable {
                    callback(result)
                })

            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task, Runnable {
                    callback(Result.failure(e))
                })
            }
        })
    }

    /**
     * Send member contributions CSV to Discord
     */
    fun sendContributionsCsvAsync(
        player: Player,
        contributions: List<MemberContribution>,
        guildName: String,
        callback: (Result<String>) -> Unit
    ) {
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task, Runnable {
            try {
                val csvContent = generateContributionsCsv(contributions)
                val fileName = "guild_contributions_${guildName}_${System.currentTimeMillis()}.csv"

                val embed = createContributionsEmbed(player, contributions.size, guildName, csvContent.length)
                val result = sendFileToDiscord(csvContent, fileName, embed)

                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task, Runnable {
                    callback(result)
                })

            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task, Runnable {
                    callback(Result.failure(e))
                })
            }
        })
    }

    /**
     * Generate CSV content for transactions
     */
    private fun generateTransactionCsv(transactions: List<BankTransaction>): String {
        val csv = StringBuilder()

        // CSV Header with better formatting
        csv.append("Transaction ID,Guild ID,Player Name,Player UUID,Type,Amount,Fee,Total Amount,Description,Timestamp\n")

        // CSV Data rows
        transactions.forEach { transaction ->
            val playerName = getPlayerDisplayName(transaction.actorId)
            val formattedTimestamp = formatTimestamp(transaction.timestamp)
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
     * Generate CSV content for member contributions
     */
    private fun generateContributionsCsv(contributions: List<MemberContribution>): String {
        val csv = StringBuilder()

        // CSV Header
        csv.append("Player Name,Player UUID,Total Deposits,Total Withdrawals,Net Contribution,Transaction Count,Status,Last Activity\n")

        // CSV Data rows
        contributions.forEach { contribution ->
            val playerName = contribution.playerName ?: "Unknown"
            val lastActivity = contribution.lastTransaction?.let { formatTimestamp(it) } ?: "Never"
            val status = contribution.contributionStatus.name

            val sanitizedName = sanitizeCsvField(playerName)

            csv.append("$sanitizedName,")
            csv.append("${contribution.playerId},")
            csv.append("${contribution.totalDeposits},")
            csv.append("${contribution.totalWithdrawals},")
            csv.append("${contribution.netContribution},")
            csv.append("${contribution.transactionCount},")
            csv.append("$status,")
            csv.append("$lastActivity\n")
        }

        return csv.toString()
    }

    /**
     * Create rich embed for transaction history
     */
    private fun createTransactionEmbed(player: Player, transactionCount: Int, guildName: String, fileSize: Int): JSONObject {
        val embed = JSONObject()

        embed.put("title", "📊 Guild Transaction History")
        embed.put("description", "CSV export requested by **${player.name}**")
        embed.put("color", 0x00FF00) // Green

        val fields = JSONArray()

        // Guild info
        val guildField = JSONObject()
        guildField.put("name", "🏰 Guild")
        guildField.put("value", guildName)
        guildField.put("inline", true)
        fields.put(guildField)

        // Transaction count
        val countField = JSONObject()
        countField.put("name", "📈 Transactions")
        countField.put("value", transactionCount.toString())
        countField.put("inline", true)
        fields.put(countField)

        // File size
        val sizeField = JSONObject()
        sizeField.put("name", "📁 File Size")
        sizeField.put("value", "${fileSize / 1024} KB")
        sizeField.put("inline", true)
        fields.put(sizeField)

        embed.put("fields", fields)

        // Footer with timestamp
        val footer = JSONObject()
        footer.put("text", "LumaGuilds Export System • ${formatTimestamp(Instant.now())}")
        embed.put("footer", footer)

        return embed
    }

    /**
     * Create rich embed for member contributions
     */
    private fun createContributionsEmbed(player: Player, memberCount: Int, guildName: String, fileSize: Int): JSONObject {
        val embed = JSONObject()

        embed.put("title", "👥 Guild Member Contributions")
        embed.put("description", "CSV export requested by **${player.name}**")
        embed.put("color", 0x0099FF) // Blue

        val fields = JSONArray()

        // Guild info
        val guildField = JSONObject()
        guildField.put("name", "🏰 Guild")
        guildField.put("value", guildName)
        guildField.put("inline", true)
        fields.put(guildField)

        // Member count
        val countField = JSONObject()
        countField.put("name", "👤 Members")
        countField.put("value", memberCount.toString())
        countField.put("inline", true)
        fields.put(countField)

        // File size
        val sizeField = JSONObject()
        sizeField.put("name", "📁 File Size")
        sizeField.put("value", "${fileSize / 1024} KB")
        sizeField.put("inline", true)
        fields.put(sizeField)

        embed.put("fields", fields)

        // Footer with timestamp
        val footer = JSONObject()
        footer.put("text", "LumaGuilds Export System • ${formatTimestamp(Instant.now())}")
        embed.put("footer", footer)

        return embed
    }

    /**
     * Send file to Discord webhook
     */
    private fun sendFileToDiscord(csvContent: String, fileName: String, embed: JSONObject): Result<String> {
        try {
            // Create multipart form data
            val boundary = "----FormBoundary${System.currentTimeMillis()}"
            val bodyBuilder = StringBuilder()

            // File part
            bodyBuilder.append("--$boundary\r\n")
            bodyBuilder.append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
            bodyBuilder.append("Content-Type: text/csv\r\n\r\n")
            bodyBuilder.append(csvContent)
            bodyBuilder.append("\r\n")

            // Payload JSON part
            bodyBuilder.append("--$boundary\r\n")
            bodyBuilder.append("Content-Disposition: form-data; name=\"payload_json\"\r\n")
            bodyBuilder.append("Content-Type: application/json\r\n\r\n")

            val payload = JSONObject()
            val embeds = JSONArray()
            embeds.put(embed)
            payload.put("embeds", embeds)
            payload.put("username", "LumaGuilds Export Bot")
            payload.put("avatar_url", "https://i.imgur.com/placeholder.png") // Could be customized

            bodyBuilder.append(payload.toString())
            bodyBuilder.append("\r\n--$boundary--\r\n")

            val body = bodyBuilder.toString()

            // Create HTTP request
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            // Send request
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            return if (response.statusCode() in 200..299) {
                Result.success("File sent to Discord successfully!")
            } else {
                Result.failure(IOException("Discord API error: ${response.statusCode()} - ${response.body()}"))
            }

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Get player display name (with fallback)
     */
    private fun getPlayerDisplayName(playerId: UUID): String {
        return Bukkit.getPlayer(playerId)?.name ?: "Player_$playerId"
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Instant): String {
        return dateFormatter.format(timestamp)
    }

    /**
     * Sanitize CSV field to prevent injection
     */
    private fun sanitizeCsvField(field: String): String {
        return field
            .replace("\"", "\"\"")
            .replace(",", ";")
            .replace("\n", " ")
            .replace("\r", " ")
            .let { if (it.contains(",") || it.contains("\"") || it.contains("\n") || it.contains("\r")) "\"$it\"" else it }
    }

    /**
     * Check if Discord integration is configured
     */
    fun isConfigured(): Boolean {
        return webhookUrl.isNotBlank() && webhookUrl.startsWith("https://discord.com/api/webhooks/")
    }
}
