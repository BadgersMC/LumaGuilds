package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ChartConfig
import java.awt.Color

/**
 * Abstract base class for chart renderers that provides common functionality
 * for rendering different types of charts on Minecraft maps.
 */
abstract class ChartRenderer(
    protected val canvas: MapCanvas,
    protected val config: ChartConfig = ChartConfig()
) {

    /**
     * Chart data point representation.
     */
    data class DataPoint(
        val label: String,
        val value: Number,
        val color: MapCanvas.MinecraftColor = MapCanvas.MinecraftColor(Color.BLUE)
    )

    /**
     * Chart metadata for titles, labels, and legends.
     */
    data class ChartMetadata(
        val title: String? = null,
        val xAxisLabel: String? = null,
        val yAxisLabel: String? = null,
        val showLegend: Boolean = true,
        val showGrid: Boolean = true,
        val backgroundColor: MapCanvas.MinecraftColor = MapCanvas.MinecraftColor(Color.WHITE)
    )

    /**
     * Chart dimensions and layout information.
     */
    data class ChartBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val padding: Int = 10
    ) {
        val contentX = x + padding
        val contentY = y + padding
        val contentWidth = width - (2 * padding)
        val contentHeight = height - (2 * padding)
    }

    // Chart area bounds (excluding title and labels)
    protected lateinit var chartBounds: ChartBounds

    // Chart metadata
    protected var metadata: ChartMetadata = ChartMetadata()

    /**
     * Main render method that subclasses must implement.
     * This method should render the specific chart type using the provided data.
     *
     * @param data The data points to render
     * @param bounds The bounds where the chart should be rendered
     * @param metadata Chart metadata (title, labels, etc.)
     */
    abstract fun render(data: List<DataPoint>, bounds: ChartBounds, metadata: ChartMetadata = ChartMetadata())

    /**
     * Renders the chart with default full-canvas bounds.
     *
     * @param data The data points to render
     * @param metadata Chart metadata
     */
    fun render(data: List<DataPoint>, metadata: ChartMetadata = ChartMetadata()) {
        val bounds = ChartBounds(0, 0, canvas.getWidth(), canvas.getHeight())
        render(data, bounds, metadata)
    }

    /**
     * Calculates the value range for scaling chart elements.
     *
     * @param data The data points
     * @return Pair of (minValue, maxValue)
     */
    protected fun calculateValueRange(data: List<DataPoint>): Pair<Double, Double> {
        if (data.isEmpty()) return Pair(0.0, 1.0)

        val values = data.map { it.value.toDouble() }
        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 1.0

        // Add some padding to the range
        val padding = (maxValue - minValue) * 0.1
        return Pair(
            (minValue - padding).coerceAtMost(0.0),
            maxValue + padding
        )
    }

    /**
     * Scales a value from data range to chart coordinate range.
     *
     * @param value The data value to scale
     * @param dataMin Minimum value in data range
     * @param dataMax Maximum value in data range
     * @param chartMin Minimum value in chart range
     * @param chartMax Maximum value in chart range
     * @return Scaled value
     */
    protected fun scaleValue(
        value: Double,
        dataMin: Double,
        dataMax: Double,
        chartMin: Double,
        chartMax: Double
    ): Double {
        if (dataMax == dataMin) return chartMin

        return chartMin + (value - dataMin) * (chartMax - chartMin) / (dataMax - dataMin)
    }

    /**
     * Renders chart title at the top of the chart area.
     *
     * @param bounds Chart bounds
     * @param metadata Chart metadata
     */
    protected fun renderTitle(bounds: ChartBounds, metadata: ChartMetadata) {
        metadata.title?.let { title ->
            val titleY = bounds.y + 5
            val titleX = bounds.x + (bounds.width / 2)

            // Draw title background
            canvas.drawRectangle(
                bounds.x,
                bounds.y,
                bounds.width,
                15,
                MapCanvas.MinecraftColor(Color(200, 200, 200)),
                true
            )

            // Render title text (simplified - full text rendering will be in text renderer)
            renderSimpleText(title, titleX - (title.length * 3), titleY + 2, MapCanvas.MinecraftColor(Color.BLACK))
        }
    }

    /**
     * Renders axis labels.
     *
     * @param bounds Chart bounds
     * @param metadata Chart metadata
     */
    protected fun renderAxisLabels(bounds: ChartBounds, metadata: ChartMetadata) {
        metadata.xAxisLabel?.let { label ->
            val labelX = bounds.x + (bounds.width / 2)
            val labelY = bounds.y + bounds.height - 8
            renderSimpleText(label, labelX - (label.length * 3), labelY, MapCanvas.MinecraftColor(Color.BLACK))
        }

        metadata.yAxisLabel?.let { label ->
            val labelX = bounds.x + 5
            val labelY = bounds.y + (bounds.height / 2)
            // Rotate text 90 degrees for Y axis (simplified representation)
            renderSimpleText(label, labelX, labelY - (label.length * 3), MapCanvas.MinecraftColor(Color.BLACK))
        }
    }

    /**
     * Renders a simple grid for the chart background.
     *
     * @param bounds Chart bounds
     */
    protected fun renderGrid(bounds: ChartBounds) {
        val gridColor = MapCanvas.MinecraftColor(Color(220, 220, 220))

        // Vertical grid lines
        for (i in 0..4) {
            val x = bounds.contentX + (i * bounds.contentWidth / 4)
            canvas.drawLine(x, bounds.contentY, x, bounds.contentY + bounds.contentHeight, gridColor)
        }

        // Horizontal grid lines
        for (i in 0..4) {
            val y = bounds.contentY + (i * bounds.contentHeight / 4)
            canvas.drawLine(bounds.contentX, y, bounds.contentX + bounds.contentWidth, y, gridColor)
        }
    }

    /**
     * Simple text rendering (placeholder - will be replaced with proper text renderer).
     * This is a basic implementation that renders text as simple pixel patterns.
     *
     * @param text The text to render
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param color Text color
     */
    protected fun renderSimpleText(text: String, x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        var currentX = x

        for (char in text) {
            when (char.uppercaseChar()) {
                'A' -> renderCharA(currentX, y, color)
                'B' -> renderCharB(currentX, y, color)
                'C' -> renderCharC(currentX, y, color)
                'D' -> renderCharD(currentX, y, color)
                'E' -> renderCharE(currentX, y, color)
                'F' -> renderCharF(currentX, y, color)
                'G' -> renderCharG(currentX, y, color)
                'H' -> renderCharH(currentX, y, color)
                'I' -> renderCharI(currentX, y, color)
                'J' -> renderCharJ(currentX, y, color)
                'K' -> renderCharK(currentX, y, color)
                'L' -> renderCharL(currentX, y, color)
                'M' -> renderCharM(currentX, y, color)
                'N' -> renderCharN(currentX, y, color)
                'O' -> renderCharO(currentX, y, color)
                'P' -> renderCharP(currentX, y, color)
                'Q' -> renderCharQ(currentX, y, color)
                'R' -> renderCharR(currentX, y, color)
                'S' -> renderCharS(currentX, y, color)
                'T' -> renderCharT(currentX, y, color)
                'U' -> renderCharU(currentX, y, color)
                'V' -> renderCharV(currentX, y, color)
                'W' -> renderCharW(currentX, y, color)
                'X' -> renderCharX(currentX, y, color)
                'Y' -> renderCharY(currentX, y, color)
                'Z' -> renderCharZ(currentX, y, color)
                ' ' -> {} // Space - do nothing
                else -> renderCharUnknown(currentX, y, color)
            }
            currentX += 6 // Character width + spacing
        }
    }

    // Character rendering methods (5x7 pixel font)
    private fun renderCharA(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // A pattern:  010
        //           1010
        //           1111
        //           1010
        //           1010
        setCharPixel(x + 1, y, color)     // 010
        setCharPixel(x, y + 1, color)     // 1010
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 1111
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x + 3, y + 2, color)
        setCharPixel(x, y + 3, color)     // 1010
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x, y + 4, color)     // 1010
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharB(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // B pattern: 110
        //           101
        //           110
        //           101
        //           110
        setCharPixel(x, y, color)         // 110
        setCharPixel(x + 1, y, color)
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 110
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x, y + 4, color)     // 110
        setCharPixel(x + 1, y + 4, color)
    }

    private fun renderCharC(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // C pattern: 011
        //           100
        //           100
        //           100
        //           011
        setCharPixel(x + 1, y, color)     // 011
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 100
        setCharPixel(x, y + 2, color)     // 100
        setCharPixel(x, y + 3, color)     // 100
        setCharPixel(x + 1, y + 4, color) // 011
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharD(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // D pattern: 110
        //           101
        //           101
        //           101
        //           110
        setCharPixel(x, y, color)         // 110
        setCharPixel(x + 1, y, color)
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 101
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x, y + 4, color)     // 110
        setCharPixel(x + 1, y + 4, color)
    }

    private fun renderCharE(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // E pattern: 111
        //           100
        //           110
        //           100
        //           111
        setCharPixel(x, y, color)         // 111
        setCharPixel(x + 1, y, color)
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 100
        setCharPixel(x, y + 2, color)     // 110
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x, y + 3, color)     // 100
        setCharPixel(x, y + 4, color)     // 111
        setCharPixel(x + 1, y + 4, color)
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharF(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // F pattern: 111
        //           100
        //           110
        //           100
        //           100
        setCharPixel(x, y, color)         // 111
        setCharPixel(x + 1, y, color)
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 100
        setCharPixel(x, y + 2, color)     // 110
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x, y + 3, color)     // 100
        setCharPixel(x, y + 4, color)     // 100
    }

    private fun renderCharG(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // G pattern: 011
        //           100
        //           101
        //           101
        //           011
        setCharPixel(x + 1, y, color)     // 011
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 100
        setCharPixel(x, y + 2, color)     // 101
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x + 1, y + 4, color) // 011
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharH(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // H pattern: 101
        //           101
        //           111
        //           101
        //           101
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 111
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x, y + 4, color)     // 101
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharI(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // I pattern: 1
        //           1
        //           1
        //           1
        //           1
        setCharPixel(x + 1, y, color)     // 1
        setCharPixel(x + 1, y + 1, color) // 1
        setCharPixel(x + 1, y + 2, color) // 1
        setCharPixel(x + 1, y + 3, color) // 1
        setCharPixel(x + 1, y + 4, color) // 1
    }

    private fun renderCharJ(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // J pattern: 001
        //           001
        //           001
        //           101
        //           010
        setCharPixel(x + 2, y, color)     // 001
        setCharPixel(x + 2, y + 1, color) // 001
        setCharPixel(x + 2, y + 2, color) // 001
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x + 1, y + 4, color) // 010
    }

    private fun renderCharK(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // K pattern: 101
        //           100
        //           110
        //           100
        //           101
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 100
        setCharPixel(x, y + 2, color)     // 110
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x, y + 3, color)     // 100
        setCharPixel(x, y + 4, color)     // 101
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharL(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // L pattern: 100
        //           100
        //           100
        //           100
        //           111
        setCharPixel(x, y, color)         // 100
        setCharPixel(x, y + 1, color)     // 100
        setCharPixel(x, y + 2, color)     // 100
        setCharPixel(x, y + 3, color)     // 100
        setCharPixel(x, y + 4, color)     // 111
        setCharPixel(x + 1, y + 4, color)
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharM(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // M pattern: 101
        //           111
        //           111
        //           101
        //           101
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 111
        setCharPixel(x + 1, y + 1, color)
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 111
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x, y + 4, color)     // 101
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharN(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // N pattern: 101
        //           111
        //           111
        //           111
        //           101
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 111
        setCharPixel(x + 1, y + 1, color)
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 111
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 111
        setCharPixel(x + 1, y + 3, color)
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x, y + 4, color)     // 101
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharO(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // O pattern: 010
        //           101
        //           101
        //           101
        //           010
        setCharPixel(x + 1, y, color)     // 010
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 101
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x + 1, y + 4, color) // 010
    }

    private fun renderCharP(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // P pattern: 110
        //           101
        //           110
        //           100
        //           100
        setCharPixel(x, y, color)         // 110
        setCharPixel(x + 1, y, color)
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 110
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x, y + 3, color)     // 100
        setCharPixel(x, y + 4, color)     // 100
    }

    private fun renderCharQ(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // Q pattern: 010
        //           101
        //           101
        //           101
        //           011
        setCharPixel(x + 1, y, color)     // 010
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 101
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x + 1, y + 4, color) // 011
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharR(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // R pattern: 110
        //           101
        //           110
        //           101
        //           101
        setCharPixel(x, y, color)         // 110
        setCharPixel(x + 1, y, color)
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 110
        setCharPixel(x + 1, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x, y + 4, color)     // 101
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharS(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // S pattern: 011
        //           100
        //           010
        //           001
        //           110
        setCharPixel(x + 1, y, color)     // 011
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 100
        setCharPixel(x + 1, y + 2, color) // 010
        setCharPixel(x + 2, y + 3, color) // 001
        setCharPixel(x, y + 4, color)     // 110
        setCharPixel(x + 1, y + 4, color)
    }

    private fun renderCharT(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // T pattern: 111
        //           010
        //           010
        //           010
        //           010
        setCharPixel(x, y, color)         // 111
        setCharPixel(x + 1, y, color)
        setCharPixel(x + 2, y, color)
        setCharPixel(x + 1, y + 1, color) // 010
        setCharPixel(x + 1, y + 2, color) // 010
        setCharPixel(x + 1, y + 3, color) // 010
        setCharPixel(x + 1, y + 4, color) // 010
    }

    private fun renderCharU(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // U pattern: 101
        //           101
        //           101
        //           101
        //           010
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 101
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x + 1, y + 4, color) // 010
    }

    private fun renderCharV(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // V pattern: 101
        //           101
        //           101
        //           101
        //           010
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 101
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 101
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x + 1, y + 4, color) // 010
    }

    private fun renderCharW(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // W pattern: 101
        //           101
        //           101
        //           111
        //           101
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x, y + 1, color)     // 101
        setCharPixel(x + 2, y + 1, color)
        setCharPixel(x, y + 2, color)     // 101
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x, y + 3, color)     // 111
        setCharPixel(x + 1, y + 3, color)
        setCharPixel(x + 2, y + 3, color)
        setCharPixel(x, y + 4, color)     // 101
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharX(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // X pattern: 101
        //           010
        //           101
        //           010
        //           101
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x + 1, y + 1, color) // 010
        setCharPixel(x, y + 2, color)     // 101
        setCharPixel(x + 2, y + 2, color)
        setCharPixel(x + 1, y + 3, color) // 010
        setCharPixel(x, y + 4, color)     // 101
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharY(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // Y pattern: 101
        //           010
        //           010
        //           010
        //           010
        setCharPixel(x, y, color)         // 101
        setCharPixel(x + 2, y, color)
        setCharPixel(x + 1, y + 1, color) // 010
        setCharPixel(x + 1, y + 2, color) // 010
        setCharPixel(x + 1, y + 3, color) // 010
        setCharPixel(x + 1, y + 4, color) // 010
    }

    private fun renderCharZ(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // Z pattern: 111
        //           001
        //           010
        //           100
        //           111
        setCharPixel(x, y, color)         // 111
        setCharPixel(x + 1, y, color)
        setCharPixel(x + 2, y, color)
        setCharPixel(x + 2, y + 1, color) // 001
        setCharPixel(x + 1, y + 2, color) // 010
        setCharPixel(x, y + 3, color)     // 100
        setCharPixel(x, y + 4, color)     // 111
        setCharPixel(x + 1, y + 4, color)
        setCharPixel(x + 2, y + 4, color)
    }

    private fun renderCharUnknown(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        // Unknown character - render a question mark-like shape
        setCharPixel(x + 1, y, color)     // 010
        setCharPixel(x, y + 1, color)     // 100
        setCharPixel(x + 1, y + 2, color) // 010
        setCharPixel(x + 1, y + 4, color) // 010
    }

    private fun setCharPixel(x: Int, y: Int, color: MapCanvas.MinecraftColor) {
        canvas.setPixel(x, y, color)
    }

    /**
     * Validates chart data before rendering.
     *
     * @param data The data to validate
     * @return true if data is valid for rendering
     */
    protected fun validateData(data: List<DataPoint>): Boolean {
        return data.isNotEmpty() && data.all { it.value.toDouble().isFinite() }
    }

    /**
     * Gets the canvas instance for direct access if needed.
     */
    protected fun getChartCanvas(): MapCanvas = canvas
}
