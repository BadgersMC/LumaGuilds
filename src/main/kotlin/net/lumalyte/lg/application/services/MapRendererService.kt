package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Service interface for rendering charts and graphs onto Minecraft maps for guild statistics visualization.
 */
interface MapRendererService {

    /**
     * Renders a comprehensive overview chart showing multiple guild metrics on a single map.
     *
     * @param guild The guild to render statistics for
     * @param player The player requesting the chart
     * @return ItemStack containing the rendered map, or null if rendering failed
     */
    fun renderGuildOverviewChart(guild: Guild, player: Player): ItemStack?

    /**
     * Renders a line chart showing trends over time for specified metrics.
     *
     * @param guild The guild to render statistics for
     * @param metric The metric to display (e.g., "kills", "balance", "wars")
     * @param timeRange The time range in days (default: 30)
     * @param player The player requesting the chart
     * @return ItemStack containing the rendered map, or null if rendering failed
     */
    fun renderTrendChart(guild: Guild, metric: String, timeRange: Int = 30, player: Player): ItemStack?

    /**
     * Renders a bar chart showing comparative data between guild members.
     *
     * @param guild The guild to render statistics for
     * @param dataType The type of data to compare (e.g., "contributions", "kills", "activity")
     * @param player The player requesting the chart
     * @return ItemStack containing the rendered map, or null if rendering failed
     */
    fun renderComparisonChart(guild: Guild, dataType: String, player: Player): ItemStack?

    /**
     * Renders a pie chart showing proportional data distribution.
     *
     * @param guild The guild to render statistics for
     * @param category The category to display proportions for (e.g., "kill_types", "member_roles")
     * @param player The player requesting the chart
     * @return ItemStack containing the rendered map, or null if rendering failed
     */
    fun renderProportionChart(guild: Guild, category: String, player: Player): ItemStack?

    /**
     * Renders a custom chart based on provided data points.
     *
     * @param title The title to display on the chart
     * @param dataPoints List of data points as pairs of (label, value)
     * @param chartType The type of chart to render
     * @param player The player requesting the chart
     * @return ItemStack containing the rendered map, or null if rendering failed
     */
    fun renderCustomChart(title: String, dataPoints: List<Pair<String, Number>>, chartType: ChartType, player: Player): ItemStack?

    /**
     * Clears the cache for a specific guild's charts.
     *
     * @param guildId The ID of the guild to clear cache for
     */
    fun clearGuildCache(guildId: UUID)

    /**
     * Clears all cached charts.
     */
    fun clearAllCache()

    /**
     * Gets cache statistics for monitoring and debugging.
     *
     * @return Map containing cache statistics
     */
    fun getCacheStatistics(): Map<String, Any>

    /**
     * Checks if the rendering service is available and properly configured.
     *
     * @return true if the service is ready to render charts
     */
    fun isAvailable(): Boolean
}

/**
 * Enumeration of supported chart types for rendering.
 */
enum class ChartType {
    LINE,
    BAR,
    PIE,
    AREA,
    SCATTER
}

/**
 * Configuration class for chart rendering settings.
 */
data class ChartConfig(
    val width: Int = 128,
    val height: Int = 128,
    val backgroundColor: Int = 0xFFFFFF, // White background
    val gridColor: Int = 0xCCCCCC,       // Light gray grid
    val textColor: Int = 0x000000,       // Black text
    val accentColor: Int = 0x0066CC,     // Blue accent
    val positiveColor: Int = 0x00AA00,   // Green for positive
    val negativeColor: Int = 0xCC0000,   // Red for negative
    val neutralColor: Int = 0x666666     // Gray for neutral
)
