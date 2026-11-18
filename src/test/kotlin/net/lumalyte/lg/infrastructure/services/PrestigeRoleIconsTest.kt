package net.lumalyte.lg.infrastructure.services

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** REQ-298: role icons are the badge, centred on a transparent 256x256 square. */
class PrestigeRoleIconsTest {
    @Test
    fun `an off-centre badge comes out centred`() {
        val badge = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        for (y in 1..13) for (x in 1..13) badge.setRGB(x, y, 0xFFFF8800.toInt()) // 13x13 at (1,1): off-centre
        val out = ImageIO.read(ByteArrayInputStream(PrestigeRoleIcons.scale(badge)))
        assertEquals(256, out.width)
        val box = PrestigeRoleIcons.visible(out)!!
        assertEquals(box.x, 256 - (box.x + box.width))
        assertEquals(box.y, 256 - (box.y + box.height))
    }
}
