package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.events.GuildPrestigeEvent
import net.lumalyte.lg.application.services.GuildPrestigeNotifier
import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import org.bukkit.Bukkit

/** Publishes [GuildPrestigeEvent]; confirmation already runs on the server thread. */
class GuildPrestigeEventBukkit(
    private val call: (GuildPrestigeEvent) -> Unit = { Bukkit.getPluginManager().callEvent(it) },
) : GuildPrestigeNotifier {
    override fun prestiged(completion: PrestigeCompletion) = call(
        GuildPrestigeEvent(
            completion.guildId,
            completion.actorId,
            completion.tier,
            completion.retainedRewardId,
            completion.appliedAt,
            completion.firstToReachTier,
        )
    )
}
