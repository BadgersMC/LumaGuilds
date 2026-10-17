package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.domain.values.BlockPosition
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor

class BlockProvenanceOperationQueueTest {
    @Test
    fun `different positions and piston batch share one nonblocking FIFO drain`() {
        val workers = mutableListOf<Runnable>()
        val queue = BlockProvenanceOperationQueue(Executor { workers.add(it) })
        val world = UUID.randomUUID()
        val calls = mutableListOf<String>()
        val place = queue.submit(BlockPosition(world, 1, 2, 3)) { calls.add("place") }
        val piston = queue.submit { calls.add("piston") }
        val read = queue.submit(BlockPosition(world, 2, 2, 3)) { calls.add("read") }
        assertEquals(1, workers.size)
        assertTrue(calls.isEmpty())
        assertFalse(place.isDone)
        workers.single().run()
        assertEquals(listOf("place", "piston", "read"), calls)
        assertTrue(piston.isDone && read.isDone)
    }

    @Test
    fun `failed mutation never permits a later natural block reward check`() {
        val workers = mutableListOf<Runnable>()
        val queue = BlockProvenanceOperationQueue(Executor { workers.add(it) })
        val failed = queue.submit<Unit> { error("database locked") }
        var queried = false
        val read = queue.submit { queried = true; false }
        workers.single().run()
        assertFailsWith<CompletionException> { failed.join() }
        assertFailsWith<CompletionException> { read.join() }
        assertFalse(queried)
        assertTrue(queue.submit { false }.isCompletedExceptionally)
    }

    @Test
    fun `backlog is bounded and overflow fails closed without running SQL on caller`() {
        val workers = mutableListOf<Runnable>()
        val queue = BlockProvenanceOperationQueue(Executor { workers.add(it) }, capacity = 2)
        var calls = 0
        val first = queue.submit { calls++ }
        val second = queue.submit { calls++ }
        val overflow = queue.submit { calls++ }
        assertTrue(overflow.isCompletedExceptionally)
        workers.single().run()
        assertEquals(0, calls)
        assertTrue(first.isCompletedExceptionally && second.isCompletedExceptionally)
    }

    @Test
    fun `executor shutdown rejects work without synchronous fallback SQL`() {
        val queue = BlockProvenanceOperationQueue(Executor { throw java.util.concurrent.RejectedExecutionException() })
        var called = false
        assertTrue(queue.submit { called = true }.isCompletedExceptionally)
        assertFalse(called)
    }

    @Test
    fun `replacement placement waits for prior break cleanup at same position`() {
        val executor = Executors.newFixedThreadPool(3)
        try {
            val queue = BlockProvenanceOperationQueue(executor)
            val position = BlockPosition(UUID.randomUUID(), 10, 64, 10)
            val provenancePresent = AtomicBoolean(true)
            val cleanupStarted = CountDownLatch(1)
            val allowCleanup = CountDownLatch(1)
            val replacementStarted = CountDownLatch(1)

            val cleanup = queue.submit(position) {
                cleanupStarted.countDown()
                check(allowCleanup.await(5, TimeUnit.SECONDS))
                provenancePresent.set(false)
            }
            assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS))

            val replacement = queue.submit(position) {
                replacementStarted.countDown()
                provenancePresent.set(true)
            }

            assertFalse(replacementStarted.await(150, TimeUnit.MILLISECONDS))
            allowCleanup.countDown()

            cleanup.join()
            replacement.join()
            assertTrue(replacementStarted.await(5, TimeUnit.SECONDS))
            assertTrue(provenancePresent.get())
        } finally {
            executor.shutdownNow()
        }
    }
}
