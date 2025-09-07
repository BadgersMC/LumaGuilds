package net.lumalyte.lg.infrastructure.services

import org.bukkit.map.MapPalette
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.min

/**
 * Abstraction layer for drawing on Minecraft maps with pixel-level operations.
 * Handles coordinate system translation, color management, and basic drawing primitives.
 */
class MapCanvas(
    private val mapView: MapView,
    private val width: Int = 128,
    private val height: Int = 128
) {
    private val imageBuffer = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    // Minecraft color palette mapping
    private val minecraftColors = arrayOf(
        Color(0, 0, 0),        // Black
        Color(0, 0, 170),      // Dark Blue
        Color(0, 170, 0),      // Dark Green
        Color(0, 170, 170),    // Dark Aqua
        Color(170, 0, 0),      // Dark Red
        Color(170, 0, 170),    // Dark Purple
        Color(255, 170, 0),    // Gold
        Color(170, 170, 170),  // Gray
        Color(85, 85, 85),     // Dark Gray
        Color(85, 85, 255),    // Blue
        Color(85, 255, 85),    // Green
        Color(85, 255, 255),   // Aqua
        Color(255, 85, 85),    // Red
        Color(255, 85, 255),   // Light Purple
        Color(255, 255, 85),   // Yellow
        Color(255, 255, 255)   // White
    )

    /**
     * Represents a color in the Minecraft map palette.
     */
    data class MinecraftColor(val red: Int, val green: Int, val blue: Int) {
        constructor(color: Color) : this(color.red, color.green, color.blue)

        fun toRgbInt(): Int = (red shl 16) or (green shl 8) or blue

        companion object {
            fun fromRgbInt(rgb: Int): MinecraftColor {
                val red = (rgb shr 16) and 0xFF
                val green = (rgb shr 8) and 0xFF
                val blue = rgb and 0xFF
                return MinecraftColor(red, green, blue)
            }
        }
    }

    /**
     * Sets a pixel at the specified coordinates with the given color.
     *
     * @param x The x-coordinate (0-127)
     * @param y The y-coordinate (0-127)
     * @param color The color to set
     */
    fun setPixel(x: Int, y: Int, color: MinecraftColor) {
        if (x in 0 until width && y in 0 until height) {
            imageBuffer.setRGB(x, y, color.toRgbInt())
        }
    }

    /**
     * Sets a pixel using RGB values, automatically converting to nearest Minecraft color.
     *
     * @param x The x-coordinate (0-127)
     * @param y The y-coordinate (0-127)
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     */
    fun setPixelRgb(x: Int, y: Int, red: Int, green: Int, blue: Int) {
        // Find nearest Minecraft color from our palette
        var nearestColor = minecraftColors[0]
        var minDistance = Double.MAX_VALUE

        for (color in minecraftColors) {
            val distance = colorDistance(red, green, blue, color.red, color.green, color.blue)
            if (distance < minDistance) {
                minDistance = distance
                nearestColor = color
            }
        }

        val minecraftColor = MinecraftColor(nearestColor)
        setPixel(x, y, minecraftColor)
    }

    /**
     * Calculates the Euclidean distance between two colors in RGB space.
     */
    private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val dr = (r1 - r2).toDouble()
        val dg = (g1 - g2).toDouble()
        val db = (b1 - b2).toDouble()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    /**
     * Sets a pixel using a Color object.
     *
     * @param x The x-coordinate (0-127)
     * @param y The y-coordinate (0-127)
     * @param color The Color object
     */
    fun setPixelColor(x: Int, y: Int, color: Color) {
        setPixelRgb(x, y, color.red, color.green, color.blue)
    }

    /**
     * Gets the color of a pixel at the specified coordinates.
     *
     * @param x The x-coordinate (0-127)
     * @param y The y-coordinate (0-127)
     * @return The color at the specified position, or black if out of bounds
     */
    fun getPixel(x: Int, y: Int): MinecraftColor {
        return if (x in 0 until width && y in 0 until height) {
            MinecraftColor.fromRgbInt(imageBuffer.getRGB(x, y))
        } else {
            MinecraftColor(0, 0, 0) // Default to black
        }
    }

    /**
     * Draws a line from (x1, y1) to (x2, y2) using Bresenham's line algorithm.
     *
     * @param x1 Starting x-coordinate
     * @param y1 Starting y-coordinate
     * @param x2 Ending x-coordinate
     * @param y2 Ending y-coordinate
     * @param color The color of the line
     */
    fun drawLine(x1: Int, y1: Int, x2: Int, y2: Int, color: MinecraftColor) {
        val dx = kotlin.math.abs(x2 - x1)
        val dy = kotlin.math.abs(y2 - y1)
        val sx = if (x1 < x2) 1 else -1
        val sy = if (y1 < y2) 1 else -1
        var err = dx - dy

        var x = x1
        var y = y1

        while (true) {
            setPixel(x, y, color)

            if (x == x2 && y == y2) break

            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }

    /**
     * Draws a rectangle with the specified dimensions and color.
     *
     * @param x Top-left x-coordinate
     * @param y Top-left y-coordinate
     * @param width Rectangle width
     * @param height Rectangle height
     * @param color The color of the rectangle
     * @param filled Whether to fill the rectangle or just draw the outline
     */
    fun drawRectangle(x: Int, y: Int, width: Int, height: Int, color: MinecraftColor, filled: Boolean = true) {
        if (filled) {
            for (px in x until min(x + width, this.width)) {
                for (py in y until min(y + height, this.height)) {
                    setPixel(px, py, color)
                }
            }
        } else {
            // Draw outline only
            drawLine(x, y, x + width - 1, y, color)
            drawLine(x + width - 1, y, x + width - 1, y + height - 1, color)
            drawLine(x + width - 1, y + height - 1, x, y + height - 1, color)
            drawLine(x, y + height - 1, x, y, color)
        }
    }

    /**
     * Draws a circle with the specified center and radius.
     *
     * @param centerX Center x-coordinate
     * @param centerY Center y-coordinate
     * @param radius Circle radius
     * @param color The color of the circle
     * @param filled Whether to fill the circle or just draw the outline
     */
    fun drawCircle(centerX: Int, centerY: Int, radius: Int, color: MinecraftColor, filled: Boolean = true) {
        val x0 = centerX
        val y0 = centerY
        var x = radius
        var y = 0
        var err = 0

        while (x >= y) {
            if (filled) {
                // Fill the circle by drawing horizontal lines
                drawLine(x0 - x, y0 + y, x0 + x, y0 + y, color)
                drawLine(x0 - x, y0 - y, x0 + x, y0 - y, color)
                drawLine(x0 - y, y0 + x, x0 + y, y0 + x, color)
                drawLine(x0 - y, y0 - x, x0 + y, y0 - x, color)
            } else {
                // Draw outline only
                setPixel(x0 + x, y0 + y, color)
                setPixel(x0 + y, y0 + x, color)
                setPixel(x0 - y, y0 + x, color)
                setPixel(x0 - x, y0 + y, color)
                setPixel(x0 - x, y0 - y, color)
                setPixel(x0 - y, y0 - x, color)
                setPixel(x0 + y, y0 - x, color)
                setPixel(x0 + x, y0 - y, color)
            }

            y += 1
            err += 1 + 2 * y
            if (2 * (err - x) + 1 > 0) {
                x -= 1
                err += 1 - 2 * x
            }
        }
    }

    /**
     * Fills the entire canvas with the specified color.
     *
     * @param color The color to fill with
     */
    fun fill(color: MinecraftColor) {
        drawRectangle(0, 0, width, height, color, true)
    }

    /**
     * Clears the canvas by filling it with black.
     */
    fun clear() {
        fill(MinecraftColor(0, 0, 0))
    }

    /**
     * Applies the current image buffer to the Minecraft map by adding a custom renderer.
     * This should be called after all drawing operations are complete.
     */
    fun applyToMap() {
        // Create a custom renderer that will draw our image buffer
        val renderer = ChartMapRenderer(imageBuffer, minecraftColors)
        mapView.addRenderer(renderer)
    }

    /**
     * Custom MapRenderer that renders our image buffer to the Minecraft map.
     */
    private class ChartMapRenderer(
        private val imageBuffer: BufferedImage,
        private val minecraftColors: Array<Color>
    ) : MapRenderer() {

        override fun render(mapView: MapView, mapCanvas: org.bukkit.map.MapCanvas, player: org.bukkit.entity.Player) {
            for (x in 0 until 128) {
                for (y in 0 until 128) {
                    if (x < imageBuffer.width && y < imageBuffer.height) {
                        val rgb = imageBuffer.getRGB(x, y)
                        val color = Color(rgb)

                        // Find nearest Minecraft color
                        val nearestColor = findNearestMinecraftColor(color.red, color.green, color.blue, minecraftColors)

                        // Convert to Minecraft color byte
                        val colorByte = MapPalette.matchColor(nearestColor.red, nearestColor.green, nearestColor.blue)
                        mapCanvas.setPixel(x, y, colorByte)
                    }
                }
            }
        }

        private fun findNearestMinecraftColor(red: Int, green: Int, blue: Int, colors: Array<Color>): Color {
            var nearestColor = colors[0]
            var minDistance = Double.MAX_VALUE

            for (color in colors) {
                val distance = colorDistance(red, green, blue, color.red, color.green, color.blue)
                if (distance < minDistance) {
                    minDistance = distance
                    nearestColor = color
                }
            }

            return nearestColor
        }

        private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
            val dr = (r1 - r2).toDouble()
            val dg = (g1 - g2).toDouble()
            val db = (b1 - b2).toDouble()
            return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
        }
    }



    /**
     * Gets the underlying Minecraft map view.
     */
    fun getMapView(): MapView = mapView

    /**
     * Gets the canvas width.
     */
    fun getWidth(): Int = width

    /**
     * Gets the canvas height.
     */
    fun getHeight(): Int = height
}
