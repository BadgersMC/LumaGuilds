package net.lumalyte.lg.infrastructure.i18n

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

/**
 * Applies narrowly-scoped upgrades to persisted locale values whose
 * placeholder contract changed between releases.
 */
object LocaleCompatibilityMigrator {
    private const val LEGACY_QUEST_NAME = "name: '<gold><action> <target>'"
    private const val CURRENT_QUEST_NAME = "name: '<gold><objective>'"
    private const val LEGACY_QUEST_DESCRIPTION = "description: '<gray><amount> <target><condition>'"
    private const val CURRENT_QUEST_DESCRIPTION = "description: '<gray><objective>'"
    private const val LEGACY_QUEST_HEADER = "claimed: '<gray>Claimed: <white><claimed>/<total>'"
    private const val CURRENT_QUEST_HEADER = "claimed: '<gray>Completed: <white><claimed>/<total>'"
    private const val LEGACY_QUEST_COMPLETED = "completed: '<green>✅ Click to claim!'"
    private const val CURRENT_QUEST_COMPLETED = "completed: '<green>✅ Complete — reward processing'"
    private const val LEGACY_QUEST_CLAIMED = "claimed: '<dark_gray>✅ Claimed'"
    private const val CURRENT_QUEST_CLAIMED = "claimed: '<green>✅ Complete — score keeps counting'"
    private const val BACKUP_NAME = "en_US.yml.pre-season2-quests.bak"

    fun migrate(dataFolder: File, logger: Logger? = null): Boolean {
        val localeFile = File(dataFolder, "lang/en_US.yml")
        if (!localeFile.isFile) return false

        val original = localeFile.readText(StandardCharsets.UTF_8)
        val migrated = original
            .replace(LEGACY_QUEST_NAME, CURRENT_QUEST_NAME)
            .replace(LEGACY_QUEST_DESCRIPTION, CURRENT_QUEST_DESCRIPTION)
            .replace(LEGACY_QUEST_HEADER, CURRENT_QUEST_HEADER)
            .replace(LEGACY_QUEST_COMPLETED, CURRENT_QUEST_COMPLETED)
            .replace(LEGACY_QUEST_CLAIMED, CURRENT_QUEST_CLAIMED)

        if (migrated == original) return false

        val backup = File(localeFile.parentFile, BACKUP_NAME)
        if (!backup.exists()) {
            localeFile.copyTo(backup)
        }

        localeFile.writeText(migrated, StandardCharsets.UTF_8)
        logger?.info("Migrated legacy weekly quest locale values to the Season 2 quest format.")
        return true
    }
}
