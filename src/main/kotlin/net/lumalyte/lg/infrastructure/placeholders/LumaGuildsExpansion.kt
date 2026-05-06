package net.lumalyte.lg.infrastructure.placeholders

import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * PlaceholderAPI expansion for LumaGuilds plugin.
 * Provides placeholders for guild information in chat, scoreboards, and other plugins.
 *
 * Available placeholders:
 * - %lumaguilds_guild_name% - Player's guild name
 * - %lumaguilds_guild_tag% - Player's guild tag (formatted)
 * - %lumaguilds_guild_emoji% - Player's guild emoji (converted to %nexo_<emoji>% format for tab/scoreboard)
 * - %lumaguilds_guild_level% - Player's guild level
 * - %lumaguilds_guild_balance% - Player's guild bank balance
 * - %lumaguilds_guild_members% - Player's guild member count
 * - %lumaguilds_guild_rank% - Player's rank in guild
 * - %lumaguilds_guild_mode% - Player's guild mode (Peaceful/Hostile)
 * - %lumaguilds_has_guild% - Whether player has a guild (true/false)
 * - %lumaguilds_guild_kills% - Player's guild total kills
 * - %lumaguilds_guild_deaths% - Player's guild total deaths
 * - %lumaguilds_guild_kdr% - Player's guild K/D ratio
 * - %lumaguilds_rel_<player>_status% - Relationship with another player (🔴 enemy, 🔵 ally, 🟢 teammate, ⚪ truce, blank neutral)
 *
 * Progression detail (player's guild):
 * - %lumaguilds_guild_age_days% - Days since guild creation
 * - %lumaguilds_guild_xp_total% - Total experience earned
 * - %lumaguilds_guild_xp_this_level% - Experience earned in current level
 * - %lumaguilds_guild_xp_next_level% - Experience needed to complete current level
 * - %lumaguilds_guild_xp_to_next% - Experience remaining to level up
 * - %lumaguilds_guild_xp_progress_pct% - Percent progress through current level (e.g. "62.5")
 * - %lumaguilds_guild_online_members% - Member count currently online
 *
 * Player's guild rank in each leaderboard (returns "" if not in top 25):
 * - %lumaguilds_guild_rank_level%
 * - %lumaguilds_guild_rank_balance%
 * - %lumaguilds_guild_rank_activity%
 * - %lumaguilds_guild_rank_members%
 * - %lumaguilds_guild_rank_age%
 *
 * Global:
 * - %lumaguilds_guild_total_count% - Total guild count on the server
 *
 * Top-N leaderboard (cached 30s, top 25):
 *   Format: %lumaguilds_top_<category>_<rank>_<field>%
 *   category = level | balance | activity | members | age
 *   rank     = 1..25 (1-based)
 *   field    = name | tag | tag_plain | id | value | level | members |
 *              balance | activity | age_days | emoji
 *   Examples:
 *     %lumaguilds_top_balance_1_name%
 *     %lumaguilds_top_level_3_value%
 *     %lumaguilds_top_activity_2_members%
 */
class LumaGuildsExpansion : PlaceholderExpansion(), KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val bankService: BankService by inject()
    private val killService: KillService by inject()
    private val rankService: RankService by inject()
    private val warService: WarService by inject()
    private val relationService: RelationService by inject()
    private val progressionRepository: ProgressionRepository by inject()
    private val leaderboardRepository: LeaderboardRepository by inject()

    private val miniMessage = MiniMessage.miniMessage()
    private val plainSerializer = PlainTextComponentSerializer.plainText()

    // --- Top leaderboard cache (30s TTL) ---
    private data class CachedTop(val expiresAt: Instant, val rows: List<TopRow>)
    private data class TopRow(val guildId: UUID, val value: Double)
    private val topCache = ConcurrentHashMap<String, CachedTop>()
    private val topCacheTtlSeconds = 30L

    override fun getIdentifier(): String = "lumaguilds"

    override fun getAuthor(): String = "LumaGuilds Team"

    override fun getVersion(): String = "1.0.0"

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, identifier: String): String? {
        val ident = identifier.lowercase()

        // Global placeholders — no player required
        when {
            ident == "guild_total_count" -> return safeGuildCount()
            ident.startsWith("top_") -> return handleTopPlaceholder(ident)
        }

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
            "guild_tag" -> guild.tag ?: "§6${guild.name}"
            "guild_emoji" -> convertEmojiToNexoPlaceholder(guild.emoji)
            "guild_level" -> guild.level.toString()
            "guild_balance" -> guild.bankBalance.toString()
            "guild_mode" -> guild.mode.toString()
            "has_guild" -> "true"

            // Age and progression detail
            "guild_age_days" -> Duration.between(guild.createdAt, Instant.now()).toDays().toString()
            "guild_xp_total" -> safeProgression(guildId)?.totalExperience?.toString() ?: "0"
            "guild_xp_this_level" -> safeProgression(guildId)?.experienceThisLevel?.toString() ?: "0"
            "guild_xp_next_level" -> safeProgression(guildId)?.experienceForNextLevel?.toString() ?: "0"
            "guild_xp_to_next" -> safeProgression(guildId)?.experienceToNextLevel?.toString() ?: "0"
            "guild_xp_progress_pct" -> {
                val p = safeProgression(guildId)
                if (p != null) String.format(Locale.ROOT, "%.1f", p.levelProgress * 100.0) else "0.0"
            }

            // This player's guild rank within each leaderboard
            "guild_rank_level" -> rankInTop("level", guildId)
            "guild_rank_balance" -> rankInTop("balance", guildId)
            "guild_rank_activity" -> rankInTop("activity", guildId)
            "guild_rank_members" -> rankInTop("members", guildId)
            "guild_rank_age" -> rankInTop("age", guildId)

            // Online member count
            "guild_online_members" -> {
                try {
                    memberService.getGuildMembers(guildId).count {
                        Bukkit.getPlayer(it.playerId)?.isOnline == true
                    }.toString()
                } catch (_: Exception) { "0" }
            }

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
                    // PlaceholderAPI requires safe fallback - service errors must not bubble up
                    "0"
                }
            }

            "guild_deaths" -> {
                try {
                    val killStats = killService.getGuildKillStats(guildId)
                    killStats.totalDeaths.toString()
                } catch (e: Exception) {
                    // PlaceholderAPI requires safe fallback - service errors must not bubble up
                    "0"
                }
            }

            "guild_kdr" -> {
                try {
                    val killStats = killService.getGuildKillStats(guildId)
                    String.format("%.2f", killStats.killDeathRatio)
                } catch (e: Exception) {
                    // PlaceholderAPI requires safe fallback - service errors must not bubble up
                    "0.00"
                }
            }

            // War stats
            "guild_wars_total" -> {
                try {
                    warService.getWarHistory(guildId, Int.MAX_VALUE).size.toString()
                } catch (e: Exception) {
                    // PlaceholderAPI requires safe fallback - service errors must not bubble up
                    "0"
                }
            }

            "guild_wars_active" -> {
                try {
                    warService.getWarsForGuild(guildId).filter { it.isActive }.size.toString()
                } catch (e: Exception) {
                    // PlaceholderAPI requires safe fallback - service errors must not bubble up
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
                    // PlaceholderAPI requires safe fallback - service errors must not bubble up
                    "0.0%"
                }
            }

            // Formatted display (combines multiple fields)
            "guild_display" -> {
                val parts = mutableListOf<String>()

                // Add emoji if available (convert to Nexo format)
                guild.emoji?.let {
                    val nexoEmoji = convertEmojiToNexoPlaceholder(it)
                    if (nexoEmoji.isNotEmpty()) {
                        parts.add(nexoEmoji)
                    }
                }

                // Add tag if available, otherwise name in gold
                parts.add(guild.tag ?: "§6${guild.name}")

                // Add level
                parts.add("[${guild.level}]")

                parts.joinToString(" ")
            }

            // Chat format (for use in chat plugins)
            "guild_chat_format" -> {
                val emoji = convertEmojiToNexoPlaceholder(guild.emoji)
                val tag = guild.tag ?: "§6${guild.name}"
                if (emoji.isNotEmpty()) {
                    "$emoji $tag"
                } else {
                    tag
                }
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
     * Returns: "🔴" for enemy (at war), "🔵" for ally, "🟢" for teammate (same guild), "⚪" for truce, "" for neutral
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

        // Same guild = teammate (green dot)
        if (playerGuildId == otherGuildId) return "🟢"

        // Check relations between guilds
        try {
            val relation = relationService.getRelation(playerGuildId, otherGuildId)
            // Only render the indicator for active relations; a PENDING ally request must
            // not be rendered as accepted, and an EXPIRED truce/enemy must not be rendered.
            if (relation != null && relation.isActive()) {
                return when (relation.type) {
                    net.lumalyte.lg.domain.entities.RelationType.ENEMY -> "🔴"  // Enemy/War
                    net.lumalyte.lg.domain.entities.RelationType.ALLY -> "🔵"   // Ally
                    net.lumalyte.lg.domain.entities.RelationType.TRUCE -> "⚪"  // Truce
                    net.lumalyte.lg.domain.entities.RelationType.NEUTRAL -> ""  // Neutral
                }
            }
        } catch (e: Exception) {
                    // PlaceholderAPI requires safe fallback - service errors must not bubble up
            // Relation service error, return neutral
        }

        // Default to neutral (no relation)
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

    /**
     * Converts emoji from Discord format (:emoji:) to Nexo placeholder format (%nexo_emoji%)
     * Examples:
     * - ":clown:" -> "%nexo_clown%"
     * - ":fire:" -> "%nexo_fire%"
     * - null or empty -> ""
     */
    private fun convertEmojiToNexoPlaceholder(emoji: String?): String {
        if (emoji.isNullOrEmpty()) return ""

        // Check if emoji is in Discord format (:emoji:)
        if (emoji.startsWith(":") && emoji.endsWith(":") && emoji.length > 2) {
            // Extract emoji name (remove colons)
            val emojiName = emoji.substring(1, emoji.length - 1)
            // Return Nexo placeholder format
            return "%nexo_$emojiName%"
        }

        // If not in Discord format, return as-is
        return emoji
    }

    // -----------------------------------------------------------------
    // Top-N leaderboard placeholders
    // Format: top_<category>_<rank>_<field>
    //   category: level | balance | activity | members | age
    //   rank: 1..N (1-based)
    //   field: name | tag | tag_plain | id | value | level | members |
    //          balance | activity | age_days | emoji
    // Examples: top_balance_1_name, top_level_3_value, top_activity_2_members
    // -----------------------------------------------------------------
    private fun handleTopPlaceholder(ident: String): String {
        val parts = ident.split("_")
        // expected: ["top", category, rank, field...]; field may have underscores
        if (parts.size < 4) return ""
        val category = parts[1]
        val rank = parts[2].toIntOrNull() ?: return ""
        if (rank < 1) return ""
        val field = parts.drop(3).joinToString("_")
        if (category !in TOP_CATEGORIES) return ""

        val rows = getTop(category)
        val row = rows.getOrNull(rank - 1) ?: return ""
        val guild = guildService.getGuild(row.guildId) ?: return ""

        return formatTopField(guild, row, field)
    }

    private fun formatTopField(guild: Guild, row: TopRow, field: String): String = when (field) {
        "name" -> guild.name
        "tag" -> guild.tag ?: "§6${guild.name}"
        "tag_plain" -> guild.tag?.let { stripFormatting(it) } ?: guild.name
        "id" -> guild.id.toString()
        "value" -> formatScore(row.value)
        "level" -> guild.level.toString()
        "members" -> safeMemberCount(guild.id).toString()
        "balance" -> safeBalance(guild.id).toString()
        "activity" -> safeWeeklyActivityScore(guild.id).toString()
        "age_days" -> Duration.between(guild.createdAt, Instant.now()).toDays().toString()
        "emoji" -> convertEmojiToNexoPlaceholder(guild.emoji)
        else -> ""
    }

    private fun formatScore(score: Double): String {
        // age category stores epochSecond — render the level/balance/etc as integer where sensible
        return if (score % 1.0 == 0.0) score.toLong().toString() else String.format(Locale.ROOT, "%.1f", score)
    }

    private fun rankInTop(category: String, guildId: UUID): String {
        val rows = getTop(category)
        val idx = rows.indexOfFirst { it.guildId == guildId }
        return if (idx >= 0) (idx + 1).toString() else ""
    }

    private fun getTop(category: String): List<TopRow> {
        val now = Instant.now()
        val cached = topCache[category]
        if (cached != null && cached.expiresAt.isAfter(now)) return cached.rows
        val computed = try { computeTop(category) } catch (_: Exception) { emptyList() }
        topCache[category] = CachedTop(now.plusSeconds(topCacheTtlSeconds), computed)
        return computed
    }

    private fun computeTop(category: String): List<TopRow> {
        val limit = TOP_CACHE_LIMIT
        return when (category) {
            "level" -> guildService.getAllGuilds()
                .map { g ->
                    val xp = progressionRepository.getGuildProgression(g.id)?.totalExperience ?: 0
                    TopRow(g.id, g.level * 1_000_000.0 + xp)
                }
                .sortedByDescending { it.value }
                .take(limit)
            "balance" -> guildService.getAllGuilds()
                .map { g -> TopRow(g.id, safeBalance(g.id).toDouble()) }
                .sortedByDescending { it.value }
                .take(limit)
            "activity" -> leaderboardRepository
                .getWeeklyActivityForPeriod(currentWeekStart(), 1000)
                .map { TopRow(it.guildId, it.totalScore.toDouble()) }
                .sortedByDescending { it.value }
                .take(limit)
            "members" -> guildService.getAllGuilds()
                .map { g -> TopRow(g.id, safeMemberCount(g.id).toDouble()) }
                .sortedByDescending { it.value }
                .take(limit)
            "age" -> guildService.getAllGuilds()
                .sortedBy { it.createdAt }
                .take(limit)
                .map { TopRow(it.id, it.createdAt.epochSecond.toDouble()) }
            else -> emptyList()
        }
    }

    private fun safeProgression(guildId: UUID) =
        try { progressionRepository.getGuildProgression(guildId) } catch (_: Exception) { null }

    private fun safeBalance(guildId: UUID): Int =
        try { bankService.getBalance(guildId) } catch (_: Exception) { 0 }

    private fun safeMemberCount(guildId: UUID): Int =
        try { memberService.getMemberCount(guildId) } catch (_: Exception) { 0 }

    private fun safeWeeklyActivityScore(guildId: UUID): Int =
        try {
            leaderboardRepository.getWeeklyActivity(guildId, currentWeekStart())?.totalScore ?: 0
        } catch (_: Exception) { 0 }

    private fun safeGuildCount(): String =
        try { guildService.getAllGuilds().size.toString() } catch (_: Exception) { "0" }

    private fun stripFormatting(tag: String): String =
        try {
            plainSerializer.serialize(miniMessage.deserialize(tag))
        } catch (_: Exception) {
            tag
        }

    private fun currentWeekStart(): Instant =
        ZonedDateTime.now(ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .truncatedTo(ChronoUnit.DAYS)
            .toInstant()

    companion object {
        private const val TOP_CACHE_LIMIT = 25
        private val TOP_CATEGORIES = setOf("level", "balance", "activity", "members", "age")
    }
}
