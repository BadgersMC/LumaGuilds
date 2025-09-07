package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ChartConfig
import java.awt.Color

/**
 * Renderer for bar charts that display comparative data between different categories.
 * Perfect for showing member contributions, individual performance, and rankings.
 */
class BarChartRenderer(
    canvas: MapCanvas,
    config: ChartConfig = ChartConfig()
) : ChartRenderer(canvas, config) {

    /**
     * Orientation of the bar chart.
     */
    enum class Orientation {
        VERTICAL,
        HORIZONTAL
    }

    /**
     * Renders a bar chart with the provided data points.
     *
     * @param data List of data points to display as bars
     * @param bounds The bounds where the chart should be rendered
     * @param metadata Chart metadata (title, labels, etc.)
     */
    override fun render(data: List<DataPoint>, bounds: ChartBounds, metadata: ChartMetadata) {
        if (!validateData(data)) {
            renderErrorMessage(bounds, "Invalid Data")
            return
        }

        // Clear the chart area
        canvas.fill(metadata.backgroundColor)

        // Render basic chart elements
        renderTitle(bounds, metadata)
        renderBarChartAxisLabels(bounds, metadata)

        if (metadata.showGrid) {
            renderBarChartGrid(bounds)
        }

        // Calculate data range for scaling
        val (dataMin, dataMax) = calculateValueRange(data)

        // Render bars
        renderBars(data, bounds, dataMin, dataMax, Orientation.VERTICAL)

        // Render value labels on bars
        renderValueLabels(data, bounds, dataMin, dataMax)

        // Render category labels
        renderCategoryLabels(data, bounds)

        // Render legend if needed
        if (metadata.showLegend && data.size > 1) {
            renderLegend(bounds, data)
        }

        // Apply the rendering to the map
        canvas.applyToMap()
    }

    /**
     * Renders bars for each data point.
     */
    private fun renderBars(data: List<DataPoint>, bounds: ChartBounds, dataMin: Double, dataMax: Double, orientation: Orientation) {
        val barSpacing = 4 // Pixels between bars
        val availableWidth = bounds.contentWidth - (barSpacing * (data.size + 1))
        val barWidth = availableWidth / data.size

        data.forEachIndexed { index, point ->
            val barHeight = if (dataMax > dataMin) {
                ((point.value.toDouble() - dataMin) / (dataMax - dataMin) * bounds.contentHeight).toInt()
            } else {
                bounds.contentHeight / 2 // Default height if all values are the same
            }

            val barX = bounds.contentX + barSpacing + (index * (barWidth + barSpacing))
            val barY = bounds.contentY + bounds.contentHeight - barHeight

            // Use point color or default to a nice blue
            val barColor = point.color

            // Draw the bar
            canvas.drawRectangle(barX, barY, barWidth, barHeight, barColor, true)

            // Add a subtle border
            canvas.drawRectangle(barX, barY, barWidth, barHeight, MapCanvas.MinecraftColor(Color(50, 50, 50)), false)
        }
    }

    /**
     * Renders value labels above each bar.
     */
    private fun renderValueLabels(data: List<DataPoint>, bounds: ChartBounds, dataMin: Double, dataMax: Double) {
        val barSpacing = 4
        val availableWidth = bounds.contentWidth - (barSpacing * (data.size + 1))
        val barWidth = availableWidth / data.size

        data.forEachIndexed { index, point ->
            val barHeight = if (dataMax > dataMin) {
                ((point.value.toDouble() - dataMin) / (dataMax - dataMin) * bounds.contentHeight).toInt()
            } else {
                bounds.contentHeight / 2
            }

            val barX = bounds.contentX + barSpacing + (index * (barWidth + barSpacing))
            val labelX = barX + (barWidth / 2) - 8 // Center the label
            val labelY = bounds.contentY + bounds.contentHeight - barHeight - 8

            // Format the value (keep it short for map display)
            val displayValue = formatValue(point.value.toDouble())

            renderSimpleText(displayValue, labelX, labelY, MapCanvas.MinecraftColor(Color.BLACK))
        }
    }

    /**
     * Renders category labels below each bar.
     */
    private fun renderCategoryLabels(data: List<DataPoint>, bounds: ChartBounds) {
        val barSpacing = 4
        val availableWidth = bounds.contentWidth - (barSpacing * (data.size + 1))
        val barWidth = availableWidth / data.size

        data.forEachIndexed { index, point ->
            val barX = bounds.contentX + barSpacing + (index * (barWidth + barSpacing))
            val labelX = barX + (barWidth / 2) - (point.label.length * 3 / 2) // Center the label
            val labelY = bounds.contentY + bounds.contentHeight + 2

            // Truncate long labels
            val displayLabel = if (point.label.length > 6) {
                point.label.substring(0, 6)
            } else {
                point.label
            }

            renderSimpleText(displayLabel, labelX, labelY, MapCanvas.MinecraftColor(Color(100, 100, 100)))
        }
    }

    /**
     * Renders a legend showing the color coding.
     */
    private fun renderLegend(bounds: ChartBounds, data: List<DataPoint>) {
        val legendX = bounds.x + bounds.width - 50
        val legendY = bounds.y + 20
        val itemHeight = 8

        // Legend background
        canvas.drawRectangle(legendX, legendY, 45, data.size * itemHeight + 10,
                           MapCanvas.MinecraftColor(Color(250, 250, 250)), true)

        // Legend border
        canvas.drawRectangle(legendX, legendY, 45, data.size * itemHeight + 10,
                           MapCanvas.MinecraftColor(Color(200, 200, 200)), false)

        // Legend title
        renderSimpleText("KEY", legendX + 15, legendY + 2, MapCanvas.MinecraftColor(Color.BLACK))

        // Legend items
        data.forEachIndexed { index, point ->
            val itemY = legendY + 8 + (index * itemHeight)

            // Color box
            canvas.drawRectangle(legendX + 3, itemY, 6, 6, point.color, true)
            canvas.drawRectangle(legendX + 3, itemY, 6, 6, MapCanvas.MinecraftColor(Color.BLACK), false)

            // Label
            val label = if (point.label.length > 8) {
                point.label.substring(0, 8)
            } else {
                point.label
            }
            renderSimpleText(label, legendX + 12, itemY, MapCanvas.MinecraftColor(Color(80, 80, 80)))
        }
    }

    /**
     * Renders an error message when data is invalid.
     */
    private fun renderErrorMessage(bounds: ChartBounds, message: String) {
        canvas.fill(MapCanvas.MinecraftColor(Color(255, 220, 220))) // Light red background

        val centerX = bounds.x + (bounds.width / 2) - (message.length * 3)
        val centerY = bounds.y + (bounds.height / 2) - 3

        renderSimpleText(message, centerX, centerY, MapCanvas.MinecraftColor(Color(180, 0, 0)))

        canvas.applyToMap()
    }

    /**
     * Renders axis labels and markers.
     */
    private fun renderBarChartAxisLabels(bounds: ChartBounds, metadata: ChartMetadata) {
        super.renderAxisLabels(bounds, metadata)

        // Add value markers on Y-axis
        renderYAxisMarkers(bounds)
    }

    /**
     * Renders value markers along the Y-axis.
     */
    private fun renderYAxisMarkers(bounds: ChartBounds) {
        val markerColor = MapCanvas.MinecraftColor(Color(150, 150, 150))
        val textColor = MapCanvas.MinecraftColor(Color(100, 100, 100))

        // Draw markers at key percentages
        val percentages = listOf(0, 25, 50, 75, 100)
        percentages.forEachIndexed { index, percentage ->
            val y = bounds.contentY + (index * bounds.contentHeight / 4)

            // Small tick mark
            canvas.drawLine(bounds.contentX - 3, y, bounds.contentX, y, markerColor)

            // Value label
            val labelX = bounds.contentX - 20
            val labelY = y - 2
            renderSimpleText("$percentage%", labelX, labelY, textColor)
        }
    }

    /**
     * Enhanced grid rendering with axis-aligned lines.
     */
    private fun renderBarChartGrid(bounds: ChartBounds) {
        super.renderGrid(bounds)

        // Add axis lines
        val axisColor = MapCanvas.MinecraftColor(Color(100, 100, 100))

        // X-axis
        canvas.drawLine(bounds.contentX, bounds.contentY + bounds.contentHeight,
                      bounds.contentX + bounds.contentWidth, bounds.contentY + bounds.contentHeight, axisColor)

        // Y-axis
        canvas.drawLine(bounds.contentX, bounds.contentY,
                      bounds.contentX, bounds.contentY + bounds.contentHeight, axisColor)
    }

    /**
     * Formats a numeric value for display on the chart.
     */
    private fun formatValue(value: Double): String {
        return when {
            value >= 1000000 -> "${(value / 1000000).toInt()}M"
            value >= 1000 -> "${(value / 1000).toInt()}K"
            value >= 100 -> value.toInt().toString()
            value >= 10 -> String.format("%.1f", value)
            else -> String.format("%.2f", value)
        }
    }

    /**
     * Factory method to create a bar chart for member contributions.
     */
    fun createMemberContributionChart(contributions: List<Pair<String, Number>>): List<DataPoint> {
        return contributions.mapIndexed { index, (memberName, contribution) ->
            // Use different colors for each member
            val colors = listOf(
                Color(100, 150, 255), // Blue
                Color(255, 150, 100), // Orange
                Color(150, 255, 100), // Green
                Color(255, 100, 150), // Pink
                Color(150, 100, 255)  // Purple
            )
            val color = colors[index % colors.size]

            DataPoint(memberName, contribution, MapCanvas.MinecraftColor(color))
        }
    }

    /**
     * Factory method to create a bar chart for kill rankings.
     */
    fun createKillRankingChart(killData: List<Pair<String, Number>>): List<DataPoint> {
        return killData.mapIndexed { index, (playerName, kills) ->
            // Color gradient from red (high) to yellow (medium) to green (low)
            val intensity = (killData.size - index - 1).toDouble() / (killData.size - 1)
            val red = (255 * (1 - intensity)).toInt()
            val green = (100 + 155 * intensity).toInt()
            val blue = 50

            DataPoint(playerName, kills, MapCanvas.MinecraftColor(Color(red, green, blue)))
        }
    }

    /**
     * Factory method to create a bar chart for activity levels.
     */
    fun createActivityChart(activityData: List<Pair<String, Number>>): List<DataPoint> {
        return activityData.map { (category, activity) ->
            DataPoint(category, activity, MapCanvas.MinecraftColor(Color(150, 200, 255)))
        }
    }
}
