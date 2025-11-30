package net.lumalyte.lg.infrastructure.services

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import net.lumalyte.lg.application.services.CacheStats
import net.lumalyte.lg.application.services.FormCacheService
import org.geysermc.cumulus.form.Form
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.time.Duration.Companion.minutes

/**
 * Implementation of FormCacheService using Guava Cache
 * Provides efficient caching with size limits, expiration, and async building
 */
class FormCacheServiceGuava(
    private val maxCacheSize: Int = 100,
    private val cacheExpirationMinutes: Int = 30,
    private val logger: Logger
) : FormCacheService {

    // Async executor for form building
    private val asyncExecutor: Executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "BedrockFormBuilder").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }

    // Guava cache for form storage
    private val formCache: Cache<String, Form> = CacheBuilder.newBuilder()
        .maximumSize(maxCacheSize.toLong())
        .expireAfterWrite(cacheExpirationMinutes.toLong(), TimeUnit.MINUTES)
        .removalListener<String, Form> { notification ->
            logger.fine("Form evicted from cache: ${notification.key}, cause: ${notification.cause}")
        }
        .recordStats()
        .build()

    override fun getOrBuildForm(cacheKey: String, formBuilder: () -> Form): Form {
        return try {
            formCache.get(cacheKey) {
                logger.fine("Building and caching form: $cacheKey")
                formBuilder()
            }
        } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
            logger.warning("Error building form '$cacheKey', building without cache: ${e.message}")
            formBuilder()
        }
    }

    override fun buildFormAsync(formBuilder: () -> Form): CompletableFuture<Form> {
        return CompletableFuture.supplyAsync({
            try {
                formBuilder()
            } catch (e: Exception) {
            // Service operation - catching all exceptions to prevent service failure
                logger.warning("Error building form asynchronously: ${e.message}")
                throw e
            }
        }, asyncExecutor)
    }

    override fun cacheForm(cacheKey: String, form: Form) {
        formCache.put(cacheKey, form)
        logger.fine("Manually cached form: $cacheKey")
    }

    override fun isCached(cacheKey: String): Boolean {
        return formCache.getIfPresent(cacheKey) != null
    }

    override fun clearCache() {
        val size = formCache.size()
        formCache.invalidateAll()
        logger.info("Cleared form cache ($size entries)")
    }

    override fun getCacheStats(): CacheStats {
        val stats = formCache.stats()
        return CacheStats(
            cacheSize = formCache.size().toInt(),
            maxSize = maxCacheSize,
            hitCount = stats.hitCount(),
            missCount = stats.missCount(),
            hitRate = stats.hitRate(),
            evictions = stats.evictionCount()
        )
    }

    override fun invalidateForm(cacheKey: String) {
        formCache.invalidate(cacheKey)
        logger.fine("Invalidated cached form: $cacheKey")
    }

    override fun getOrBuildFormAsync(cacheKey: String, formBuilder: () -> Form): CompletableFuture<Form> {
        // First check if we have it cached
        val cachedForm = formCache.getIfPresent(cacheKey)
        if (cachedForm != null) {
            return CompletableFuture.completedFuture(cachedForm)
        }

        // Build asynchronously and cache the result
        return buildFormAsync(formBuilder).thenApply { form ->
            formCache.put(cacheKey, form)
            logger.fine("Built and cached form asynchronously: $cacheKey")
            form
        }
    }

    /**
     * Creates a cache key for a specific form type and parameters
     */
    fun createCacheKey(formType: String, vararg parameters: Any?): String {
        val paramString = parameters.joinToString("|") { it?.toString() ?: "null" }
        return "$formType:$paramString"
    }

    /**
     * Gets cache statistics as a formatted string
     */
    fun getFormattedStats(): String {
        val stats = getCacheStats()
        return """
            Form Cache Statistics:
            Size: ${stats.cacheSize}/${stats.maxSize}
            Hit Rate: ${String.format("%.2f", stats.hitRate * 100)}%
            Hits: ${stats.hitCount}
            Misses: ${stats.missCount}
            Evictions: ${stats.evictions}
        """.trimIndent()
    }
}
