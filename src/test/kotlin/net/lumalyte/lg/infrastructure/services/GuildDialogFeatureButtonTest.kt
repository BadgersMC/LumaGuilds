package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody
import net.kyori.adventure.text.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** REQ-236: the gold Browse Recruiting Guilds button must draw on one line inside the dialog surface. */
class GuildDialogFeatureButtonTest {
    private fun advance(font: String, code: Int): Int? =
        if (font == GuildDialogFeatureButton.FONT) GuildDialogFeatureButton.halfAdvance(code) else null

    @Test
    fun `art and label stay on one line where the client wraps plain messages`() {
        val label = assertNotNull(GuildDialogFeatureButton.label(Component.text("Browse Recruiting Guilds")))
        // The client wraps a plain message at its width minus 8.
        val rows = assertNotNull(GuildDialogText.rows(label, (GuildDialogFeatureButton.BODY_WIDTH - 8) * 2, ::advance))
        assertEquals(1, rows.size, "the art or text wrapped onto a second line")
        assertEquals(GuildDialogFeatureButton.ART * 2, rows.single().second, "label is exactly the art width, so the art is centred")
    }

    @Test
    fun `the themed dialog surface can measure the button so its background still draws`() {
        val label = assertNotNull(GuildDialogFeatureButton.label(Component.text("Browse Recruiting Guilds")))
        val body = mockk<PlainMessageDialogBody> {
            every { contents() } returns label
            every { width() } returns GuildDialogFeatureButton.BODY_WIDTH
        }
        val height = GuildDialogSurface.bodyHeight(body)
        assertEquals(17, height)
    }
}
