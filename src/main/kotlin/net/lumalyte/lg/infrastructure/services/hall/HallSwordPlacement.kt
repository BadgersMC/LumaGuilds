package net.lumalyte.lg.infrastructure.services.hall

/**
 * Where the Guard pose's vanilla netherite sword stands, in blocks from the statue's feet (the
 * prestige model's units divided by 16). The sword icon runs corner to corner; in an item display's
 * own space (transform NONE) its tip points up and to the -x side, so a 3/8 turn forwards about the
 * view axis points the tip straight down. (A 3/8 turn backwards laid it flat: seen in-game.)
 */
internal object HallSwordPlacement {
    /** Hands meet 6.1/16 of a block in front of the statue in the Guard pose. */
    const val FORWARD = 6.1 / 16

    /** Pommel at about 0.96 blocks and tip on the plinth, so the icon's centre sits halfway. */
    const val CENTER_HEIGHT = 0.48

    /** The icon's diagonal is about 19.8 of its 16 pixels; this makes the sword about 0.96 blocks. */
    const val SCALE = 0.78f

    const val TURN = (3.0 * Math.PI / 4).toFloat()

    /** World offset (x, z) of the sword's centre for a statue at yaw [yawDegrees] (Minecraft yaw). */
    fun offset(yawDegrees: Float): Pair<Double, Double> {
        val radians = Math.toRadians(yawDegrees.toDouble())
        return (-Math.sin(radians) * FORWARD) to (Math.cos(radians) * FORWARD)
    }
}
