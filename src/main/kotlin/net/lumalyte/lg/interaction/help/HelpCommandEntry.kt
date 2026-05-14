package net.lumalyte.lg.interaction.help

/**
 * One command line in a [HelpTopic]'s command list, shown both in-game and
 * (via the parity check) referenced by the corresponding wiki page.
 */
data class HelpCommandEntry(
    val syntax: String,
    val blurb: String,
    val prefill: String,
)
