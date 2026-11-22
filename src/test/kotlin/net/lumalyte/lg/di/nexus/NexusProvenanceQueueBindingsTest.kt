package net.lumalyte.lg.di.nexus

import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.infrastructure.listeners.BlockProvenanceOperationQueue
import org.junit.jupiter.api.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NexusProvenanceQueueBindingsTest {
    @Test
    fun `provenance queue runs FIFO operations on a Nexus owned virtual thread`() {
        val context = NexusContext.create(classLoader = javaClass.classLoader)
        try {
            context.registerProgressionListenerBindings()
            val queue = context.getBean<BlockProvenanceOperationQueue>()
            assertSame(queue, context.getBean<BlockProvenanceOperationQueue>())
            assertTrue(requireNotNull(context.scope).isActive)
            val workerIsVirtual = queue.submit { Thread.currentThread().isVirtual }
            assertEquals(true, workerIsVirtual.get(5, TimeUnit.SECONDS))
        } finally {
            context.close()
        }
    }

    @Test
    fun `provenance queue rejects new work after Nexus scope cancellation`() {
        val context = NexusContext.create(classLoader = javaClass.classLoader)
        try {
            context.registerProgressionListenerBindings()
            val queue = context.getBean<BlockProvenanceOperationQueue>()
            requireNotNull(context.scope).cancel()
            val failure = assertFailsWith<ExecutionException> {
                queue.submit { "must not run" }.get(5, TimeUnit.SECONDS)
            }
            assertTrue(failure.cause is RejectedExecutionException)
        } finally {
            context.close()
        }
    }
}
