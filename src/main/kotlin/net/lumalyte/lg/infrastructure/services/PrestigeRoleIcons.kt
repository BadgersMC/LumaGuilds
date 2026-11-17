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

    fun scale(source: BufferedImage): ByteArray {
        val factor = maxOf(1, SIZE / maxOf(source.width, source.height))
        val out = BufferedImage(source.width * factor, source.height * factor, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(source.getScaledInstance(out.width, out.height, Image.SCALE_REPLICATE), 0, 0, null)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(out, "png", it) }.toByteArray()
    }
}
