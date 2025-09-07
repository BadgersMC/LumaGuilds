package net.lumalyte.lg.application.actions.player.tool

import net.lumalyte.lg.application.services.ToolItemService

class IsItemMoveTool(private val toolItemService: ToolItemService) {
    fun execute(itemData: Map<String, String>?): Boolean {
        return toolItemService.isMoveTool(itemData)
    }
}
