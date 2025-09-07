package net.lumalyte.lg.application.actions.claim

import net.lumalyte.lg.application.persistence.ClaimPermissionRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.results.claim.IsPlayerActionAllowedResult
import net.lumalyte.lg.application.services.GuildRolePermissionResolver

import net.lumalyte.lg.domain.values.ClaimPermission
import net.lumalyte.lg.domain.values.PlayerActionType
import net.lumalyte.lg.domain.values.Position2D
import java.util.UUID

class IsPlayerActionAllowed(private val playerAccessRepository: PlayerAccessRepository,
                            private val claimPermissionRepository: ClaimPermissionRepository,
                            private val getClaimAtPosition: GetClaimAtPosition,
                            private val playerStateRepository: PlayerStateRepository,
                            private val guildRolePermissionResolver: GuildRolePermissionResolver) {
    fun execute(playerId: UUID, worldId: UUID, position: Position2D,
                playerActionType: PlayerActionType): IsPlayerActionAllowedResult {
        // Get the claim at the current position
        val claim = when (val result = getClaimAtPosition.execute(worldId, position)) {
            GetClaimAtPositionResult.NoClaimFound -> return IsPlayerActionAllowedResult.NoClaimFound
            GetClaimAtPositionResult.StorageError -> return IsPlayerActionAllowedResult.StorageError
            is GetClaimAtPositionResult.Success -> result.claim
        }

        // Always allow if player owns the claim or has override
        val playerState = playerStateRepository.get(playerId)
        if (playerId == claim.playerId || (playerState != null && playerState.claimOverride))
            return IsPlayerActionAllowedResult.Allowed(claim)

        // Get the flag associated with the action
        val relevantPermission = actionToPermissionMapping[playerActionType]
            ?: return IsPlayerActionAllowedResult.NoAssociatedPermission

        // Check guild role permissions first
        if (guildRolePermissionResolver.hasPermission(playerId, claim.id, relevantPermission)) {
            return IsPlayerActionAllowedResult.Allowed(claim)
        }
        
        // Fallback to individual and claim-wide permissions
        if (playerAccessRepository.doesPlayerHavePermission(playerId, claim.id, relevantPermission)
                || claimPermissionRepository.doesClaimHavePermission(claim.id, relevantPermission)) {
            return IsPlayerActionAllowedResult.Allowed(claim)
        }
        
        return IsPlayerActionAllowedResult.Denied(claim)
    }

    private val actionToPermissionMapping: Map<PlayerActionType, ClaimPermission> = mapOf(
        // Build
        PlayerActionType.BREAK_BLOCK to ClaimPermission.BUILD,
        PlayerActionType.PLACE_BLOCK to ClaimPermission.BUILD,
        PlayerActionType.PLACE_FLUID to ClaimPermission.BUILD,
        PlayerActionType.PLACE_ENTITY to ClaimPermission.BUILD,
        PlayerActionType.DAMAGE_STATIC_ENTITY to ClaimPermission.BUILD,
        PlayerActionType.FERTILIZE_LAND to ClaimPermission.BUILD,
        PlayerActionType.STEP_ON_FARMLAND to ClaimPermission.BUILD,
        PlayerActionType.TELEPORT_DRAGON_EGG to ClaimPermission.BUILD,
        PlayerActionType.FILL_BUCKET to ClaimPermission.BUILD,
        PlayerActionType.SHEAR_PUMPKIN to ClaimPermission.BUILD,
        PlayerActionType.BREAK_POT to ClaimPermission.BUILD,
        PlayerActionType.PUSH_ARMOUR_STAND to ClaimPermission.BUILD,

        // Harvest
        PlayerActionType.HARVEST_CROP to ClaimPermission.HARVEST,
        PlayerActionType.FERTILIZE_CROP to ClaimPermission.HARVEST,

        // Containers
        PlayerActionType.OPEN_CONTAINER to ClaimPermission.CONTAINER,

        // Manipulate
        PlayerActionType.TAKE_LECTERN_BOOK to ClaimPermission.DISPLAY,
        PlayerActionType.MODIFY_BLOCK to ClaimPermission.DISPLAY,
        PlayerActionType.MODIFY_STATIC_ENTITY to ClaimPermission.DISPLAY,

        // Vehicles
        PlayerActionType.PLACE_VEHICLE to ClaimPermission.VEHICLE,
        PlayerActionType.DESTROY_VEHICLE to ClaimPermission.VEHICLE,

        // Signs
        PlayerActionType.EDIT_SIGN to ClaimPermission.SIGN,
        PlayerActionType.DYE_SIGN to ClaimPermission.SIGN,

        // Redstone
        PlayerActionType.USE_REDSTONE to ClaimPermission.REDSTONE,

        // Doors
        PlayerActionType.OPEN_DOOR to ClaimPermission.DOOR,

        // Trading
        PlayerActionType.TRADE_VILLAGER to ClaimPermission.TRADE,

        // Husbandry
        PlayerActionType.DAMAGE_ANIMAL to ClaimPermission.HUSBANDRY,
        PlayerActionType.INTERACT_WITH_ANIMAL to ClaimPermission.HUSBANDRY,
        PlayerActionType.ROD_ANIMAL to ClaimPermission.HUSBANDRY,
        PlayerActionType.DETACH_LEAD to ClaimPermission.HUSBANDRY,
        PlayerActionType.USE_BEEHIVE to ClaimPermission.HUSBANDRY,
        PlayerActionType.POTION_ANIMAL to ClaimPermission.HUSBANDRY,
        PlayerActionType.PUSH_ANIMAL to ClaimPermission.HUSBANDRY,

        // Detonate
        PlayerActionType.PRIME_TNT to ClaimPermission.DETONATE,
        PlayerActionType.DETONATE_ENTITY to ClaimPermission.DETONATE,
        PlayerActionType.DETONATE_BLOCK to ClaimPermission.DETONATE,
        PlayerActionType.TRIGGER_RAID to ClaimPermission.DETONATE,
        PlayerActionType.SLEEP_IN_BED to ClaimPermission.DETONATE,
        PlayerActionType.SET_RESPAWN_POINT to ClaimPermission.DETONATE,

        // Event
        PlayerActionType.TRIGGER_RAID to ClaimPermission.EVENT,

        // Sleep
        PlayerActionType.SLEEP_IN_BED to ClaimPermission.SLEEP,
        PlayerActionType.SET_RESPAWN_POINT to ClaimPermission.SLEEP,

        // View
        PlayerActionType.VIEW_LECTERN_BOOK to ClaimPermission.VIEW
    )
}
