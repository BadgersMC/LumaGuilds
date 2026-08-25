package net.lumalyte.lg.utils

import java.time.Duration

object QuestDisplayFormatter {
    fun token(value: String): String = value.lowercase().split('_').joinToString(" ") {
        it.replaceFirstChar(Char::uppercase)
    }

    fun duration(value: Duration): String {
        val days = value.toDays()
        val hours = value.minusDays(days).toHours()
        val minutes = value.minusDays(days).minusHours(hours).toMinutes()
        return "${days}d ${hours}h ${minutes}m"
    }
}
