package net.lumalyte.lg.infrastructure.services

private const val READER_COLUMNS = 24
private const val READER_ROWS = 8

/** Word wrapping retains all text, including supplementary Unicode and newline-heavy valid answers. */
internal fun recruitmentReaderPages(lines: List<String>): List<String> {
    val rows = lines.flatMap { value -> value.lines().flatMap(::readerRows) }
    return rows.chunked(READER_ROWS).map { it.joinToString("\n") }.ifEmpty { listOf("") }
}

private fun readerRows(line: String): List<String> {
    val points = line.codePoints().toArray()
    if (points.isEmpty()) return listOf("")
    val rows = mutableListOf<String>()
    var start = 0
    while (start < points.size) {
        val end = readerEnd(points, start)
        rows += buildString { for (index in start until end) appendCodePoint(points[index]) }
        start = end
    }
    return rows
}

private fun readerEnd(points: IntArray, start: Int): Int {
    val hardEnd = minOf(start + READER_COLUMNS, points.size)
    val space =
        if (hardEnd < points.size) {
            (start until hardEnd).lastOrNull { Character.isWhitespace(points[it]) }
        } else {
            null
        }
    return space?.plus(1) ?: hardEnd
}
