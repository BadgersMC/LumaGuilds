package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.application.actions.claim.ConvertClaimToGuild
import net.lumalyte.lg.application.actions.claim.CreateClaim
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.actions.claim.IsNewClaimLocationValid
import net.lumalyte.lg.application.actions.claim.IsPlayerActionAllowed
import net.lumalyte.lg.application.actions.claim.IsWorldActionAllowed
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import net.lumalyte.lg.application.actions.claim.anchor.BreakClaimAnchor
import net.lumalyte.lg.application.actions.claim.anchor.GetClaimAnchorAtPosition
import net.lumalyte.lg.application.actions.claim.anchor.MoveClaimAnchor
import net.lumalyte.lg.application.actions.claim.flag.DisableAllClaimFlags
import net.lumalyte.lg.application.actions.claim.flag.DisableClaimFlag
import net.lumalyte.lg.application.actions.claim.flag.DoesClaimHaveFlag
import net.lumalyte.lg.application.actions.claim.flag.EnableAllClaimFlags
import net.lumalyte.lg.application.actions.claim.flag.EnableClaimFlag
import net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimBlockCount
import net.lumalyte.lg.application.actions.claim.metadata.GetClaimDetails
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimDescription
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimIcon
import net.lumalyte.lg.application.actions.claim.metadata.UpdateClaimName
import net.lumalyte.lg.application.actions.claim.partition.CanRemovePartition
import net.lumalyte.lg.application.actions.claim.partition.CreatePartition
import net.lumalyte.lg.application.actions.claim.partition.GetClaimPartitions
import net.lumalyte.lg.application.actions.claim.partition.GetPartitionByPosition
import net.lumalyte.lg.application.actions.claim.partition.RemovePartition
import net.lumalyte.lg.application.actions.claim.partition.ResizePartition
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetClaimPlayerPermissions
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.application.actions.claim.permission.GrantAllClaimWidePermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantAllPlayerClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantClaimWidePermission
import net.lumalyte.lg.application.actions.claim.permission.GrantGuildMembersClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.GrantPlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.permission.RevokeAllClaimWidePermissions
import net.lumalyte.lg.application.actions.claim.permission.RevokeAllPlayerClaimPermissions
import net.lumalyte.lg.application.actions.claim.permission.RevokeClaimWidePermission
import net.lumalyte.lg.application.actions.claim.permission.RevokePlayerClaimPermission
import net.lumalyte.lg.application.actions.claim.transfer.AcceptTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.CanPlayerReceiveTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.DoesPlayerHaveTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.OfferPlayerTransferRequest
import net.lumalyte.lg.application.actions.claim.transfer.WithdrawPlayerTransferRequest
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.actions.player.GetRemainingClaimBlockCount
import net.lumalyte.lg.application.actions.player.IsPlayerInClaimMenu
import net.lumalyte.lg.application.actions.player.RegisterClaimMenuOpening
import net.lumalyte.lg.application.actions.player.ToggleClaimOverride
import net.lumalyte.lg.application.actions.player.UnregisterClaimMenuOpening
import net.lumalyte.lg.application.actions.player.tool.GetClaimIdFromMoveTool
import net.lumalyte.lg.application.actions.player.tool.GivePlayerClaimTool
import net.lumalyte.lg.application.actions.player.tool.GivePlayerMoveTool
import net.lumalyte.lg.application.actions.player.tool.IsItemClaimTool
import net.lumalyte.lg.application.actions.player.tool.IsItemMoveTool
import net.lumalyte.lg.application.actions.player.tool.SyncToolVisualization
import net.lumalyte.lg.application.actions.player.visualisation.ClearSelectionVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.ClearVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.DisplaySelectionVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.DisplayVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.GetVisualisedClaimBlocks
import net.lumalyte.lg.application.actions.player.visualisation.GetVisualiserMode
import net.lumalyte.lg.application.actions.player.visualisation.IsPlayerVisualising
import net.lumalyte.lg.application.actions.player.visualisation.RefreshVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.ScheduleClearVisualisation
import net.lumalyte.lg.application.actions.player.visualisation.ToggleVisualiserMode
import net.lumalyte.lg.application.persistence.ClaimFlagRepository
import net.lumalyte.lg.application.persistence.ClaimPermissionRepository
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.ClaimTransferRequestRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.persistence.PlayerAccessRepository
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.application.services.ClaimManagementAuthorizer
import net.lumalyte.lg.application.services.ToolItemService
import net.lumalyte.lg.application.services.VisualisationPerformanceService
import net.lumalyte.lg.application.services.VisualisationService
import net.lumalyte.lg.application.services.WorldManipulationService
import net.lumalyte.lg.application.services.scheduling.SchedulerService
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimFlagRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimPermissionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.claims.ClaimTransferRequestRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.claims.PlayerAccessRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.partitions.PartitionRepositorySQLite
import net.lumalyte.lg.infrastructure.persistence.players.PlayerStateRepositoryMemory
import net.lumalyte.lg.infrastructure.services.ToolItemServiceBukkit
import net.lumalyte.lg.infrastructure.services.VisualisationPerformanceServiceBukkit
import net.lumalyte.lg.infrastructure.services.VisualisationServiceBukkit
import net.lumalyte.lg.infrastructure.services.WorldManipulationServiceBukkit
import net.lumalyte.lg.infrastructure.services.scheduling.SchedulerServiceBukkit
import net.lumalyte.lg.interaction.listeners.AdminOverrideListener
import kotlin.reflect.KClass

