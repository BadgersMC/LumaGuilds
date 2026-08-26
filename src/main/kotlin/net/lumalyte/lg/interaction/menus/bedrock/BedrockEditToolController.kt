package net.lumalyte.lg.interaction.menus.bedrock

import java.util.UUID

class BedrockEditToolController(
    private val getMode: (UUID) -> Int,
    private val toggleMode: (UUID) -> BedrockEditToolToggleResult
) {
    fun currentMode(playerId: UUID): Int = getMode(playerId)

    fun toggle(playerId: UUID): BedrockEditToolToggleResult = toggleMode(playerId)
}

sealed interface BedrockEditToolToggleResult {
    data class Changed(val mode: Int) : BedrockEditToolToggleResult
    data class OnCooldown(val seconds: Int) : BedrockEditToolToggleResult
}
