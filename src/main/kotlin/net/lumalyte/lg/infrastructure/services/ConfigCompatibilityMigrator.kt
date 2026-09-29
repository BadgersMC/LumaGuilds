package net.lumalyte.lg.infrastructure.services

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

/**
 * Applies narrowly-scoped upgrades to persisted config values that shipped as
 * temporary staging defaults. A value is changed only while its exact legacy
 * staging block is still present, so custom operator configuration remains intact.
 */
object ConfigCompatibilityMigrator {
    private const val BACKUP_NAME = "config.yml.pre-chapter2-production.bak"
    private const val CRLF = "\r\n"
    private const val LF = "\n"

    private val legacyRewardsBlock = listOf(
        "  # Keep false until Chapter 2 migration and player interfaces are ready.",
        "  # This switch never migrates guilds; missing reward accounts block benefit reads.",
        "  chapter_two_rewards_enabled: false",
    ).joinToString(LF)

    private val currentRewardsBlock = listOf(
        "  # Chapter 2 reward reads and purchases are production-ready. Set false to disable explicitly.",
        "  # Missing legacy reward accounts are reconciled safely during startup.",
        "  chapter_two_rewards_enabled: true",
    ).joinToString(LF)

    private val legacyPrestigeBlock = listOf(
        "  # Prestige is deliberately gated off until migration/readiness and live validation are complete.",
        "  prestige:",
        "    enabled: false",
    ).joinToString(LF)

    private val currentPrestigeBlock = listOf(
        "  # Prestige is production-ready. Set false to disable it explicitly.",
        "  prestige:",
        "    enabled: true",
    ).joinToString(LF)

    /**
     * Migrates a persisted config file when it still contains the exact shipped
     * Chapter 2 staging defaults.
     */
    fun migrate(dataFolder: File, logger: Logger? = null): Boolean {
        val configFile = File(dataFolder, "config.yml")
        return if (configFile.isFile) {
            migrateFile(configFile, dataFolder, logger)
        } else {
            false
        }
    }

    /**
     * Returns the config text with only exact shipped staging blocks promoted.
     */
    internal fun migrateStagingDefaults(text: String): String {
        val lineEnding = if (text.contains(CRLF)) CRLF else LF
        val normalized = text.replace(CRLF, LF)
        val migrated = normalized
            .replace(legacyRewardsBlock, currentRewardsBlock)
            .replace(legacyPrestigeBlock, currentPrestigeBlock)
        return if (lineEnding == CRLF) migrated.replace(LF, CRLF) else migrated
    }

    private fun migrateFile(configFile: File, dataFolder: File, logger: Logger?): Boolean {
        val original = configFile.readText(StandardCharsets.UTF_8)
        val migrated = migrateStagingDefaults(original)
        val changed = migrated != original
        if (changed) {
            backup(configFile, dataFolder)
            configFile.writeText(migrated, StandardCharsets.UTF_8)
            logger?.info(
                "Promoted legacy Chapter 2 staging defaults to production; " +
                    "explicit custom feature gates were preserved.",
            )
        }
        return changed
    }

    private fun backup(configFile: File, dataFolder: File) {
        val backup = File(dataFolder, BACKUP_NAME)
        if (!backup.exists()) {
            configFile.copyTo(backup)
        }
    }
}
