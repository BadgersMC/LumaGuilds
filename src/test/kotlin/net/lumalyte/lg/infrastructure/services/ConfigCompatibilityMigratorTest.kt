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
        val original = listOf(
            "progression:",
            "  # Keep false until Chapter 2 migration and player interfaces are ready.",
            "  # This switch never migrates guilds; missing reward accounts block benefit reads.",
            "  chapter_two_rewards_enabled: false",
            "  max_level: 100",
            "  # Prestige is deliberately gated off until migration/readiness and live validation are complete.",
            "  prestige:",
            "    enabled: false",
            "    max_count: 6",
            "",
        ).joinToString("\n")
        val config = configFile(tempDir, original)

        assertTrue(ConfigCompatibilityMigrator.migrate(tempDir.toFile()))

        val migrated = config.readText()
        assertTrue(migrated.contains("chapter_two_rewards_enabled: true"))
        assertTrue(migrated.contains("Chapter 2 reward reads and purchases are production-ready"))
        assertTrue(migrated.contains("# Prestige is production-ready. Set false to disable it explicitly."))
        assertTrue(migrated.contains("prestige:\n    enabled: true"))
        val backup = File(tempDir.toFile(), "config.yml.pre-chapter2-production.bak")
        assertTrue(backup.isFile)
        assertEquals(original, backup.readText())
    }

    @Test
    fun customFalseValuesStay(@TempDir tempDir: Path) {
        val original = listOf(
            "progression:",
            "  chapter_two_rewards_enabled: false",
            "  prestige:",
            "    enabled: false",
        ).joinToString("\n")
        val config = configFile(tempDir, original)

        assertFalse(ConfigCompatibilityMigrator.migrate(tempDir.toFile()))

        assertEquals(original, config.readText())
        assertFalse(File(tempDir.toFile(), "config.yml.pre-chapter2-production.bak").exists())
    }

    @Test
    fun customPrestigeTrueStays(@TempDir tempDir: Path) {
        val original = listOf(
            "progression:",
            "  # Prestige is deliberately gated off until migration/readiness and live validation are complete.",
            "  prestige:",
            "    enabled: true",
        ).joinToString("\n")
        val config = configFile(tempDir, original)

        assertFalse(ConfigCompatibilityMigrator.migrate(tempDir.toFile()))
        assertEquals(original, config.readText())
    }

    private fun configFile(tempDir: Path, content: String): File =
        tempDir.resolve("config.yml").toFile().apply { writeText(content) }
}
