package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.WarNotificationKind

/**
 * One sound in a moment: a vanilla sound key, played [delayTicks] after the moment starts and,
 * when [stopAfterTicks] is set, cut that many ticks after it starts. Minecraft can't fade, so a stop is a
 * hard cut that also silences the player's own copies of that sound: guild moments never set it (REQ-280).
 */
data class SoundCue(val sound: String, val volume: Float, val pitch: Float, val delayTicks: Long = 0, val stopAfterTicks: Long? = null)

/**
 * REQ-233 to REQ-235: war and diplomacy moments from the approved war notification mockup.
 * Cues follow war-redesign.js; its samples are vanilla sounds (identified by matching the audio):
 * bell = block.bell.use, impact = entity.wither.spawn, triumph = ui.toast.challenge_complete,
 * reward = two harp notes a fifth apart (D, then A 190 ms later), chime = block.amethyst_block.chime,
 * crit = entity.player.attack.crit, levelup = entity.player.levelup. Browser volumes are scaled by 1.5
 * so the loudest cue plays near full volume in game.
 */
enum class GuildMoment(val cues: List<SoundCue>) {
    /** Two bells (.51 @.85, .42 @.8 after 200 ms) over a low bass note; every cue rings out. */
    DECLARED(WAR_CALL),

    /** The mockup reuses the declaration cue when the war starts. */
    BEGINS(WAR_CALL),

    /** triumph .65, reward .46, chime .24 @1.15 after 1050 ms; every cue rings out. */
    VICTORY(listOf(
        SoundCue("ui.toast.challenge_complete", 0.98f, 1.0f, 0),
        SoundCue("block.note_block.harp", 0.69f, HARP_D, 0),
        SoundCue("block.note_block.harp", 0.62f, HARP_A, 4),
        SoundCue("block.amethyst_block.chime", 0.36f, 1.15f, 21),
    )),

    /** A low bell over a bass note, then a lower bell 400 ms later, tolling out. */
    DEFEAT(listOf(
        SoundCue("block.bell.use", 0.53f, 0.7f, 0),
        SoundCue("block.note_block.bass", 0.6f, 0.5f, 0),
        SoundCue("block.bell.use", 0.42f, 0.6f, 8),
    )),

    /** reward .38 @.85, chime .18 @.95 after 650 ms. */
    PEACE(listOf(
        SoundCue("block.note_block.harp", 0.57f, HARP_D * 0.85f, 0),
        SoundCue("block.note_block.harp", 0.51f, HARP_A * 0.85f, 4),
        SoundCue("block.amethyst_block.chime", 0.27f, 0.95f, 13),
    )),

    /** Not in the mockup: the peace cue raised a little, so it reads as good news. */
    ALLIANCE(listOf(
        SoundCue("block.note_block.harp", 0.6f, HARP_D * 1.12f, 0),
        SoundCue("block.note_block.harp", 0.55f, HARP_A * 1.12f, 4),
        SoundCue("block.amethyst_block.chime", 0.3f, 1.3f, 8),
    )),

    /** "slower crit + faster level up": crit .48 @.75, level-up .48 @1.9 after 130 ms. */
    KILL(listOf(
        SoundCue("entity.player.attack.crit", 0.72f, 0.75f, 0),
        SoundCue("entity.player.levelup", 0.72f, 1.9f, 3),
    ));

    companion object {
        fun of(kind: WarNotificationKind): GuildMoment = when (kind) {
            WarNotificationKind.DECLARATION_RECEIVED, WarNotificationKind.DECLARATION_SENT -> DECLARED
            WarNotificationKind.WAR_ACCEPTED -> BEGINS
            WarNotificationKind.VICTORY -> VICTORY
            WarNotificationKind.DEFEAT -> DEFEAT
        }

        /** The declaring guild's name first, then the defending guild's. */
        fun declaringFirst(kind: WarNotificationKind, own: String, opponent: String): Pair<String, String> =
            if (kind == WarNotificationKind.DECLARATION_RECEIVED) opponent to own else own to opponent
    }
}

/** Note-block pitches for D and A (the harp sample is F#). */
private const val HARP_D = 0.7937f
private const val HARP_A = 1.1892f

/**
 * REQ-280: no cue is cut short. Minecraft can't fade a playing sound, so the mockup's fades were hard
 * cuts in game (and stopping a sound by name also cut the player's own crits and level-ups). The
 * wither-spawn impact was a long low roar that read as background noise under the bells; a low
 * bass note gives the weight instead.
 */
private val WAR_CALL = listOf(
    SoundCue("block.bell.use", 0.77f, 0.85f, 0),
    SoundCue("block.note_block.bass", 0.6f, 0.5f, 0),
    SoundCue("block.bell.use", 0.63f, 0.8f, 4),
)
