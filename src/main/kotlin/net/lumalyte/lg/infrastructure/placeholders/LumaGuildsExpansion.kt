package net.lumalyte.lg.infrastructure.placeholders

import net.lumalyte.lg.application.services.*
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * PlaceholderAPI expansion for LumaGuilds plugin.
 * Provides placeholders for guild information in chat, scoreboards, and other plugins.
 *
 * Available placeholders:
 * - %lumaguilds_guild_name% - Player's guild name
 * - %lumaguilds_guild_tag% - Player's guild tag (formatted)
 * - %lumaguilds_guild_emoji% - Player's guild emoji (Nexo format)
 * - %lumaguilds_guild_level% - Player's guild level
 * - %lumaguilds_guild_balance% - Player's guild bank balance
 * - %lumaguilds_guild_members% - Player's guild member count
 * - %lumaguilds_guild_rank% - Player's rank in guild
 * - %lumaguilds_guild_mode% - Player's guild mode (Peaceful/Hostile)
 * - %lumaguilds_has_guild% - Whether player has a guild (true/false)
 * - %lumaguilds_guild_kills% - Player's guild total kills
 * - %lumaguilds_guild_deaths% - Player's guild total deaths
 * - %lumaguilds_guild_kdr% - Player's guild K/D ratio
 */
class LumaGuildsExpansion : PlaceholderExpansion(), KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val killService: KillService by inject()
    private val rankService: RankService by inject()
    private val warService: WarService by inject()

    override fun getIdentifier(): String = "lumaguilds"

    override fun getAuthor(): String = "LumaGuilds Team"

    override fun getVersion(): String = "1.0.0"

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, identifier: String): String? {
        if (player == null) return null

        val playerId = player.uniqueId

        // Check if player has a guild
        val playerGuilds = memberService.getPlayerGuilds(playerId)
        val guildId = playerGuilds.firstOrNull() ?: return when (identifier) {
            "has_guild" -> "false"
            else -> "" // Return empty string for other placeholders if no guild
        }

        // Get guild information
        val guild = guildService.getGuild(guildId) ?: return ""

        return when (identifier.lowercase()) {
            // Basic guild info
            "guild_name" -> guild.name
            "guild_tag" -> guild.tag ?: ""
            "guild_emoji" -> guild.emoji ?: ""
            "guild_level" -> guild.level.toString()
            "guild_balance" -> guild.bankBalance.toString()
            "guild_mode" -> guild.mode.toString()
            "has_guild" -> "true"

            // Member info
            "guild_members" -> memberService.getMemberCount(guildId).toString()
            "guild_rank" -> {
                val rankId = memberService.getPlayerRankId(playerId, guildId)
                if (rankId != null) {
                    val rank = rankService.getRank(rankId)
                    rank?.name ?: "Unknown"
                } else {
                    "Unknown"
                }
            }

            // Kill stats
            "guild_kills" -> {
                try {
                    val killStats = killService.getGuildKillStats(guildId)
                    killStats.totalKills.toString()
                } catch (e: Exception) {
                    "0"
                }
            }

            "guild_deaths" -> {
                try {
                    val killStats = killService.getGuildKillStats(guildId)
                    killStats.totalDeaths.toString()
                } catch (e: Exception) {
                    "0"
                }
            }

            "guild_kdr" -> {
                try {
                    val killStats = killService.getGuildKillStats(guildId)
                    String.format("%.2f", killStats.killDeathRatio)
                } catch (e: Exception) {
                    "0.00"
                }
            }

            // War stats
            "guild_wars_total" -> {
                try {
                    warService.getWarHistory(guildId, Int.MAX_VALUE).size.toString()
                } catch (e: Exception) {
                    "0"
                }
            }

            "guild_wars_active" -> {
                try {
                    warService.getWarsForGuild(guildId).filter { it.isActive }.size.toString()
                } catch (e: Exception) {
                    "0"
                }
            }

            // Performance stats
            "guild_efficiency" -> {
                try {
                    val killStats = killService.getGuildKillStats(guildId)
                    val totalActions = killStats.totalKills + killStats.totalDeaths
                    if (totalActions > 0) {
                        String.format("%.1f%%", (killStats.totalKills.toDouble() / totalActions) * 100)
                    } else {
                        "0.0%"
                    }
                } catch (e: Exception) {
                    "0.0%"
                }
            }

            // Formatted display (combines multiple fields)
            "guild_display" -> {
                val parts = mutableListOf<String>()

                // Add emoji if available
                guild.emoji?.let { parts.add(it) }

                // Add tag if available, otherwise name
                if (guild.tag != null) {
                    parts.add(guild.tag!!)
                } else {
                    parts.add(guild.name)
                }

                // Add level
                parts.add("[${guild.level}]")

                parts.joinToString(" ")
            }

            // Chat format (for use in chat plugins)
            "guild_chat_format" -> {
                val emoji = guild.emoji ?: ""
                val tag = guild.tag ?: guild.name
                "$emoji $tag"
            }

            else -> {
                // Handle relational placeholders: %lumaguilds_rel_<player>_status%
                if (identifier.startsWith("rel_", ignoreCase = true)) {
                    return handleRelationalPlaceholder(player, identifier)
                }
                null // Placeholder not found
            }
        }
    }

    /**
     * Handles relational placeholders for guild relationships
     * Format: %lumaguilds_rel_<playername>_status%
     * Returns: "🔴" for enemy (at war), "🟢" for ally, "" for neutral
     */
    private fun handleRelationalPlaceholder(player: Player?, params: String): String? {
        if (player == null) return ""

        // Parse the relational placeholder: rel_<playername>_status
        val parts = params.split("_")
        if (parts.size < 3 || parts[0].lowercase() != "rel" || parts.last().lowercase() != "status") {
            return null // Invalid format
        }

        // Extract player name (everything between "rel_" and "_status")
        val otherPlayerName = parts.drop(1).dropLast(1).joinToString("_")

        // Find the other player by name
        val otherPlayer = Bukkit.getPlayer(otherPlayerName)
        if (otherPlayer == null) return "" // Player not online

        // Get guild IDs for both players
        val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)
        val otherGuilds = memberService.getPlayerGuilds(otherPlayer.uniqueId)

        val playerGuildId = playerGuilds.firstOrNull()
        val otherGuildId = otherGuilds.firstOrNull()

        // If either player is not in a guild, they're neutral
        if (playerGuildId == null || otherGuildId == null) return ""

        // Same guild = neutral (not enemy or ally between guilds)
        if (playerGuildId == otherGuildId) return ""

        // Check if guilds are at war (enemies)
        try {
            val wars = warService.getWarsForGuild(playerGuildId)
            val activeWar = wars.find { it.isActive && (it.declaringGuildId == otherGuildId || it.defendingGuildId == otherGuildId) }
            if (activeWar != null) return "🔴" // Enemy (at war)
        } catch (e: Exception) {
            // War service not available, continue to check allies
        }

        // TODO: Add ally system here and return "🟢" for allies
        // For now, no ally system implemented, so return neutral
        return ""
    }

    /**
     * Formats large numbers with K/M suffixes for display
     */
    private fun formatNumber(number: Long): String {
        return when {
            number >= 1_000_000L -> "${number / 1_000_000L}M"
            number >= 1_000L -> "${number / 1_000L}K"
            else -> number.toString()
        }
    }
}
