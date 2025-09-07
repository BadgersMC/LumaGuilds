package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ChartConfig
import java.awt.Color

/**
 * Renderer for line charts that display trends and changes over time.
 * Perfect for showing guild performance metrics, balance changes, and historical data.
 */
class LineChartRenderer(
    canvas: MapCanvas,
    config: ChartConfig = ChartConfig()
) : ChartRenderer(canvas, config) {

    /**
     * Renders a line chart with the provided data points.
     *
     * @param data List of data points to plot as a line
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
        renderLineChartAxisLabels(bounds, metadata)

        if (metadata.showGrid) {
            renderLineChartGrid(bounds)
        }

        // Calculate data range for scaling
        val (dataMin, dataMax) = calculateValueRange(data)

        // Render data points and connecting lines
        renderDataLines(data, bounds, dataMin, dataMax)

        // Render data point markers
        renderDataPoints(data, bounds, dataMin, dataMax)

        // Render legend if multiple data series (future enhancement)
        if (metadata.showLegend) {
            renderLegend(bounds, data)
        }

        // Apply the rendering to the map
        canvas.applyToMap()
    }

    /**
     * Renders the connecting lines between data points.
     */
    private fun renderDataLines(data: List<DataPoint>, bounds: ChartBounds, dataMin: Double, dataMax: Double) {
        if (data.size < 2) return

        val lineColor = MapCanvas.MinecraftColor(Color.BLUE)

        for (i in 0 until data.size - 1) {
            val point1 = data[i]
            val point2 = data[i + 1]

            // Calculate pixel coordinates
            val x1 = scaleValue(i.toDouble(), 0.0, (data.size - 1).toDouble(),
                               bounds.contentX.toDouble(), (bounds.contentX + bounds.contentWidth).toDouble()).toInt()
            val y1 = scaleValue(point1.value.toDouble(), dataMin, dataMax,
                               (bounds.contentY + bounds.contentHeight).toDouble(), bounds.contentY.toDouble()).toInt()

            val x2 = scaleValue((i + 1).toDouble(), 0.0, (data.size - 1).toDouble(),
                               bounds.contentX.toDouble(), (bounds.contentX + bounds.contentWidth).toDouble()).toInt()
            val y2 = scaleValue(point2.value.toDouble(), dataMin, dataMax,
                               (bounds.contentY + bounds.contentHeight).toDouble(), bounds.contentY.toDouble()).toInt()

            // Draw line between points
            canvas.drawLine(x1, y1, x2, y2, lineColor)
        }
    }

    /**
     * Renders markers at each data point.
     */
    private fun renderDataPoints(data: List<DataPoint>, bounds: ChartBounds, dataMin: Double, dataMax: Double) {
        val pointColor = MapCanvas.MinecraftColor(Color.RED)
        val pointSize = 2 // Size of the point marker

        data.forEachIndexed { index, point ->
            // Calculate pixel coordinates
            val x = scaleValue(index.toDouble(), 0.0, (data.size - 1).toDouble(),
                              bounds.contentX.toDouble(), (bounds.contentX + bounds.contentWidth).toDouble()).toInt()
            val y = scaleValue(point.value.toDouble(), dataMin, dataMax,
                              (bounds.contentY + bounds.contentHeight).toDouble(), bounds.contentY.toDouble()).toInt()

            // Draw point marker (small circle or square)
            canvas.drawCircle(x, y, pointSize, pointColor, true)
        }
    }

    /**
     * Renders a simple legend for the line chart.
     */
    private fun renderLegend(bounds: ChartBounds, data: List<DataPoint>) {
        if (data.isEmpty()) return

        val legendX = bounds.x + bounds.width - 40
        val legendY = bounds.y + 20
        val legendWidth = 35
        val legendHeight = 25

        // Legend background
        canvas.drawRectangle(legendX, legendY, legendWidth, legendHeight,
                           MapCanvas.MinecraftColor(Color(240, 240, 240)), true)

        // Legend border
        canvas.drawRectangle(legendX, legendY, legendWidth, legendHeight,
                           MapCanvas.MinecraftColor(Color.BLACK), false)

        // Line sample
        val lineColor = MapCanvas.MinecraftColor(Color.BLUE)
        canvas.drawLine(legendX + 5, legendY + 8, legendX + 15, legendY + 8, lineColor)

        // Point sample
        val pointColor = MapCanvas.MinecraftColor(Color.RED)
        canvas.drawCircle(legendX + 10, legendY + 8, 1, pointColor, true)

        // Label
        renderSimpleText("DATA", legendX + 18, legendY + 5, MapCanvas.MinecraftColor(Color.BLACK))
    }

    /**
     * Renders an error message when data is invalid.
     */
    private fun renderErrorMessage(bounds: ChartBounds, message: String) {
        canvas.fill(MapCanvas.MinecraftColor(Color(255, 200, 200))) // Light red background

        val centerX = bounds.x + (bounds.width / 2) - (message.length * 3)
        val centerY = bounds.y + (bounds.height / 2) - 3

        renderSimpleText(message, centerX, centerY, MapCanvas.MinecraftColor(Color.RED))

        canvas.applyToMap()
    }

    /**
     * Renders axis labels and value markers.
     */
    private fun renderLineChartAxisLabels(bounds: ChartBounds, metadata: ChartMetadata) {
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

        // Draw markers at 0%, 25%, 50%, 75%, 100%
        for (i in 0..4) {
            val y = bounds.contentY + (i * bounds.contentHeight / 4)
            val percentage = 100 - (i * 25) // Invert because Y=0 is top

            // Small tick mark
            canvas.drawLine(bounds.contentX - 3, y, bounds.contentX, y, markerColor)

            // Value label
            val labelX = bounds.contentX - 25
            val labelY = y - 2
            renderSimpleText("$percentage%", labelX, labelY, textColor)
        }
    }

    /**
     * Enhanced grid rendering with axis-aligned lines.
     */
    private fun renderLineChartGrid(bounds: ChartBounds) {
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
     * Factory method to create a line chart for guild balance over time.
     */
    fun createGuildBalanceChart(balanceHistory: List<Pair<String, Number>>): List<DataPoint> {
        return balanceHistory.map { (date, balance) ->
            DataPoint(date, balance, MapCanvas.MinecraftColor(Color(0, 100, 200)))
        }
    }

    /**
     * Factory method to create a line chart for kill trends.
     */
    fun createKillTrendChart(killHistory: List<Pair<String, Number>>): List<DataPoint> {
        return killHistory.map { (date, kills) ->
            DataPoint(date, kills, MapCanvas.MinecraftColor(Color(200, 50, 50)))
        }
    }

    /**
     * Factory method to create a line chart for war performance.
     */
    fun createWarPerformanceChart(warHistory: List<Pair<String, Number>>): List<DataPoint> {
        return warHistory.map { (date, performance) ->
            DataPoint(date, performance, MapCanvas.MinecraftColor(Color(150, 100, 50)))
        }
    }
}
