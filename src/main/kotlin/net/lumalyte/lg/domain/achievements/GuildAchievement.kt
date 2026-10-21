package net.lumalyte.lg.domain.achievements

/** Personal milestone records. Guild activity determines recipients in the platform adapter. */
data class GuildAchievement(
    val id: String, val parent: String?, val title: String, val description: List<String>,
    val requirement: String, val target: String?, val goal: Int, val personalXp: Int,
    val messages: List<String>, val category: String, val icon: String, val frame: String,
    val x: Float, val y: Float,
) {
    init {
        require(id.matches(Regex("[a-z0-9_]+")) && goal > 0 && personalXp in 0..10000)
        require(title.isNotBlank() && category.isNotBlank())
    }
}

data class AchievementRecord(
    val progress: Map<String, Int> = emptyMap(),
    val rewarded: Set<String> = emptySet(),
    val receipts: Set<String> = emptySet(),
    val tracked: String? = null,
)

data class AchievementChange(val record: AchievementRecord, val completed: List<GuildAchievement>)

/** Pure bounded transition. Replayed event receipts never increment counters or issue another reward. */
class GuildAchievementEngine(val definitions: List<GuildAchievement>) {
    init {
        require(definitions.map { it.id }.distinct().size == definitions.size)
        require(definitions.all { it.parent == null || definitions.any { p -> p.id == it.parent } })
        require(definitions.none { it.parent == it.id })
    }

    fun pending(before: AchievementRecord): AchievementChange {
        val completed = definitions.filter { (before.progress[it.id] ?: 0) >= it.goal && it.id !in before.rewarded }
        return AchievementChange(before.copy(rewarded = before.rewarded + completed.map { it.id }), completed)
    }

    fun advance(before: AchievementRecord, requirement: String, target: String? = null,
        amount: Int = 1, absolute: Boolean = false, receipt: String? = null): AchievementChange {
        require(amount >= 0)
        if (amount == 0 || receipt != null && receipt in before.receipts) return AchievementChange(before, emptyList())
        val progress = before.progress.toMutableMap()
        val completed = mutableListOf<GuildAchievement>()
        for (node in definitions.filter { it.requirement == requirement && matches(it, target) }) {
            val old = progress[node.id] ?: 0
            val next = (if (absolute) maxOf(old.toLong(), amount.toLong()) else old.toLong() + amount).coerceAtMost(node.goal.toLong()).toInt()
            progress[node.id] = next
            if (next == node.goal && node.id !in before.rewarded) completed += node
        }
        val changed = progress != before.progress
        // Only receipt-bearing commerce/KOTH events use this ledger. Stop storing once their counters are capped.
        val receipts = if (receipt != null && changed) before.receipts + receipt else before.receipts
        return AchievementChange(before.copy(progress = progress, rewarded = before.rewarded + completed.map { it.id },
            receipts = receipts), completed)
    }

    private fun matches(node: GuildAchievement, target: String?): Boolean = when (node.requirement) {
        "GUILD_LEVEL_UP" -> (target?.toIntOrNull() ?: -1) >= (node.target?.toIntOrNull() ?: Int.MAX_VALUE)
        "GUILD_LEADERBOARD_RANK" -> (target?.toIntOrNull() ?: Int.MAX_VALUE) in 1..(node.target?.toIntOrNull() ?: 0)
        else -> node.target == null || node.target == target
    }
}
