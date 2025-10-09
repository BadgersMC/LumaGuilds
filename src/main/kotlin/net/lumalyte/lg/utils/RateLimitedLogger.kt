package net.lumalyte.lg.utils

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Rate-limited logger that prevents log spam from repeated identical messages.
 * Useful for preventing console spam from frequent errors or repeated operations.
 */
class RateLimitedLogger(
    private val logger: Logger,
    private val defaultCooldown: Duration = Duration.ofMinutes(5)
) {

    // Thread-safe storage for rate limiting
    private val lastLogTimes = ConcurrentHashMap<String, Instant>()
    private val messageCounts = ConcurrentHashMap<String, Int>()

    /**
     * Logs a message with rate limiting. Only logs if enough time has passed since the last identical message.
     *
     * @param level The logging level
     * @param key Unique identifier for this type of message (used for rate limiting)
     * @param message The message to log
     * @param cooldown Custom cooldown duration (defaults to 5 minutes)
     * @return true if the message was logged, false if it was rate limited
     */
    fun log(level: Level, key: String, message: String, cooldown: Duration = defaultCooldown): Boolean {
        val now = Instant.now()
        val lastLogTime = lastLogTimes[key]

        if (lastLogTime != null && Duration.between(lastLogTime, now) < cooldown) {
            // Increment suppressed count
            messageCounts.merge(key, 1) { old, _ -> old + 1 }
            return false
        }

        // Check if we have suppressed messages to report
        val suppressedCount = messageCounts[key] ?: 0
        val finalMessage = if (suppressedCount > 0) {
            "$message (suppressed $suppressedCount similar messages)"
        } else {
            message
        }

        // Log the message
        logger.log(level, finalMessage)

        // Update tracking
        lastLogTimes[key] = now
        messageCounts[key] = 0

        return true
    }

    /**
     * Logs a message with an associated throwable, with rate limiting.
     *
     * @param level The logging level
     * @param key Unique identifier for this type of message
     * @param message The message to log
     * @param throwable The throwable to log
     * @param cooldown Custom cooldown duration
     * @return true if the message was logged, false if it was rate limited
     */
    fun log(level: Level, key: String, message: String, throwable: Throwable, cooldown: Duration = defaultCooldown): Boolean {
        val now = Instant.now()
        val lastLogTime = lastLogTimes[key]

        if (lastLogTime != null && Duration.between(lastLogTime, now) < cooldown) {
            messageCounts.merge(key, 1) { old, _ -> old + 1 }
            return false
        }

        val suppressedCount = messageCounts[key] ?: 0
        val finalMessage = if (suppressedCount > 0) {
            "$message (suppressed $suppressedCount similar messages)"
        } else {
            message
        }

        logger.log(level, finalMessage, throwable)

        lastLogTimes[key] = now
        messageCounts[key] = 0

        return true
    }

    /**
     * Forces logging of a message regardless of rate limits.
     * Useful for critical messages that should always be logged.
     *
     * @param level The logging level
     * @param message The message to log
     * @param throwable Optional throwable to log
     */
    fun forceLog(level: Level, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.log(level, message, throwable)
        } else {
            logger.log(level, message)
        }
    }

    /**
     * Clears all rate limiting state. Useful for testing or manual resets.
     */
    fun clear() {
        lastLogTimes.clear()
        messageCounts.clear()
    }

    /**
     * Gets statistics about rate limiting for monitoring/debugging.
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "tracked_keys" to lastLogTimes.size,
            "total_suppressed" to messageCounts.values.sum(),
            "suppressed_by_key" to messageCounts.toMap()
        )
    }

    companion object {
        // Common cooldown durations
        val AGGRESSIVE_COOLDOWN = Duration.ofSeconds(30)  // For very frequent errors
        val NORMAL_COOLDOWN = Duration.ofMinutes(5)       // Default
        val RELAXED_COOLDOWN = Duration.ofMinutes(15)     // For less frequent issues
    }
}
