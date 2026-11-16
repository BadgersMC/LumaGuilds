package net.lumalyte.lg.interaction.menus.guild

/**
 * REQ-258: where things sit in the Java guild stall menus. Stalls are centred in a 3-row menu
 * (one in the middle, two either side of it); a stall's members are centred under it.
 */
internal object GuildStallLayout {
    const val WIDTH = 9
    const val LIST_ROWS = 3
    const val LIST_PAGE = 7
    const val LIST_BACK = 22
    const val LIST_STATUS = 13
    const val DETAIL_ROWS = 6
    const val DETAIL_STALL = 4
    const val DETAIL_PER_ROW = 7
    const val DETAIL_PAGE = DETAIL_PER_ROW * 4
    const val DETAIL_BACK = 49
    const val DETAIL_PREVIOUS = 45
    const val DETAIL_NEXT = 53
    val DETAIL_CONTROLS = listOf(47, 51, 48, 50)

    /** Stalls in the middle row; up to three are spaced a slot apart, more sit side by side. */
    fun stalls(count: Int): List<Int> {
        val n = count.coerceIn(0, LIST_PAGE)
        val spacing = if (n <= 3) 2 else 1
        val first = 13 - (n - 1) * spacing / 2
        return List(n) { first + it * spacing }
    }

    /** Members in rows of up to seven from the second row, each row centred. */
    fun members(count: Int): List<Int> {
        val n = count.coerceIn(0, DETAIL_PAGE)
        return (0 until n).map { index ->
            val row = index / DETAIL_PER_ROW
            val inRow = minOf(DETAIL_PER_ROW, n - row * DETAIL_PER_ROW)
            val first = 4 - inRow / 2
            (row + 1) * WIDTH + first + index % DETAIL_PER_ROW
        }
    }

    /** "stall18" reads "Stall 18"; "north_stall_2" reads "North Stall 2". */
    fun displayName(region: String): String =
        region.replace(Regex("([A-Za-z])(\\d)"), "$1 $2").replace(Regex("(\\d)([A-Za-z])"), "$1 $2")
            .split('_', '-', ' ').filter(String::isNotBlank)
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}
