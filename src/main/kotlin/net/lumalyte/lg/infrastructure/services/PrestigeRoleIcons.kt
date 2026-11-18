package net.lumalyte.lg.infrastructure.services

import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/** REQ-298: reads a prestige badge PNG and scales pixel art up (no smoothing) to a Discord role icon. */
internal object PrestigeRoleIcons {
    const val SIZE = 256

    fun load(root: File, path: String): ByteArray? {
        val file = File(root, path).takeIf { it.isFile } ?: return null
        val source = runCatching { ImageIO.read(file) }.getOrNull() ?: return null
        return scale(source)
    }

    /**
     * Crops to the badge's visible pixels, scales by a whole factor (crisp pixels) and centres it on a
     * transparent square: the in-game badges sit a pixel off-centre on their 16x16 canvas.
     */
    fun scale(source: BufferedImage): ByteArray {
        val box = visible(source) ?: return png(BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB))
        val badge = source.getSubimage(box.x, box.y, box.width, box.height)
        val factor = maxOf(1, (SIZE - 16) / maxOf(box.width, box.height))
        val w = box.width * factor
        val h = box.height * factor
        val out = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(badge.getScaledInstance(w, h, Image.SCALE_REPLICATE), (SIZE - w) / 2, (SIZE - h) / 2, null)
        g.dispose()
        return png(out)
    }

    internal fun visible(image: BufferedImage): java.awt.Rectangle? {
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if ((image.getRGB(x, y) ushr 24) != 0) {
                minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
        }
        return if (maxX < 0) null else java.awt.Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun png(image: BufferedImage) = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}
