package net.lumalyte.lg.domain.entities

import java.util.UUID

/** Public directory facts; an empty owner set is explicitly unknown. */
class GuildDirectoryDetails(
    /** Priority-zero owner identities; empty means unknown. */
    val owners: List<UUID> = emptyList(),
    /** Names of active, unexpired allied guilds. */
    val allies: List<String> = emptyList(),
)
