package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.domain.values.BlockPosition
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.slf4j.LoggerFactory

/** One shared FIFO for quest and XP tracking, including multi-block piston moves.
 * A single drain avoids consuming every SQLite connection with competing writers.
 * Failure/overflow latches closed: unknown provenance must never earn natural-block rewards.
 */
class BlockProvenanceOperationQueue(
    private val executor: Executor,
    private val capacity: Int = 8192,
) {
    private val pending = ArrayDeque<() -> Unit>()
    private var running = false
    private var failure: Throwable? = null
    private val logger = LoggerFactory.getLogger(BlockProvenanceOperationQueue::class.java)

    init { require(capacity > 0) }

    @Suppress("UNUSED_PARAMETER")
    fun <T> submit(position: BlockPosition, operation: () -> T): CompletableFuture<T> = submit(operation)

    @Synchronized
    fun <T> submit(operation: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        if (failure != null) return result.also { it.completeExceptionally(checkNotNull(failure)) }
        if (pending.size >= capacity) {
            fail(RejectedExecutionException("Block provenance queue exceeded $capacity"))
            return result.also { it.completeExceptionally(checkNotNull(failure)) }
        }
        pending.addLast {
            val priorFailure = synchronized(this) { failure }
            if (priorFailure != null) {
                result.completeExceptionally(priorFailure)
            } else {
                try { result.complete(operation()) }
                catch (error: Throwable) {
                    synchronized(this) { fail(error) }
                    result.completeExceptionally(error)
                }
            }
        }
        if (!running) {
            running = true
            try { executor.execute(::drain) }
            catch (error: RejectedExecutionException) {
                fail(error)
                drain()
            }
        }
        return result
    }

    private fun fail(error: Throwable) {
        if (failure != null) return
        failure = error
        logger.error("Block provenance/quest tracking disabled until restart: database failure or queue overload. " +
            "No further natural-block rewards will be granted; investigate before restarting.", error)
    }

    private fun drain() {
        while (true) {
            val next = synchronized(this) {
                if (pending.isEmpty()) { running = false; null } else pending.removeFirst()
            } ?: return
            next()
        }
    }
}
