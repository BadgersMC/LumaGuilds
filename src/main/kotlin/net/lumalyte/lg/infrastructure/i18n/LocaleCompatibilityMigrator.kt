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
    private const val LEGACY_PRESTIGE_REQUIREMENT = "requirement: \"<gray>Requires Level <light_purple>25\""
    private const val CURRENT_PRESTIGE_REQUIREMENT = "requirement: \"<gray>Requires Level <light_purple>100\""
    private const val QUEST_BACKUP_NAME = "en_US.yml.pre-season2-quests.bak"
    private const val PRESTIGE_BACKUP_NAME = "en_US.yml.pre-prestige-100.bak"

    fun migrate(dataFolder: File, logger: Logger? = null): Boolean {
        val localeFile = File(dataFolder, "lang/en_US.yml")
        if (!localeFile.isFile) return false

        val original = localeFile.readText(StandardCharsets.UTF_8)
        val needsQuestMigration = listOf(
            LEGACY_QUEST_NAME,
            LEGACY_QUEST_DESCRIPTION,
            LEGACY_QUEST_HEADER,
            LEGACY_QUEST_COMPLETED,
            LEGACY_QUEST_CLAIMED,
        ).any(original::contains)
        val needsPrestigeMigration = original.contains(LEGACY_PRESTIGE_REQUIREMENT)

        val migrated = original
            .replace(LEGACY_QUEST_NAME, CURRENT_QUEST_NAME)
            .replace(LEGACY_QUEST_DESCRIPTION, CURRENT_QUEST_DESCRIPTION)
            .replace(LEGACY_QUEST_HEADER, CURRENT_QUEST_HEADER)
            .replace(LEGACY_QUEST_COMPLETED, CURRENT_QUEST_COMPLETED)
            .replace(LEGACY_QUEST_CLAIMED, CURRENT_QUEST_CLAIMED)
            .replace(LEGACY_PRESTIGE_REQUIREMENT, CURRENT_PRESTIGE_REQUIREMENT)

        if (migrated == original) return false

        if (needsQuestMigration) {
            val backup = File(localeFile.parentFile, QUEST_BACKUP_NAME)
            if (!backup.exists()) localeFile.copyTo(backup)
        }
        if (needsPrestigeMigration) {
            val backup = File(localeFile.parentFile, PRESTIGE_BACKUP_NAME)
            if (!backup.exists()) localeFile.copyTo(backup)
        }

        localeFile.writeText(migrated, StandardCharsets.UTF_8)
        logger?.info("Migrated persisted Season 2 locale compatibility values.")
        return true
    }
}
