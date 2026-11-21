package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.core.NexusContext

/** Lazy event bindings only; no Bukkit registration or scheduler activation occurs here. */
internal fun NexusContext.registerInteractionListenerBindings(claimsEnabled: Boolean) {
    registerBean("combatAntiGriefListener", net.lumalyte.lg.infrastructure.listeners.CombatAntiGriefListener::class) { nexusCombatAntiGriefListener() }
    registerBean("playerSessionListener", net.lumalyte.lg.infrastructure.listeners.PlayerSessionListener::class) { nexusPlayerSessionListener() }
    registerBean("vaultProtectionListener", net.lumalyte.lg.infrastructure.listeners.VaultProtectionListener::class) { nexusVaultProtectionListener() }
    registerBean("warKillTrackingListener", net.lumalyte.lg.infrastructure.listeners.WarKillTrackingListener::class) { nexusWarKillTrackingListener() }
    registerBean("bannerSelectionListener", net.lumalyte.lg.interaction.listeners.BannerSelectionListener::class) { nexusBannerSelectionListener() }
    registerBean("partyChatListener", net.lumalyte.lg.interaction.listeners.PartyChatListener::class) { nexusPartyChatListener() }
    if (claimsEnabled) {
        registerBean("claimAnchorListener", net.lumalyte.lg.interaction.listeners.ClaimAnchorListener::class) { nexusClaimAnchorListener() }
        registerBean("claimDestructionListener", net.lumalyte.lg.interaction.listeners.ClaimDestructionListener::class) { nexusClaimDestructionListener() }
        registerBean("claimPvpProtectionListener", net.lumalyte.lg.interaction.listeners.ClaimPvpProtectionListener::class) { nexusClaimPvpProtectionListener() }
        registerBean("closeInventoryListener", net.lumalyte.lg.interaction.listeners.CloseInventoryListener::class) { nexusCloseInventoryListener() }
        registerBean("editToolListener", net.lumalyte.lg.interaction.listeners.EditToolListener::class) { nexusEditToolListener() }
        registerBean("harvestReplantListener", net.lumalyte.lg.interaction.listeners.HarvestReplantListener::class) { nexusHarvestReplantListener() }
        registerBean("moveToolListener", net.lumalyte.lg.interaction.listeners.MoveToolListener::class) { nexusMoveToolListener() }
        registerBean("partitionUpdateListener", net.lumalyte.lg.interaction.listeners.PartitionUpdateListener::class) { nexusPartitionUpdateListener() }
        registerBean("playerClaimProtectionListener", net.lumalyte.lg.interaction.listeners.PlayerClaimProtectionListener::class) { nexusPlayerClaimProtectionListener() }
        registerBean("toolRemovalListener", net.lumalyte.lg.interaction.listeners.ToolRemovalListener::class) { nexusToolRemovalListener() }
        registerBean("worldClaimProtectionListener", net.lumalyte.lg.interaction.listeners.WorldClaimProtectionListener::class) { nexusWorldClaimProtectionListener() }
        registerBean("editToolVisualisingListener", net.lumalyte.lg.interaction.listeners.EditToolVisualisingListener::class) {
            net.lumalyte.lg.interaction.listeners.EditToolVisualisingListener(
                getBean<net.lumalyte.lg.LumaGuilds>(),
                lazy { getBean<net.lumalyte.lg.application.actions.player.tool.SyncToolVisualization>() },
            )
        }
    }
}
