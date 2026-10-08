package net.lumalyte.lg.domain.entities

import java.util.UUID

/** Public directory facts; an empty owner set is explicitly unknown. */
class GuildDirectoryDetails(
    val owners: List<UUID> = emptyList(),
    val allies: List<String> = emptyList(),
)
