package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarBannerPlacementResult
import net.lumalyte.lg.application.services.WarBannerService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.domain.entities.WarBannerState
import net.lumalyte.lg.infrastructure.services.WarBannerServiceBukkit
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.block.Banner
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

class WarBannerListener(
    private val core: WarBannerService,
    private val bukkit: WarBannerServiceBukkit,
    private val guildService: GuildService,
    private val lang: LangService,
) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val guildId = bukkit.itemGuildId(event.itemInHand) ?: return
        val player = event.player
        val guild = guildService.getGuild(guildId)
        if (guild == null) {
            event.isCancelled = true
            player.sendMessage(lang.msg("war_banner.feedback.guild_missing"))
            return
        }

        bukkit.cleanupForPlacement(guildId)
        val block = event.blockPlaced
        val result = core.place(
            transactionId = UUID.randomUUID(),
            playerId = player.uniqueId,
            guildId = guildId,
            worldId = block.world.uid,
            x = block.x,
            y = block.y,
            z = block.z,
            now = System.currentTimeMillis(),
        ) { state ->
            bukkit.renderPlaced(block, guild, state)
        }

        when (result) {
            is WarBannerPlacementResult.Placed -> {
                player.sendMessage(lang.msg(
                    "war_banner.feedback.placed",
                    "cost" to result.cost,
                ))
                broadcast(lang.msg("war_banner.broadcast.placed", "guild" to guild.name))
            }
            is WarBannerPlacementResult.PaymentUnknown -> {
                player.sendMessage(lang.msg("war_banner.feedback.payment_unknown"))
                broadcast(lang.msg("war_banner.broadcast.placed", "guild" to guild.name))
            }
            is WarBannerPlacementResult.Cooldown -> reject(
                event,
                lang.msg(
                    "war_banner.feedback.cooldown",
                    "seconds" to ((result.remainingMillis + 999L) / 1000L),
                ),
            )
            WarBannerPlacementResult.ActiveBanner ->
                reject(event, lang.msg("war_banner.feedback.already_active"))
            WarBannerPlacementResult.NotMember ->
                reject(event, lang.msg("war_banner.feedback.not_member"))
            WarBannerPlacementResult.NoPermission ->
                reject(event, lang.msg("war_banner.feedback.no_permission"))
            WarBannerPlacementResult.NoActiveWar ->
                reject(event, lang.msg("war_banner.feedback.no_active_war"))
            WarBannerPlacementResult.InsufficientGold ->
                reject(event, lang.msg("war_banner.feedback.insufficient_gold"))
            WarBannerPlacementResult.PaymentUnavailable ->
                reject(event, lang.msg("war_banner.feedback.payment_unavailable"))
            WarBannerPlacementResult.ConfigurationError ->
                reject(event, lang.msg("war_banner.feedback.configuration_error"))
            is WarBannerPlacementResult.Failed -> {
                if (result.compensated) {
                    reject(event, lang.msg("war_banner.feedback.failed_refunded"))
                } else {
                    reject(event, lang.msg("war_banner.feedback.failed_uncertain"))
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBreak(event: BlockBreakEvent) {
        val banner = event.block.state as? Banner ?: return
        if (!banner.persistentDataContainer.has(
                PluginKeys.WAR_BANNER_ID,
                PersistentDataType.STRING,
            )
        ) return

        event.isCancelled = false
        event.isDropItems = false
        val state = core.destroyAt(
            event.block.world.uid,
            event.block.x,
            event.block.y,
            event.block.z,
        )
        state?.let(::broadcastDestroyed)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().forEach(::destroyExploded)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().forEach(::destroyExploded)
    }

    private fun destroyExploded(block: org.bukkit.block.Block) {
        val banner = block.state as? Banner ?: return
        if (!banner.persistentDataContainer.has(
                PluginKeys.WAR_BANNER_ID,
                PersistentDataType.STRING,
            )
        ) return
        core.destroyAt(block.world.uid, block.x, block.y, block.z)
            ?.let(::broadcastDestroyed)
    }

    private fun reject(event: BlockPlaceEvent, message: Component) {
        event.isCancelled = true
        event.player.sendMessage(message)
    }

    private fun broadcastDestroyed(state: WarBannerState) {
        val guildName = guildService.getGuild(state.guildId)?.name
            ?: state.guildId.toString().take(8)
        broadcast(lang.msg("war_banner.broadcast.destroyed", "guild" to guildName))
    }

    private fun broadcast(message: Component) {
        Bukkit.getOnlinePlayers().forEach { it.sendMessage(message) }
        Bukkit.getConsoleSender().sendMessage(message)
    }
}
