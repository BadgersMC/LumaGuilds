package net.lumalyte.lg.infrastructure.i18n

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocaleCompatibilityMigratorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `migrates persisted pre-season2 weekly quest templates and creates a backup`() {
        val original = """
            menu:
              quests:
                item:
                  header:
                    claimed: '<gray>Claimed: <white><claimed>/<total>'
                  quest:
                    name: '<gold><action> <target>'
                    description: '<gray><amount> <target><condition>'
                    completed: '<green>✅ Click to claim!'
                    claimed: '<dark_gray>✅ Claimed'
        """.trimIndent()
        val locale = localeFile(original)

        assertTrue(LocaleCompatibilityMigrator.migrate(tempDir.toFile()))

        val migrated = locale.readText()
        assertTrue(migrated.contains("name: '<gold><objective>'"))
        assertTrue(migrated.contains("description: '<gray><objective>'"))
        assertTrue(migrated.contains("claimed: '<gray>Completed: <white><claimed>/<total>'"))
        assertTrue(migrated.contains("completed: '<green>✅ Complete — reward processing'"))
        assertTrue(migrated.contains("claimed: '<green>✅ Complete — score keeps counting'"))
        assertFalse(migrated.contains("<action>"))
        assertFalse(migrated.contains("<condition>"))
        assertFalse(migrated.contains("Click to claim"))

        val backup = File(locale.parentFile, "en_US.yml.pre-season2-quests.bak")
        assertTrue(backup.isFile)
        assertEquals(original, backup.readText())
    }

    @Test
    fun `current weekly quest locale is left untouched`() {
        val current = """
            menu:
              quests:
                item:
                  quest:
                    name: '<gold><objective>'
                    description: '<gray><objective>'
        """.trimIndent()
        val locale = localeFile(current)

        assertFalse(LocaleCompatibilityMigrator.migrate(tempDir.toFile()))
        assertEquals(current, locale.readText())
        assertFalse(File(locale.parentFile, "en_US.yml.pre-season2-quests.bak").exists())
    }

    private fun localeFile(content: String): File {
        val locale = tempDir.resolve("lang/en_US.yml").toFile()
        locale.parentFile.mkdirs()
        locale.writeText(content)
        return locale
    }
}
