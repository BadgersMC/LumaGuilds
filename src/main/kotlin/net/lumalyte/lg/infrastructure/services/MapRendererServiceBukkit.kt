package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ChartConfig
import net.lumalyte.lg.application.services.ChartType
import net.lumalyte.lg.application.services.MapRendererService
import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.map.MapView
import org.koin.core.component.KoinComponent
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Bukkit implementation of the MapRendererService for rendering charts on Minecraft maps.
 */
class MapRendererServiceBukkit(
    private val config: ChartConfig = ChartConfig()
) : MapRendererService, KoinComponent {

    private val logger = LoggerFactory.getLogger(MapRendererServiceBukkit::class.java)
    private val chartCache = ConcurrentHashMap<String, CachedChart>()

    // TODO: Inject these services when they're implemented
    // private val killService: KillService by inject()
    // private val warService: WarService by inject()
    // private val bankService: BankService by inject()
    // private val memberService: MemberService by inject()

    data class CachedChart(
        val mapView: MapView,
        val timestamp: Long,
        val ttl: Long = 5 * 60 * 1000 // 5 minutes default TTL
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
    }

    override fun renderGuildOverviewChart(guild: Guild, player: Player): ItemStack? {
        logger.info("Rendering overview chart for guild: ${guild.name}")

        return try {
            // Create a new map item
            val mapItem = ItemStack(Material.FILLED_MAP)
            val mapMeta = mapItem.itemMeta as? MapMeta ?: return null

            // TODO: Create and configure the map view with chart renderer
            // For now, return a basic map that will be enhanced in future tasks
            val mapView = Bukkit.createMap(player.world)
            mapMeta.mapView = mapView

            mapItem.itemMeta = mapMeta

            // Cache the rendered chart
            val cacheKey = "overview_${guild.id}"
            chartCache[cacheKey] = CachedChart(mapView, System.currentTimeMillis())

            logger.info("Successfully rendered overview chart for guild: ${guild.name}")
            mapItem

        } catch (e: Exception) {
            logger.error("Failed to render overview chart for guild ${guild.name}", e)
            null
        }
    }

    override fun renderTrendChart(guild: Guild, metric: String, timeRange: Int, player: Player): ItemStack? {
        logger.info("Rendering trend chart for guild: ${guild.name}, metric: $metric, timeRange: $timeRange")

        return try {
            val mapItem = ItemStack(Material.FILLED_MAP)
            val mapMeta = mapItem.itemMeta as? MapMeta ?: return null

            // TODO: Implement trend chart rendering with line chart renderer
            val mapView = Bukkit.createMap(player.world)
            mapMeta.mapView = mapView

            mapItem.itemMeta = mapMeta

            // Cache the rendered chart
            val cacheKey = "trend_${guild.id}_${metric}_${timeRange}"
            chartCache[cacheKey] = CachedChart(mapView, System.currentTimeMillis())

            logger.info("Successfully rendered trend chart for guild: ${guild.name}")
            mapItem

        } catch (e: Exception) {
            logger.error("Failed to render trend chart for guild ${guild.name}, metric $metric", e)
            null
        }
    }

    override fun renderComparisonChart(guild: Guild, dataType: String, player: Player): ItemStack? {
        logger.info("Rendering comparison chart for guild: ${guild.name}, dataType: $dataType")

        return try {
            val mapItem = ItemStack(Material.FILLED_MAP)
            val mapMeta = mapItem.itemMeta as? MapMeta ?: return null

            // TODO: Implement comparison chart rendering with bar chart renderer
            val mapView = Bukkit.createMap(player.world)
            mapMeta.mapView = mapView

            mapItem.itemMeta = mapMeta

            // Cache the rendered chart
            val cacheKey = "comparison_${guild.id}_${dataType}"
            chartCache[cacheKey] = CachedChart(mapView, System.currentTimeMillis())

            logger.info("Successfully rendered comparison chart for guild: ${guild.name}")
            mapItem

        } catch (e: Exception) {
            logger.error("Failed to render comparison chart for guild ${guild.name}, dataType $dataType", e)
            null
        }
    }

    override fun renderProportionChart(guild: Guild, category: String, player: Player): ItemStack? {
        logger.info("Rendering proportion chart for guild: ${guild.name}, category: $category")

        return try {
            val mapItem = ItemStack(Material.FILLED_MAP)
            val mapMeta = mapItem.itemMeta as? MapMeta ?: return null

            // TODO: Implement proportion chart rendering with pie chart renderer
            val mapView = Bukkit.createMap(player.world)
            mapMeta.mapView = mapView

            mapItem.itemMeta = mapMeta

            // Cache the rendered chart
            val cacheKey = "proportion_${guild.id}_${category}"
            chartCache[cacheKey] = CachedChart(mapView, System.currentTimeMillis())

            logger.info("Successfully rendered proportion chart for guild: ${guild.name}")
            mapItem

        } catch (e: Exception) {
            logger.error("Failed to render proportion chart for guild ${guild.name}, category $category", e)
            null
        }
    }

    override fun renderCustomChart(title: String, dataPoints: List<Pair<String, Number>>, chartType: ChartType, player: Player): ItemStack? {
        logger.info("Rendering custom chart: $title, type: $chartType, data points: ${dataPoints.size}")

        return try {
            val mapItem = ItemStack(Material.FILLED_MAP)
            val mapMeta = mapItem.itemMeta as? MapMeta ?: return null

            // Create map view
            val mapView = Bukkit.createMap(player.world)
            mapMeta.mapView = mapView

            mapItem.itemMeta = mapMeta

            // Create canvas and appropriate chart renderer
            val canvas = MapCanvas(mapView)
            val chartData = dataPoints.map { (label, value) ->
                ChartRenderer.DataPoint(label, value)
            }

            val chartBounds = ChartRenderer.ChartBounds(5, 15, 118, 90) // Leave space for title
            val metadata = ChartRenderer.ChartMetadata(
                title = title,
                showLegend = true,
                showGrid = true
            )

            // Render the appropriate chart type
            when (chartType) {
                ChartType.LINE -> {
                    val renderer = LineChartRenderer(canvas, config)
                    renderer.render(chartData, chartBounds, metadata)
                }
                ChartType.BAR -> {
                    val renderer = BarChartRenderer(canvas, config)
                    renderer.render(chartData, chartBounds, metadata)
                }
                else -> {
                    // For unsupported chart types, render a simple line chart as fallback
                    val renderer = LineChartRenderer(canvas, config)
                    renderer.render(chartData, chartBounds, metadata)
                }
            }

            // Cache the rendered chart
            val cacheKey = "custom_${title.hashCode()}_${chartType}_${System.currentTimeMillis()}"
            chartCache[cacheKey] = CachedChart(mapView, System.currentTimeMillis())

            logger.info("Successfully rendered custom chart: $title")
            mapItem

        } catch (e: Exception) {
            logger.error("Failed to render custom chart: $title", e)
            null
        }
    }

    override fun clearGuildCache(guildId: UUID) {
        logger.info("Clearing cache for guild: $guildId")

        val keysToRemove = chartCache.keys.filter { key ->
            key.contains(guildId.toString())
        }

        keysToRemove.forEach { key ->
            chartCache.remove(key)
            logger.debug("Removed cached chart: $key")
        }

        logger.info("Cleared ${keysToRemove.size} cached charts for guild: $guildId")
    }

    override fun clearAllCache() {
        logger.info("Clearing all chart cache")

        val sizeBefore = chartCache.size
        chartCache.clear()

        logger.info("Cleared $sizeBefore cached charts")
    }

    override fun getCacheStatistics(): Map<String, Any> {
        val totalCharts = chartCache.size
        val expiredCharts = chartCache.values.count { it.isExpired() }
        val activeCharts = totalCharts - expiredCharts

        // Clean up expired charts periodically
        if (expiredCharts > 0) {
            val expiredKeys = chartCache.filter { it.value.isExpired() }.keys
            expiredKeys.forEach { chartCache.remove(it) }
            logger.debug("Cleaned up $expiredCharts expired charts")
        }

        return mapOf(
            "total_cached_charts" to totalCharts,
            "active_charts" to activeCharts,
            "expired_charts" to expiredCharts,
            "cache_memory_mb" to (chartCache.size * 0.1), // Rough estimate
            "config_width" to config.width,
            "config_height" to config.height
        )
    }

    override fun isAvailable(): Boolean {
        // TODO: Add more comprehensive availability checks
        // For now, just check if we can create maps
        return try {
            // This is a basic check - in a real implementation we'd check
            // if the server supports maps and we have necessary permissions
            true
        } catch (e: Exception) {
            logger.error("MapRendererService availability check failed", e)
            false
        }
    }

    /**
     * Gets a cached chart if it exists and hasn't expired.
     *
     * @param cacheKey The cache key to look up
     * @return The cached chart, or null if not found or expired
     */
    private fun getCachedChart(cacheKey: String): CachedChart? {
        val cached = chartCache[cacheKey]
        return if (cached?.isExpired() == false) {
            cached
        } else {
            // Remove expired chart
            if (cached != null) {
                chartCache.remove(cacheKey)
            }
            null
        }
    }

    /**
     * Background task to periodically clean up expired charts.
     * This should be called during plugin initialization.
     */
    fun startCacheCleanupTask() {
        // TODO: Implement scheduled cleanup task using BukkitRunnable
        logger.info("Cache cleanup task started")
    }

    /**
     * Stops the cache cleanup task.
     * This should be called during plugin shutdown.
     */
    fun stopCacheCleanupTask() {
        // TODO: Cancel the cleanup task
        logger.info("Cache cleanup task stopped")
    }
}
