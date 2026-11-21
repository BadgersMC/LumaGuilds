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
 * Lazy shared provenance FIFO and progression listener bindings. The queue borrows
 * Nexus's virtual-thread executor and rejects submissions after Nexus scope cancellation.
 * Listener construction and Bukkit registration remain deferred until the final cutover.
 */
internal fun NexusContext.registerProgressionListenerBindings() {
    // One shared FIFO using Nexus's owned virtual-thread pool; no second executor.
    registerBean("blockProvenanceOperationQueue", BlockProvenanceOperationQueue::class) {
        BlockProvenanceOperationQueue(nexusVirtualExecutor())
    }

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
            lunarClientService = { if (containsBean("lunarClientService")) getBean<net.lumalyte.lg.application.services.apollo.LunarClientService>() else null },
        )
    }
}