/**
 * Nexus replacement for the legacy claimsModule Koin graph.
 * Its caller decides if claims are enabled. Definitions are lazy to avoid database
 * access before the storage bootstrap/migrations have completed.
 */
internal fun NexusContext.registerClaimBindings() {
    // Repositories have interface contracts; register by contract, not by implementation.
    claimSingleton<ClaimFlagRepository> { ClaimFlagRepositorySQLite(getBean()) }
    claimSingleton<ClaimPermissionRepository> { ClaimPermissionRepositorySQLite(getBean()) }
    claimSingleton<ClaimRepository> { ClaimRepositorySQLite(getBean()) }
    claimSingleton<ClaimTransferRequestRepository> { ClaimTransferRequestRepositorySQL(getBean()) }
    claimSingleton<PartitionRepository> { PartitionRepositorySQLite(getBean()) }
    claimSingleton<PlayerAccessRepository> { PlayerAccessRepositorySQLite(getBean()) }
    claimSingleton<PlayerStateRepository> { PlayerStateRepositoryMemory() }

    claimSingleton<VisualisationService> { VisualisationServiceBukkit() }
    claimSingleton<VisualisationPerformanceService> { VisualisationPerformanceServiceBukkit() }
    claimSingleton<WorldManipulationService> { WorldManipulationServiceBukkit() }
    claimSingleton<SchedulerService> { SchedulerServiceBukkit(getBean()) }
    claimSingleton<ToolItemService> { ToolItemServiceBukkit(getBean(), getBean()) }
    constructClaimSingleton<ClaimManagementAuthorizer>()

    constructClaimSingleton<CreateClaim>()
    claimSingleton<ConvertClaimToGuild> { ConvertClaimToGuild(getBean(), getBean(), getBean()) }
    constructClaimSingleton<GetClaimAtPosition>()
    constructClaimSingleton<IsNewClaimLocationValid>()
    constructClaimSingleton<IsPlayerActionAllowed>()
    constructClaimSingleton<IsWorldActionAllowed>()
    constructClaimSingleton<ListPlayerClaims>()

    constructClaimSingleton<BreakClaimAnchor>()
    constructClaimSingleton<GetClaimAnchorAtPosition>()
    constructClaimSingleton<MoveClaimAnchor>()

    constructClaimSingleton<DisableAllClaimFlags>()
    constructClaimSingleton<DisableClaimFlag>()
    constructClaimSingleton<DoesClaimHaveFlag>()
    constructClaimSingleton<EnableAllClaimFlags>()
    constructClaimSingleton<EnableClaimFlag>()
    constructClaimSingleton<GetClaimFlags>()

    constructClaimSingleton<GetClaimBlockCount>()
    constructClaimSingleton<GetClaimDetails>()
    constructClaimSingleton<UpdateClaimDescription>()
    constructClaimSingleton<UpdateClaimIcon>()
    constructClaimSingleton<UpdateClaimName>()

    constructClaimSingleton<CanRemovePartition>()
    constructClaimSingleton<CreatePartition>()
    constructClaimSingleton<GetClaimPartitions>()
    constructClaimSingleton<GetPartitionByPosition>()
    constructClaimSingleton<RemovePartition>()
    constructClaimSingleton<ResizePartition>()

    constructClaimSingleton<GetClaimPermissions>()
    constructClaimSingleton<GetClaimPlayerPermissions>()
    constructClaimSingleton<GetPlayersWithPermissionInClaim>()
    constructClaimSingleton<GrantAllClaimWidePermissions>()
    constructClaimSingleton<GrantAllPlayerClaimPermissions>()
    constructClaimSingleton<GrantClaimWidePermission>()
    constructClaimSingleton<GrantGuildMembersClaimPermissions>()
    constructClaimSingleton<GrantPlayerClaimPermission>()
    constructClaimSingleton<RevokeAllClaimWidePermissions>()
    constructClaimSingleton<RevokeAllPlayerClaimPermissions>()
    constructClaimSingleton<RevokeClaimWidePermission>()
    constructClaimSingleton<RevokePlayerClaimPermission>()

    constructClaimSingleton<AcceptTransferRequest>()
    constructClaimSingleton<CanPlayerReceiveTransferRequest>()
    constructClaimSingleton<DoesPlayerHaveTransferRequest>()
    constructClaimSingleton<OfferPlayerTransferRequest>()
    constructClaimSingleton<WithdrawPlayerTransferRequest>()

    constructClaimSingleton<DoesPlayerHaveClaimOverride>()
    constructClaimSingleton<GetRemainingClaimBlockCount>()
    constructClaimSingleton<IsPlayerInClaimMenu>()
    constructClaimSingleton<RegisterClaimMenuOpening>()
    constructClaimSingleton<UnregisterClaimMenuOpening>()
    constructClaimSingleton<ToggleClaimOverride>()

    constructClaimSingleton<GetClaimIdFromMoveTool>()
    constructClaimSingleton<GivePlayerClaimTool>()
    constructClaimSingleton<GivePlayerMoveTool>()
    constructClaimSingleton<IsItemClaimTool>()
    constructClaimSingleton<IsItemMoveTool>()
    constructClaimSingleton<SyncToolVisualization>()

    constructClaimSingleton<ClearSelectionVisualisation>()
    constructClaimSingleton<ClearVisualisation>()
    constructClaimSingleton<DisplaySelectionVisualisation>()
    constructClaimSingleton<DisplayVisualisation>()
    constructClaimSingleton<GetVisualisedClaimBlocks>()
    constructClaimSingleton<GetVisualiserMode>()
    constructClaimSingleton<IsPlayerVisualising>()
    constructClaimSingleton<RefreshVisualisation>()
    constructClaimSingleton<ScheduleClearVisualisation>()
    constructClaimSingleton<ToggleVisualiserMode>()

    claimSingleton { AdminOverrideListener(getBean(), getBean()) }
}

private inline fun <reified T : Any> NexusContext.claimSingleton(noinline factory: () -> T) {
    registerBean(beanName(T::class), T::class, ScopeType.SINGLETON, factory)
}

private inline fun <reified T : Any> NexusContext.constructClaimSingleton() {
    claimSingleton(getBeanFactory().createFactory(T::class))
}

private fun beanName(type: KClass<*>): String =
    requireNotNull(type.simpleName) { "Anonymous classes cannot be Nexus beans" }
        .replaceFirstChar { it.lowercase() }
