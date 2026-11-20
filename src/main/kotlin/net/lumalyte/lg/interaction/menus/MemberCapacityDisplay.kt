package net.lumalyte.lg.interaction.menus

import java.util.UUID

/**
 * REQ-190: the member cap pages show is the member service's limit (level,
 * prestige and protected floor), not the flat number in the reward snapshot.
 */
internal fun memberCapacityShown(guildId: UUID, fallback: Int): Int =
    runCatching {
        org.koin.core.context.GlobalContext.getOrNull()
            ?.getOrNull<net.lumalyte.lg.application.services.MemberService>()
            ?.getMemberLimit(guildId)
    }.getOrNull() ?: fallback
