package net.lumalyte.lg.application.persistence

import java.util.UUID

/** A cleared goal retains its revision so old menus cannot overwrite later edits. */
data class ChronicleGoal(val rewardId: String?, val revision: Long) {
    init { require(revision >= 0) }
}

interface ChronicleGoalRepository {
    /** Missing is an empty revision-zero goal; storage errors must throw, never masquerade as missing. */
    fun read(guildId: UUID): ChronicleGoal
    fun compareAndSet(guildId: UUID, expected: ChronicleGoal, rewardId: String?): Boolean
}
