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
        var end = minOf(start + READER_COLUMNS, points.size)
        if (end < points.size) {
            val space = (start until end).lastOrNull { Character.isWhitespace(points[it]) }
            if (space != null) end = space + 1
        }
        rows += buildString { (start until end).forEach { appendCodePoint(points[it]) } }
        start = end
    }
    return rows
}
