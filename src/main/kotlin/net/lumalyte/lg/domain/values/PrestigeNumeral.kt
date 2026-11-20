package net.lumalyte.lg.domain.values

/**
 * REQ-165: how a prestige tier or count reads to players. Tiers I to VI are Roman
 * numerals; zero (no prestige yet) and anything outside the lifetime range stay
 * plain numbers rather than inventing a numeral.
 */
object PrestigeNumeral {
    private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI")

    fun of(count: Int): String = ROMAN.getOrNull(count - 1) ?: count.toString()
}
