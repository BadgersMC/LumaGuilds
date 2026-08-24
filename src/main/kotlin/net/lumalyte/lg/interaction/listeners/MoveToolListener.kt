package net.lumalyte.lg.interaction.listeners

import net.badgersmc.nexus.i18n.LangService

import net.lumalyte.lg.application.actions.claim.anchor.MoveClaimAnchor
import net.lumalyte.lg.application.actions.player.tool.GetClaimIdFromMoveTool
import net.lumalyte.lg.application.results.claim.anchor.MoveClaimAnchorResult
import net.lumalyte.lg.application.results.player.tool.GetClaimIdFromMoveToolResult
import net.lumalyte.lg.infrastructure.adapters.bukkit.toCustomItemData
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.kyori.adventure.text.format.TextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class MoveToolListener: Listener, KoinComponent {
    private val lang: LangService by inject()
    private val moveClaimAnchor: MoveClaimAnchor by inject()
    private val getClaimIdFromMoveTool: GetClaimIdFromMoveTool by inject()

    @EventHandler
    fun onClaimMoveBlockPlace(event: BlockPlaceEvent) {
        // Get the claim id from the move tool
        val claimId: UUID
        val result = getClaimIdFromMoveTool.execute(event.itemInHand.toCustomItemData())
        when (result) {
            GetClaimIdFromMoveToolResult.NotMoveTool -> return
            is GetClaimIdFromMoveToolResult.Success -> claimId = result.claimId
        }

        // Perform action to move the claim bell
        val playerId = event.player.uniqueId
        when (moveClaimAnchor.execute(
            claimId, event.player.uniqueId,
            event.blockPlaced.world.uid, event.blockPlaced.location.toPosition3D())) {
            is MoveClaimAnchorResult.Success -> {
                event.player.sendActionBar(
                    lang.msg("feedback.move_tool.success")
                        .color(TextColor.color(85, 255, 85)))
            }
            is MoveClaimAnchorResult.InvalidPosition -> {
                event.player.sendActionBar(
                    lang.msg("feedback.move_tool.outside_border")
                        .color(TextColor.color(255, 85, 85)))
                event.isCancelled = true
            }
            is MoveClaimAnchorResult.NoPermission -> {
                event.player.sendActionBar(
                    lang.msg("feedback.move_tool.no_permission")
                        .color(TextColor.color(255, 85, 85)))
                event.player.inventory.setItemInMainHand(ItemStack.empty())
                event.isCancelled = true
            }
            is MoveClaimAnchorResult.StorageError -> {
                event.player.sendActionBar(
                    lang.msg("general.error")
                        .color(TextColor.color(255, 85, 85)))
                event.player.inventory.setItemInMainHand(ItemStack.empty())
                event.isCancelled = true
            }
        }
    }
}
