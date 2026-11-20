package net.lumalyte.lg.domain.rewards

import kotlin.math.pow
import kotlin.math.roundToInt

/** Who a fanfare is for: the prestiging guild's members, or everyone else online. */
enum class FanfareAudience { MEMBERS, SERVER }

/** One vanilla sound at [tick] (20 per second) after the fanfare starts. */
data class FanfareCue(val tick: Int, val sound: String, val volume: Float, val pitch: Float)

/**
 * REQ-164: the prestige fanfare Fain picked on TEST (preview 8), built only from
 * vanilla sounds: three rising note-block bells (F, A, C) leading into the
 * challenge-complete chime, with a high F bell on the chime. Everything is pitched
 * 0.9 so the bells sit in the chime's key. Every tier hears the same fanfare;
 * everyone outside the guild hears it quietly.
 */
object PrestigeFanfareScore {
    /** The challenge chime's pitch; the bells are tuned to it. */
    private const val KEY = 0.9f
    private const val SERVER_VOLUME = 0.3f

    /** Note-block pitch for semitone [n] (0 to 24, F# to F#), shifted into [KEY]. */
    private fun np(n: Int): Float = (2.0.pow((n - 12) / 12.0) * KEY * 1000).roundToInt() / 1000f

    private const val BELL = "block.note_block.bell"

    private val MEMBERS = listOf(
        FanfareCue(0, BELL, .55f, np(11)),
        FanfareCue(3, BELL, .55f, np(15)),
        FanfareCue(6, BELL, .6f, np(18)),
        FanfareCue(9, "ui.toast.challenge_complete", 1f, KEY),
        FanfareCue(9, BELL, .5f, np(23)),
    )

    private val SERVER = MEMBERS.map { it.copy(volume = minOf(it.volume, SERVER_VOLUME)) }

    fun cues(tier: Int, audience: FanfareAudience): List<FanfareCue> {
        require(tier in 1..6) { "Prestige tier must be 1 to 6" }
        return if (audience == FanfareAudience.SERVER) SERVER else MEMBERS
    }
}
