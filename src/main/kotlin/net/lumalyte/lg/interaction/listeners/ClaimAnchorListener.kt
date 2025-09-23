package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.actions.claim.transfer.DoesPlayerHaveTransferRequest
import net.lumalyte.lg.application.actions.claim.anchor.GetClaimAnchorAtPosition
import net.lumalyte.lg.application.actions.claim.CreateClaim
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.results.claim.transfer.DoesPlayerHaveTransferRequestResult
import net.lumalyte.lg.application.results.claim.anchor.GetClaimAnchorAtPositionResult
import net.lumalyte.lg.application.results.player.DoesPlayerHaveClaimOverrideResult
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.management.ClaimCreationMenu
import net.lumalyte.lg.interaction.menus.management.ClaimManagementMenu
import net.lumalyte.lg.interaction.menus.management.ClaimTransferMenu
import org.bukkit.Bukkit
import org.bukkit.inventory.EquipmentSlot
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClaimAnchorListener(): Listener, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val getClaimAnchorAtPosition: GetClaimAnchorAtPosition by inject()
    private val createClaim: CreateClaim by inject()
    private val doesPlayerHaveTransferRequest: DoesPlayerHaveTransferRequest by inject()
    private val doesPlayerHaveClaimOverride: DoesPlayerHaveClaimOverride by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    @EventHandler
    fun onPlayerClaimHubInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!event.player.isSneaking) return
        val clickedBlock = event.clickedBlock ?: return
        if ((clickedBlock.type) != Material.BELL) return
        if (!event.player.hasPermission("bellclaims.action.bell")) return

        // Get the claim if it exists at the clicked location
        val playerId = event.player.uniqueId
        var claim: Claim? = null
        val claimResult = getClaimAnchorAtPosition.execute(clickedBlock.location.toPosition3D(), clickedBlock.world.uid)
        when (claimResult) {
            is GetClaimAnchorAtPositionResult.Success -> claim = claimResult.claim
            is GetClaimAnchorAtPositionResult.NoClaimAnchorFound -> {}
            is GetClaimAnchorAtPositionResult.StorageError -> {
                event.player.sendMessage(localizationProvider.get(playerId, LocalizationKeys.GENERAL_ERROR))
                return
            }
        }

        if (claim != null) {
            // Check if the player has an active claim transfer request
            var playerHasTransferRequest = false
            val transferResult = doesPlayerHaveTransferRequest.execute(claim.id, event.player.uniqueId)
            when (transferResult) {
                is DoesPlayerHaveTransferRequestResult.Success -> playerHasTransferRequest = transferResult.hasRequest
                else -> {
                    event.player.sendMessage(localizationProvider.get(playerId, LocalizationKeys.GENERAL_ERROR))
                }
            }

            // Get the player's claim override state
            val result = doesPlayerHaveClaimOverride.execute(event.player.uniqueId)
            val claimOverride = when (result) {
                DoesPlayerHaveClaimOverrideResult.StorageError -> false
                is DoesPlayerHaveClaimOverrideResult.Success -> result.hasOverride
            }

            // Notify no ability to interact with the claim without being owner or without an active transfer request
            if (claim.playerId != event.player.uniqueId && !playerHasTransferRequest && !claimOverride) {
                val playerName = Bukkit.getOfflinePlayer(claim.playerId).name ?: LocalizationKeys.GENERAL_NAME_ERROR
                event.player.sendActionBar(Component.text(
                    localizationProvider.get(playerId, LocalizationKeys.FEEDBACK_CLAIM_OWNER, playerName))
                    .color(TextColor.color(255, 85, 85)))
                return
            }

            // Open transfer request menu if pending
            if (playerHasTransferRequest) {
                val menuNavigator = MenuNavigator(event.player)
                menuNavigator.openMenu(menuFactory.createClaimTransferMenu(menuNavigator, claim, event.player))
                return
            }

            val menuNavigator = MenuNavigator(event.player)
            menuNavigator.openMenu(menuFactory.createClaimManagementMenu(menuNavigator, event.player, claim))
            return
        }

        // Open the menu
        event.isCancelled = true
        val menuNavigator = MenuNavigator(event.player)
        menuNavigator.openMenu(menuFactory.createClaimCreationMenu(event.player, menuNavigator, clickedBlock.location))
    }

    @EventHandler
    fun onBellPlaced(event: BlockPlaceEvent) {
        // Only handle bell placement
        if (event.block.type != Material.BELL) return

        val player = event.player
        val playerId = player.uniqueId
        val blockLocation = event.block.location

        // Check if player has permission to create claims
        if (!player.hasPermission("bellclaims.action.bell")) {
            player.sendMessage(Component.text("You don't have permission to create claims")
                .color(TextColor.color(255, 85, 85)))
            return
        }

        // Check if there's already a claim at this location
        val existingClaimResult = getClaimAnchorAtPosition.execute(blockLocation.toPosition3D(), blockLocation.world.uid)
        when (existingClaimResult) {
            is GetClaimAnchorAtPositionResult.Success -> {
                // There's already a claim here, let the player know
                player.sendMessage(Component.text("There's already a claim at this location")
                    .color(TextColor.color(255, 85, 85)))
                return
            }
            is GetClaimAnchorAtPositionResult.StorageError -> {
                player.sendMessage(Component.text("An error occurred while checking for existing claims")
                    .color(TextColor.color(255, 85, 85)))
                return
            }
            is GetClaimAnchorAtPositionResult.NoClaimAnchorFound -> {
                // No claim exists, proceed with creation
            }
        }

        // Create the claim automatically with a default name
        try {
            // Generate a default claim name
            val defaultName = "${player.name}'s Claim"
            val createResult = createClaim.execute(playerId, defaultName, blockLocation.toPosition3D(), blockLocation.world.uid)

            when (createResult) {
                is net.lumalyte.lg.application.results.claim.CreateClaimResult.Success -> {
                    val claim = createResult.claim
                    player.sendMessage(Component.text("Claim '${claim.name}' created successfully!")
                        .color(TextColor.color(85, 255, 85)))

                    // Log the successful claim creation
                    println("[DEBUG] ClaimAnchorListener: Successfully created claim ${claim.id} for player ${player.name} at ${blockLocation.x}, ${blockLocation.y}, ${blockLocation.z}")
                }
                is net.lumalyte.lg.application.results.claim.CreateClaimResult.NameCannotBeBlank -> {
                    player.sendMessage(Component.text("Claim name cannot be blank")
                        .color(TextColor.color(255, 85, 85)))
                    event.isCancelled = true
                }
                is net.lumalyte.lg.application.results.claim.CreateClaimResult.LimitExceeded -> {
                    player.sendMessage(Component.text("You have reached your claim limit")
                        .color(TextColor.color(255, 85, 85)))
                    event.isCancelled = true
                }
                is net.lumalyte.lg.application.results.claim.CreateClaimResult.NameAlreadyExists -> {
                    // Generate a unique name if the default name already exists
                    var counter = 1
                    var uniqueName = "${player.name}'s Claim $counter"
                    while (true) {
                        // This is a simplified check - in a real implementation you'd check the database
                        // For now, just append a number and hope for the best
                        break
                    }
                    val retryResult = createClaim.execute(playerId, uniqueName, blockLocation.toPosition3D(), blockLocation.world.uid)
                    if (retryResult is net.lumalyte.lg.application.results.claim.CreateClaimResult.Success) {
                        val claim = retryResult.claim
                        player.sendMessage(Component.text("Claim '${claim.name}' created successfully!")
                            .color(TextColor.color(85, 255, 85)))
                        println("[DEBUG] ClaimAnchorListener: Successfully created claim ${claim.id} with unique name")
                    } else {
                        player.sendMessage(Component.text("Failed to create claim - name already exists")
                            .color(TextColor.color(255, 85, 85)))
                        event.isCancelled = true
                    }
                }
                is net.lumalyte.lg.application.results.claim.CreateClaimResult.TooCloseToWorldBorder -> {
                    player.sendMessage(Component.text("Claim is too close to the world border")
                        .color(TextColor.color(255, 85, 85)))
                    event.isCancelled = true
                }
            }
        } catch (e: Exception) {
            println("[ERROR] ClaimAnchorListener: Failed to create claim for player ${player.name}: ${e.message}")
            e.printStackTrace()
            player.sendMessage(Component.text("An error occurred while creating your claim")
                .color(TextColor.color(255, 85, 85)))
            event.isCancelled = true
        }
    }
}
