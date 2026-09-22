package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.api.events.GuildBannerSetEvent
import net.lumalyte.lg.api.events.GuildLeaderboardRankChangeEvent
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.infrastructure.services.SpawnBannerServiceBukkit
import org.bukkit.block.Banner
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.persistence.PersistentDataType

class SpawnBannerListener(
    private val service: SpawnBannerServiceBukkit,
    private val lang: LangService,
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val (rank, category) = service.itemBinding(event.itemInHand) ?: return
        if (!event.player.isOp && !event.player.hasPermission("bellclaims.admin")) {
            event.isCancelled = true
            event.player.sendMessage(lang.msg("spawn_banner.feedback.no_permission"))
            return
        }

        val state = service.register(event.blockPlaced, rank, category)
        if (state == null) {
            event.isCancelled = true
            event.player.sendMessage(lang.msg("spawn_banner.feedback.place_failed"))
            return
        }
        event.player.sendMessage(
            lang.msg(
                "spawn_banner.feedback.placed",
                "rank" to rank,
                "category" to category.commandName,
            )
        )
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        val banner = event.block.state as? Banner ?: return
        if (!banner.persistentDataContainer.has(
                PluginKeys.SPAWN_BANNER_ID,
                PersistentDataType.STRING,
            )
        ) return
        if (!event.player.isOp && !event.player.hasPermission("bellclaims.admin")) {
            event.isCancelled = true
            event.player.sendMessage(lang.msg("spawn_banner.feedback.no_permission"))
            return
        }

        val state = service.removeAt(event.block)
        if (state == null) {
            event.isCancelled = true
            event.player.sendMessage(lang.msg("spawn_banner.feedback.remove_failed"))
            return
        }
        event.isDropItems = false
        event.block.world.dropItemNaturally(
            event.block.location,
            service.configuredDrop(state),
        )
        event.player.sendMessage(lang.msg("spawn_banner.feedback.removed"))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLeaderboardRankChange(event: GuildLeaderboardRankChangeEvent) {
        service.refreshLeaderboard(event.leaderboardType, event.period)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGuildBannerSet(event: GuildBannerSetEvent) {
        service.refreshGuild(event.guildId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().forEach(::removeExploded)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().forEach(::removeExploded)
    }

    private fun removeExploded(block: org.bukkit.block.Block) {
        val banner = block.state as? Banner ?: return
        if (!banner.persistentDataContainer.has(
                PluginKeys.SPAWN_BANNER_ID,
                PersistentDataType.STRING,
            )
        ) return
        service.removeAt(block)
    }
}
