package net.lumalyte.lg.domain.values

/** REQ-195: one Yes/No line on the guild card. */
enum class GuildCardFact { HOME, BANNER, VAULT, SHOP }

/** REQ-195: what the guild card says. A null [shop] means no guild shop lookup is registered. */
data class GuildCardFacts(val home: Boolean, val banner: Boolean, val vault: Boolean, val shop: Boolean?) {
    /** The lines to show, in order; the shop line is left out when [shop] is unknown. */
    fun lines(): List<Pair<GuildCardFact, Boolean>> = buildList {
        add(GuildCardFact.HOME to home)
        add(GuildCardFact.BANNER to banner)
        add(GuildCardFact.VAULT to vault)
        shop?.let { add(GuildCardFact.SHOP to it) }
    }
}

/** REQ-196: one relations row: up to [shown] guilds and how many more there are. */
data class RelationRow<T>(val shown: List<T>, val more: Int) {
    companion object {
        const val MAX_SHOWN = 8

        fun <T> of(all: List<T>): RelationRow<T> = RelationRow(all.take(MAX_SHOWN), maxOf(0, all.size - MAX_SHOWN))
    }
}
