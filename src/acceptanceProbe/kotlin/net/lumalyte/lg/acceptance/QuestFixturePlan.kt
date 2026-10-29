package net.lumalyte.lg.acceptance

import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.QuestAction
import java.security.MessageDigest

/** Bounded definition-only fixture: never fabricates progress or awards a reward. */
object QuestFixturePlan {
    const val ID = "acceptance-deposit-5000-v1"

    fun fingerprint(week: WeeklyQuestSet): String = MessageDigest.getInstance("SHA-256")
        .digest(week.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun install(week: WeeklyQuestSet): WeeklyQuestSet {
        require(week.quests.size in 2..32 && week.quests.none { it.id == ID })
        val target = QuestTarget("lumaguilds:bank/coins", setOf(QuestAction.DEPOSIT_BANK), 5_000, 5_000)
        return week.copy(quests = week.quests + QuestDefinition(
            ID, action = QuestAction.DEPOSIT_BANK, target = target, targetCount = 5_000,
            tier = QuestRewardTier.COMMON, experienceReward = 500,
        ))
    }

    fun restore(current: WeeklyQuestSet, originalWeek: String, originalHash: String): WeeklyQuestSet {
        require(current.weekId == originalWeek) { "Active week changed; preserve and review" }
        val restored = current.copy(quests = current.quests.filterNot { it.id == ID })
        require(fingerprint(restored) == originalHash) { "Original definitions drifted; preserve and review" }
        return restored
    }
}
