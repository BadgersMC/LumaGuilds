package net.lumalyte.lg.infrastructure.vault

/** Current-run level unlocks for ordinary vault storage, independent of guild gold. */
internal object VaultLevelCapacity {
    fun slots(level: Int): Int = when {
        level <= 1 -> 9
        level in 2..5 -> 18
        level in 6..10 -> 27
        level in 11..15 -> 36
        level in 16..20 -> 45
        else -> 54
    }
}
