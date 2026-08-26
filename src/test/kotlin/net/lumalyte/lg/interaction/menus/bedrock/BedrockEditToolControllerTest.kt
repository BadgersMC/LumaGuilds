package net.lumalyte.lg.interaction.menus.bedrock

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BedrockEditToolControllerTest {
    @Test
    fun `toggle delegates for the current player and returns the new mode`() {
        val playerId = UUID.randomUUID()
        var toggledPlayer: UUID? = null
        val controller = BedrockEditToolController(
            getMode = { 0 },
            toggleMode = { id -> toggledPlayer = id; BedrockEditToolToggleResult.Changed(1) }
        )

        assertEquals(0, controller.currentMode(playerId))
        assertEquals(1, assertIs<BedrockEditToolToggleResult.Changed>(controller.toggle(playerId)).mode)
        assertEquals(playerId, toggledPlayer)
    }

    @Test
    fun `cooldown is preserved instead of reported as a change`() {
        val controller = BedrockEditToolController(
            getMode = { 0 },
            toggleMode = { BedrockEditToolToggleResult.OnCooldown(12) }
        )

        assertEquals(12, assertIs<BedrockEditToolToggleResult.OnCooldown>(controller.toggle(UUID.randomUUID())).seconds)
    }
}
