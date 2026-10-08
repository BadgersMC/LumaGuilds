package net.lumalyte.lg.application.services

import java.util.UUID

/** Teleport suggestions share the same home access decision as the teleport operation. */
internal fun GuildService.accessibleHomeNames(playerId: UUID, guildId: UUID): List<String> =
    getHomes(guildId).homeNames.filter { canUseHome(playerId, guildId, it) }
