package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.services.PlayerService
import net.lumalyte.lg.application.services.ToolItemService
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.Item
import org.slf4j.LoggerFactory
import java.util.UUID

class HytaleToolItemService(
    private val playerService: PlayerService
) : ToolItemService {

    private val log = LoggerFactory.getLogger(HytaleToolItemService::class.java)

    companion object {
        const val CLAIM_TOOL_KEY = "lumaguilds_claim_tool"
        const val MOVE_TOOL_KEY = "lumaguilds_move_tool"
        const val CLAIM_ID_KEY = "lumaguilds_claim_id"
    }

    override fun giveClaimTool(playerId: UUID): Boolean {
        val inventory = playerService.getPlayerInventory(playerId) ?: return false

        val tool = Item.of("Items/GoldenAxe", 1)
            .withDisplayName("§6Claim Tool")
            .withLore(
                "§7Right-click to select positions",
                "§7Create claims with /claim create"
            )
            .withMetadata(CLAIM_TOOL_KEY, "true")

        val remainder = inventory.addItem(tool)
        return remainder == null
    }

    override fun giveMoveTool(playerId: UUID, claim: Claim): Boolean {
        val inventory = playerService.getPlayerInventory(playerId) ?: return false

        val tool = Item.of("Items/Bell", 1)
            .withDisplayName("§6Claim Bell Mover")
            .withLore(
                "§7Right-click to place new claim bell",
                "§7Claim: ${claim.name}"
            )
            .withMetadata(MOVE_TOOL_KEY, "true")
            .withMetadata(CLAIM_ID_KEY, claim.id.toString())

        val remainder = inventory.addItem(tool)
        return remainder == null
    }

    override fun doesPlayerHaveClaimTool(playerId: UUID): Boolean {
        val inventory = playerService.getPlayerInventory(playerId) ?: return false

        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot) ?: continue
            val stringMetadata = item.metadata.mapValues { it.value.toString() }
            if (isClaimTool(stringMetadata)) return true
        }

        return false
    }

    override fun doesPlayerHaveMoveTool(playerId: UUID, claim: Claim): Boolean {
        val inventory = playerService.getPlayerInventory(playerId) ?: return false

        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot) ?: continue
            val stringMetadata = item.metadata.mapValues { it.value.toString() }
            if (isMoveTool(stringMetadata) && getClaimIdFromPlayerMoveTool(stringMetadata) == claim.id.toString()) {
                return true
            }
        }

        return false
    }

    override fun isClaimTool(itemData: Map<String, String>?): Boolean {
        return itemData?.get(CLAIM_TOOL_KEY) == "true"
    }

    override fun isMoveTool(itemData: Map<String, String>?): Boolean {
        return itemData?.get(MOVE_TOOL_KEY) == "true"
    }

    override fun getClaimIdFromPlayerMoveTool(itemData: Map<String, String>?): String? {
        return itemData?.get(CLAIM_ID_KEY)
    }
}
