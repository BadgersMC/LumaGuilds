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

class BlockProvenanceOperationQueueTest {
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
