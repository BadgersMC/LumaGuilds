package net.lumalyte.lg.infrastructure.services

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

/**
 * Applies narrowly-scoped upgrades to persisted config values that shipped as
 * temporary staging defaults. A value is changed only while its exact legacy
 * staging marker is still present, so custom operator configuration remains intact.
 */
object ConfigCompatibilityMigrator {
    private const val LEGACY_REWARDS_COMMENT =
        "  # Keep false until Chapter 2 migration and player interfaces are ready."
    private const val LEGACY_REWARDS_DETAIL =
        "  # This switch never migrates guilds; missing reward accounts block benefit reads."
    private const val CURRENT_REWARDS_COMMENT =
        "  # Chapter 2 reward reads and purchases are production-ready. Set false to disable explicitly."
    private const val CURRENT_REWARDS_DETAIL =
        "  # Missing legacy reward accounts are reconciled safely during startup."

    private const val LEGACY_PRESTIGE_COMMENT =
        "  # Prestige is deliberately gated off until migration/readiness and live validation are complete."
    private const val CURRENT_PRESTIGE_COMMENT =
        "  # Prestige is production-ready. Set false to disable it explicitly."

    private const val BACKUP_NAME = "config.yml.pre-chapter2-production.bak"

    fun migrate(dataFolder: File, logger: Logger? = null): Boolean {
        val configFile = File(dataFolder, "config.yml")
        if (!configFile.isFile) return false

        val original = configFile.readText(StandardCharsets.UTF_8)
        val migrated = migrateProductionStagingDefaults(original)
        if (migrated == original) return false

        val backup = File(dataFolder, BACKUP_NAME)
        if (!backup.exists()) configFile.copyTo(backup)
        configFile.writeText(migrated, StandardCharsets.UTF_8)
        logger?.info(
            "Promoted legacy Chapter 2 staging defaults to production; explicit custom feature gates were preserved."
        )
        return true
    }

    internal fun migrateProductionStagingDefaults(text: String): String {
        val lineEnding = if ("\r\n" in text) "\r\n" else "\n"
        val hadTrailingNewline = text.endsWith("\n")
        val lines = text.split(Regex("\\r?\\n")).toMutableList().also {
            if (hadTrailingNewline && it.lastOrNull().isNullOrEmpty()) it.removeAt(it.lastIndex)
        }
        var changed = false

        val rewardsMarker = lines.indexOfFirst { it == LEGACY_REWARDS_COMMENT }
        if (rewardsMarker >= 0 &&
            lines.getOrNull(rewardsMarker + 1) == LEGACY_REWARDS_DETAIL
        ) {
            val enabled = (rewardsMarker + 2 until minOf(lines.size, rewardsMarker + 6))
                .firstOrNull { lines[it].trimStart().startsWith("chapter_two_rewards_enabled:") }
            if (enabled != null && lines[enabled].trim() == "chapter_two_rewards_enabled: false") {
                lines[rewardsMarker] = CURRENT_REWARDS_COMMENT
                lines[rewardsMarker + 1] = CURRENT_REWARDS_DETAIL
                lines[enabled] = lines[enabled].replace(
                    "chapter_two_rewards_enabled: false",
                    "chapter_two_rewards_enabled: true",
                )
                changed = true
            }
        }

        val prestigeMarker = lines.indexOfFirst { it == LEGACY_PRESTIGE_COMMENT }
        if (prestigeMarker >= 0) {
            val prestige = (prestigeMarker + 1 until minOf(lines.size, prestigeMarker + 5))
                .firstOrNull { lines[it].trim() == "prestige:" }
            val enabled = prestige?.let { start ->
                (start + 1 until minOf(lines.size, start + 5))
                    .firstOrNull { lines[it].trimStart().startsWith("enabled:") }
            }
            if (enabled != null && lines[enabled].trim() == "enabled: false") {
                lines[prestigeMarker] = CURRENT_PRESTIGE_COMMENT
                lines[enabled] = lines[enabled].replace("enabled: false", "enabled: true")
                changed = true
            }
        }

        if (!changed) return text
        return lines.joinToString(lineEnding).let { if (hadTrailingNewline) "$it$lineEnding" else it }
    }
}
