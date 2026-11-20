package net.lumalyte.lg.domain.values

import java.util.Locale

/** REQ-221: what /pc was asked to do. A guild has one party, so it is never named. */
sealed interface PartyChatIntent {
    data object Toggle : PartyChatIntent
    data object On : PartyChatIntent
    data object Off : PartyChatIntent
    data object Mute : PartyChatIntent
    data object Help : PartyChatIntent
    data class Message(val text: String) : PartyChatIntent

    companion object {
        fun parse(args: List<String>): PartyChatIntent {
            if (args.isEmpty()) return Toggle
            val first = args[0].lowercase(Locale.ROOT)
            if (args.size == 1) {
                when (first) {
                    "on" -> return On
                    "off", "clear", "global" -> return Off
                    "mute", "toggle" -> return Mute
                    "help" -> return Help
                }
            }
            if (first == "switch") {
                // The old "/pc switch <party>" still works; the name no longer matters.
                return if (args.getOrNull(1)?.equals("global", ignoreCase = true) == true) Off else On
            }
            return Message(args.joinToString(" "))
        }
    }
}
