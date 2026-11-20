package net.lumalyte.lg.application.persistence

import java.util.UUID

/** REQ-190: member counts recorded once when level-based capacity shipped. */
interface MemberCapacityFloors {
    /** The guild's protected floor, or null for guilds created after the snapshot. */
    fun floor(guildId: UUID): Int?

    /** Records every existing guild's member count the first time only; returns rows written. */
    fun snapshotOnce(): Int
}
