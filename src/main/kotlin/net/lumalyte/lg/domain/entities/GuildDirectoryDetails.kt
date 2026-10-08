package net.lumalyte.lg.domain.entities

import java.util.UUID

/** Public directory facts; an empty owner set is explicitly unknown. */
interface GuildDirectoryDetails {
    /** Priority-zero owner identities; empty means unknown. */
    val owners: List<UUID>

    /** Names of active, unexpired allied guilds. */
    val allies: List<String>
}

/** Creates immutable public directory facts without exposing their data implementation. */
fun GuildDirectoryDetails(
    owners: List<UUID> = emptyList(),
    allies: List<String> = emptyList(),
): GuildDirectoryDetails = DirectoryFacts(owners.toList(), allies.toList())

private data class DirectoryFacts(
    override val owners: List<UUID>,
    override val allies: List<String>,
) : GuildDirectoryDetails
