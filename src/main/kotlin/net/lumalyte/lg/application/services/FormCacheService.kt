package net.lumalyte.lg.application.services

import org.geysermc.cumulus.form.Form
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Service for caching and asynchronously building Bedrock forms
 * Provides performance optimizations for frequently used and complex forms
 */
interface FormCacheService {

    /**
     * Gets a cached form or builds it if not cached
     */
    fun getOrBuildForm(cacheKey: String, formBuilder: () -> Form): Form

    /**
     * Builds a form asynchronously
     */
    fun buildFormAsync(formBuilder: () -> Form): CompletableFuture<Form>

    /**
     * Caches a form with the given key
     */
    fun cacheForm(cacheKey: String, form: Form)

    /**
     * Checks if a form is cached
     */
    fun isCached(cacheKey: String): Boolean

    /**
     * Clears all cached forms
     */
    fun clearCache()

    /**
     * Gets cache statistics
     */
    fun getCacheStats(): CacheStats

    /**
     * Invalidates a specific cached form
     */
    fun invalidateForm(cacheKey: String)

    /**
     * Gets or builds a form asynchronously with caching
     */
    fun getOrBuildFormAsync(cacheKey: String, formBuilder: () -> Form): CompletableFuture<Form>
}

data class CacheStats(
    val cacheSize: Int,
    val maxSize: Int,
    val hitCount: Long,
    val missCount: Long,
    val hitRate: Double,
    val evictions: Long
)
