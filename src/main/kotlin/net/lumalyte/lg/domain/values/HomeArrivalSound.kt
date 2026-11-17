package net.lumalyte.lg.domain.values

/**
 * REQ-281: the "Quiet portal" cue from the Enthusia message studio (Soft arrival, new alternative):
 * a small teleport shimmer, then a warm harp landing note a quarter second later. Played to a
 * player when a guild-home or allied-home teleport lands.
 */
object HomeArrivalSound {
    val cues: List<SoundCue> = listOf(
        SoundCue("entity.enderman.teleport", 0.13f, 1.3f, 0),
        SoundCue("block.note_block.harp", 0.23f, 1.1f, 5),
    )
}
