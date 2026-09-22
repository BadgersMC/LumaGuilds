package net.lumalyte.lg.interaction.commands

internal fun pageBounds(totalItems: Int, page: Int, pageSize: Int = 10): IntRange? {
    if (page < 1 || pageSize < 1 || totalItems < 1) return null
    val startLong = (page.toLong() - 1L) * pageSize.toLong()
    if (startLong >= totalItems.toLong()) return null
    val start = startLong.toInt()
    val endExclusive = minOf(startLong + pageSize.toLong(), totalItems.toLong()).toInt()
    return start until endExclusive
}
