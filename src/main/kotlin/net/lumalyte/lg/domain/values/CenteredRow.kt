package net.lumalyte.lg.domain.values

/** REQ-205/REQ-206: columns for [count] items centred in a 9-wide chest row (at most 9). */
object CenteredRow {
    private const val WIDTH = 9

    fun slots(count: Int): List<Int> {
        val n = count.coerceIn(0, WIDTH)
        val start = (WIDTH - n + 1) / 2
        return (start until start + n).toList()
    }
}
