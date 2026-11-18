package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.application.persistence.BlockProvenanceRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.LeaderboardService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PlaytimeActivityService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.infrastructure.listeners.BlockProvenanceOperationQueue
import net.lumalyte.lg.infrastructure.listeners.ProgressionEventListener
import net.lumalyte.lg.infrastructure.services.AsyncTaskService
import org.bukkit.plugin.Plugin

/**
 * Lazy listener binding. Progression, leaderboard and provenance services have
 * not yet all been mapped into Nexus, so the listener must not be constructed
 * until the complete graph is ready and Bukkit listener registration begins.
 */
internal fun NexusContext.registerProgressionListenerBindings() {
    registerBean("progressionEventListener", ProgressionEventListener::class) {
        ProgressionEventListener(
            progressionService = getBean<ProgressionService>(),
            memberService = getBean<MemberService>(),
            memberRepository = getBean<MemberRepository>(),
            configService = getBean<ConfigService>(),
            asyncTaskService = getBean<AsyncTaskService>(),
            leaderboardService = getBean<LeaderboardService>(),
            playtimeActivityService = getBean<PlaytimeActivityService>(),
            blockProvenanceRepository = getBean<BlockProvenanceRepository>(),
            plugin = getBean<Plugin>(),
            virtualDispatcher = requireNotNull(dispatchers).virtualThread,
            taskScope = requireNotNull(scope) { "Nexus coroutine scope unavailable" },
            provenanceOperations = getBean<BlockProvenanceOperationQueue>(),
        )
    }
}
