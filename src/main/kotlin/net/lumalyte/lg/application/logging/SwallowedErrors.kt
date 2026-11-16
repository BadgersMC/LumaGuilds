package net.lumalyte.lg.application.logging

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Audit 2026-11-16: errors that a call site turns into a safe fallback (null, Unavailable, 0) are
 * still logged, once a minute per site with a count of the ones skipped, so they show in latest.log.
 */
object SwallowedErrors {
    private const val INTERVAL_MS = 60_000L
    private val logger = LoggerFactory.getLogger("LumaGuilds")
    private val lastLogged = ConcurrentHashMap<String, Long>()
    private val skipped = ConcurrentHashMap<String, AtomicInteger>()

    fun log(site: String, error: Throwable, now: Long = System.currentTimeMillis()) {
        val last = lastLogged[site]
        if (last != null && now - last < INTERVAL_MS) {
            skipped.computeIfAbsent(site) { AtomicInteger() }.incrementAndGet()
            return
        }
        lastLogged[site] = now
        val more = skipped.remove(site)?.get() ?: 0
        val suffix = if (more > 0) " ($more more since the last report)" else ""
        logger.warn("Handled error at $site$suffix: ${error.javaClass.simpleName}: ${error.message}", error)
    }
}
