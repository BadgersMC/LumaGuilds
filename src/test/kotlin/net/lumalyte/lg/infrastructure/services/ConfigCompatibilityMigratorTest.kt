package net.lumalyte.lg.infrastructure.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ConfigCompatibilityMigratorTest {
    @Test
    fun legacyDefaultsPromote(@TempDir tempDir: Path) {
        val original = legacyDefaults()
        val config = configFile(tempDir, original)

        assertTrue(ConfigCompatibilityMigrator.migrate(tempDir.toFile()))

        assertPromoted(config.readText())
        assertBackup(tempDir, original)
    }

    @Test
    fun customFalseValuesStay(@TempDir tempDir: Path) {
        val original =
            listOf(
                PROGRESSION_LINE,
                REWARDS_FALSE,
                PRESTIGE_LINE,
                PRESTIGE_FALSE,
            ).joinToString("\n")
        val config = configFile(tempDir, original)

        assertFalse(ConfigCompatibilityMigrator.migrate(tempDir.toFile()))

        assertEquals(original, config.readText())
        assertFalse(File(tempDir.toFile(), BACKUP_NAME).exists())
    }

    @Test
    fun customPrestigeTrueStays(@TempDir tempDir: Path) {
        val original =
            listOf(
                PROGRESSION_LINE,
                LEGACY_PRESTIGE_COMMENT,
                PRESTIGE_LINE,
                PRESTIGE_TRUE,
            ).joinToString("\n")
        val config = configFile(tempDir, original)

        assertFalse(ConfigCompatibilityMigrator.migrate(tempDir.toFile()))
        assertEquals(original, config.readText())
    }

    private fun legacyDefaults(): String {
        return listOf(
            PROGRESSION_LINE,
            LEGACY_REWARDS_COMMENT,
            LEGACY_REWARDS_DETAIL,
            REWARDS_FALSE,
            "  max_level: 100",
            LEGACY_PRESTIGE_COMMENT,
            PRESTIGE_LINE,
            PRESTIGE_FALSE,
            "    max_count: 6",
            "",
        ).joinToString("\n")
    }

    private fun assertPromoted(migrated: String) {
        assertTrue(migrated.contains("chapter_two_rewards_enabled: true"))
        assertTrue(migrated.contains("Chapter 2 reward reads and purchases are production-ready"))
        assertTrue(migrated.contains("# Prestige is production-ready. Set false to disable it explicitly."))
        assertTrue(migrated.contains("prestige:\n    enabled: true"))
    }

    private fun assertBackup(tempDir: Path, original: String) {
        val backup = File(tempDir.toFile(), BACKUP_NAME)
        assertTrue(backup.isFile)
        assertEquals(original, backup.readText())
    }

    private fun configFile(tempDir: Path, content: String): File {
        return tempDir.resolve("config.yml").toFile().apply { writeText(content) }
    }

    private companion object {
        const val PROGRESSION_LINE = "progression:"
        const val REWARDS_FALSE = "  chapter_two_rewards_enabled: false"
        const val PRESTIGE_LINE = "  prestige:"
        const val PRESTIGE_FALSE = "    enabled: false"
        const val PRESTIGE_TRUE = "    enabled: true"
        const val BACKUP_NAME = "config.yml.pre-chapter2-production.bak"
        const val LEGACY_REWARDS_COMMENT =
            "  # Keep false until Chapter 2 migration and player interfaces are ready."
        const val LEGACY_REWARDS_DETAIL =
            "  # This switch never migrates guilds; missing reward accounts block benefit reads."
        const val LEGACY_PRESTIGE_COMMENT =
            "  # Prestige is deliberately gated off until migration/readiness and live validation are complete."
    }
}
