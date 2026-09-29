package net.lumalyte.lg.infrastructure.i18n

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class LocaleCompatibilityMigratorTest {
    @Test
    fun migratesQuestAndPrestige(@TempDir tempDir: Path) {
        val original = LEGACY_QUEST_AND_PRESTIGE
        val locale = localeFile(tempDir, original)

        assertTrue(LocaleCompatibilityMigrator.migrate(tempDir.toFile()))

        val migrated = locale.readText()
        assertQuestMigration(migrated)
        assertTrue(migrated.contains(CURRENT_REQUIREMENT))
        assertFalse(migrated.contains(LEGACY_REQUIREMENT))
        assertQuestAndPrestigeBackups(locale, original)
    }

    @Test
    fun leavesCurrentLocaleUntouched(@TempDir tempDir: Path) {
        val current = """
            menu:
              quests:
                item:
                  quest:
                    name: '<gold><objective>'
                    description: '<gray><objective>'
        """.trimIndent()
        val locale = localeFile(tempDir, current)

        assertFalse(LocaleCompatibilityMigrator.migrate(tempDir.toFile()))
        assertEquals(current, locale.readText())
        assertFalse(File(locale.parentFile, QUEST_BACKUP).exists())
        assertFalse(File(locale.parentFile, PRESTIGE_BACKUP).exists())
    }

    @Test
    fun migratesPrestigeRequirement(@TempDir tempDir: Path) {
        val original = """
            menu:
              guild_progression:
                prestige:
                  $LEGACY_REQUIREMENT
        """.trimIndent()
        val locale = localeFile(tempDir, original)

        assertTrue(LocaleCompatibilityMigrator.migrate(tempDir.toFile()))

        assertTrue(locale.readText().contains(CURRENT_REQUIREMENT))
        assertFalse(File(locale.parentFile, QUEST_BACKUP).exists())
        val backup = File(locale.parentFile, PRESTIGE_BACKUP)
        assertTrue(backup.isFile)
        assertEquals(original, backup.readText())
    }

    private fun assertQuestMigration(migrated: String) {
        assertTrue(migrated.contains("name: '<gold><objective>'"))
        assertTrue(migrated.contains("description: '<gray><objective>'"))
        assertTrue(migrated.contains("claimed: '<gray>Completed: <white><claimed>/<total>'"))
        assertTrue(migrated.contains("completed: '<green>✅ Complete — reward processing'"))
        assertTrue(migrated.contains("claimed: '<green>✅ Complete — score keeps counting'"))
        assertFalse(migrated.contains("<action>"))
        assertFalse(migrated.contains("<condition>"))
        assertFalse(migrated.contains("Click to claim"))
    }

    private fun assertQuestAndPrestigeBackups(locale: File, original: String) {
        val questBackup = File(locale.parentFile, QUEST_BACKUP)
        assertTrue(questBackup.isFile)
        assertEquals(original, questBackup.readText())
        val prestigeBackup = File(locale.parentFile, PRESTIGE_BACKUP)
        assertTrue(prestigeBackup.isFile)
        assertEquals(original, prestigeBackup.readText())
    }

    private fun localeFile(tempDir: Path, content: String): File {
        val locale = tempDir.resolve("lang/en_US.yml").toFile()
        locale.parentFile.mkdirs()
        locale.writeText(content)
        return locale
    }

    private companion object {
        const val LEGACY_REQUIREMENT = "requirement: \"<gray>Requires Level <light_purple>25\""
        const val CURRENT_REQUIREMENT = "requirement: \"<gray>Requires Level <light_purple>100\""
        const val QUEST_BACKUP = "en_US.yml.pre-season2-quests.bak"
        const val PRESTIGE_BACKUP = "en_US.yml.pre-prestige-100.bak"

        val LEGACY_QUEST_AND_PRESTIGE =
            listOf(
                "menu:",
                "  quests:",
                "    item:",
                "      header:",
                "        claimed: '<gray>Claimed: <white><claimed>/<total>'",
                "      quest:",
                "        name: '<gold><action> <target>'",
                "        description: '<gray><amount> <target><condition>'",
                "        completed: '<green>✅ Click to claim!'",
                "        claimed: '<dark_gray>✅ Claimed'",
                "  guild_progression:",
                "    prestige:",
                "      $LEGACY_REQUIREMENT",
            ).joinToString("\n")
    }
}
