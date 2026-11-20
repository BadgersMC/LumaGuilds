package net.lumalyte.lg.domain.values

import java.util.Locale

/**
 * REQ-216 to REQ-218: which emojis a guild may pick.
 *
 * [allowed] is open to every guild. [guildOnly] maps a lower-case guild name to that
 * guild's own logo; a logo is only offered to its guild, even if it is also on [allowed].
 */
data class GuildEmojiRules(
    val allowed: List<String> = DEFAULT_ALLOWED,
    val guildOnly: Map<String, String> = emptyMap(),
) {
    /** What the picker shows for [guildName]: its own logo first, then the approved list. */
    fun choices(guildName: String?): List<String> {
        val reserved = guildOnly.values.toSet()
        val own = guildName?.let { guildOnly[key(it)] }
        return (listOfNotNull(own) + allowed.filter { it !in reserved }).distinct()
    }

    /** Whether [guildName] may save [emoji]; [current] is the emoji it already has (REQ-218). */
    fun canPick(emoji: String, guildName: String?, current: String?): Boolean =
        emoji == current || emoji in choices(guildName)

    companion object {
        fun key(guildName: String): String = guildName.trim().lowercase(Locale.ROOT)

        /** The Redstone Labs chat emojis: no donor heads and no guild logos. */
        val DEFAULT_ALLOWED = listOf(
            "angry", "imp", "sheesh", "clown", "innocent", "died", "laugh", "exploding_head", "look_left",
            "sleep", "expressionless", "look_right", "sunglasses", "eyes_left", "nerd", "thinking", "eyes_right",
            "omegalul", "face_monocle", "pleading", "wink", "freeze", "raised_eyebrow", "happy", "roblox_head",
            "head_bandage", "salute", "heart_eyes", "heart", "sad", "scream", "shusing", "skull", "vomit",
        )
    }
}
