package net.lumalyte.lg.infrastructure.placeholders

import net.lumalyte.lg.domain.rewards.PrestigeStanding
import net.lumalyte.lg.domain.values.PrestigeNumeral
import net.lumalyte.lg.utils.ColorCodeUtils
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** REQ-155 formatting. An unreadable standing is blank, never a fabricated tier. */
object PrestigePlaceholder {
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    /** Blank below tier I, unlike [PrestigeNumeral], so placeholder users can hide it. */
    fun roman(tier: Int): String = if (tier in 1..6) PrestigeNumeral.of(tier) else ""

    fun badge(tier: Int, badges: List<String>): String = if (tier <= 0) "" else badges.getOrElse(tier) { "" }

    /** Wraps each glyph in its own font; the proxy draws text in minecraft:default otherwise. */
    fun withFonts(miniMessage: String, fontOf: (Char) -> String?): String = buildString {
        miniMessage.forEach { c ->
            val font = fontOf(c)?.takeUnless { it.isBlank() || it == "minecraft:default" }
            if (font == null) append(c) else append("<font:").append(font).append('>').append(c).append("</font>")
        }
    }

    fun value(
        identifier: String,
        standing: PrestigeStanding?,
        badges: List<String>,
        maxCount: Int,
        fontOf: (Char) -> String? = { null },
    ): String {
        if (identifier == "guild_prestige_max") return maxCount.toString()
        if (standing == null) return ""
        return when (identifier) {
            "guild_prestige" -> standing.tier.toString()
            "guild_prestige_roman" -> roman(standing.tier)
            "guild_prestige_badge" -> badge(standing.tier, badges)
            // REQ-219: Velocitab and other MiniMessage tab lists show "&f" literally.
            "guild_prestige_badge_minimessage" -> badge(standing.tier, badges).let { if (it.isEmpty()) it else withFonts(ColorCodeUtils.toMiniMessage(it), fontOf) }
            "guild_prestige_first_tier" -> standing.firstTier.toString()
            "guild_prestige_last_date" -> standing.lastAppliedAt?.let(DATE::format) ?: ""
            else -> ""
        }
    }
}
