package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.WarBannerService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.WarBannerState
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.utils.GuildHomeSafety
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Banner
import org.bukkit.block.Block
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Rotatable
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID

enum class WarBannerTeleportResult {
    STARTED,
    NO_ACTIVE_BANNER,
    BANNER_MISSING,
    WORLD_UNAVAILABLE,
    UNSAFE,
}

class WarBannerServiceBukkit(
    private val plugin: Plugin,
    private val core: WarBannerService,
    private val configService: ConfigService,
    private val teleportationService: TeleportationService,
    private val lang: LangService,
) {
    init {
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                core.expireDue(System.currentTimeMillis()).forEach(::removePhysical)
            },
            20L,
            100L,
        )
    }

    fun createDeployableItem(guild: Guild): ItemStack {
        val item = currentGuildBanner(guild)
        item.amount = 1
        item.name(lang.gui("war_banner.item.name", "guild" to guild.name))
        item.lore(lang.gui(
            "war_banner.item.lore.cost",
            "cost" to configService.loadConfig().warBanner.rawGoldCost,
        ))
        item.lore(lang.gui("war_banner.item.lore.duration"))
        item.lore(lang.gui("war_banner.item.lore.place"))

        val meta = item.itemMeta
        meta.persistentDataContainer.set(
            PluginKeys.WAR_BANNER_ITEM,
            PersistentDataType.BYTE,
            1,
        )
        meta.persistentDataContainer.set(
            PluginKeys.WAR_BANNER_GUILD_ID,
            PersistentDataType.STRING,
            guild.id.toString(),
        )
        meta.persistentDataContainer.set(
            PluginKeys.GUILD_BANNER_MARKER,
            PersistentDataType.BYTE,
            1,
        )
        item.itemMeta = meta
        return item
    }

    fun itemGuildId(item: ItemStack?): UUID? {
        if (item == null || !item.type.name.endsWith("_BANNER")) return null
        val meta = item.itemMeta ?: return null
        if (!meta.persistentDataContainer.has(
                PluginKeys.WAR_BANNER_ITEM,
                PersistentDataType.BYTE,
            )
        ) return null
        val raw = meta.persistentDataContainer.get(
            PluginKeys.WAR_BANNER_GUILD_ID,
            PersistentDataType.STRING,
        ) ?: return null
        return runCatching { UUID.fromString(raw) }.getOrNull()
    }

    fun renderPlaced(
        block: Block,
        guild: Guild,
        state: WarBannerState,
    ): Boolean = runCatching {
        val desired = currentGuildBanner(guild)
        val desiredMeta = desired.itemMeta as? BannerMeta
            ?: return false
        val wall = block.type.name.endsWith("_WALL_BANNER")
        val base = desired.type.name.removeSuffix("_BANNER")
        val desiredType = Material.matchMaterial(
            if (wall) "${base}_WALL_BANNER" else "${base}_BANNER"
        ) ?: Material.WHITE_BANNER

        val previousData = block.blockData
        if (block.type != desiredType) {
            block.type = desiredType
            val newData = block.blockData
            when {
                previousData is Rotatable && newData is Rotatable -> {
                    newData.rotation = previousData.rotation
                    block.blockData = newData
                }
                previousData is Directional && newData is Directional -> {
                    newData.facing = previousData.facing
                    block.blockData = newData
                }
            }
        }

        val banner = block.state as? Banner ?: return false
        banner.patterns = desiredMeta.patterns
        banner.persistentDataContainer.set(
            PluginKeys.WAR_BANNER_GUILD_ID,
            PersistentDataType.STRING,
            guild.id.toString(),
        )
        banner.persistentDataContainer.set(
            PluginKeys.WAR_BANNER_ID,
            PersistentDataType.STRING,
            state.bannerId.toString(),
        )
        banner.update(true, false)
        true
    }.getOrDefault(false)

    fun isPhysical(state: WarBannerState): Boolean {
        val world = Bukkit.getWorld(state.worldId) ?: return false
        val banner = world.getBlockAt(state.x, state.y, state.z).state as? Banner
            ?: return false
        return banner.persistentDataContainer.get(
            PluginKeys.WAR_BANNER_ID,
            PersistentDataType.STRING,
        ) == state.bannerId.toString()
    }

    fun cleanupForPlacement(guildId: UUID, now: Long = System.currentTimeMillis()) {
        val state = core.state(guildId) ?: return
        if (!state.active) return
        if (state.expiresAt <= now) {
            removePhysical(state)
            core.destroyAt(state.worldId, state.x, state.y, state.z)
            return
        }
        if (!isPhysical(state)) {
            core.destroyAt(state.worldId, state.x, state.y, state.z)
        }
    }

    fun teleport(
        player: Player,
        guildId: UUID,
        now: Long = System.currentTimeMillis(),
    ): WarBannerTeleportResult {
        val state = core.activeForMember(player.uniqueId, guildId, now)
            ?: return WarBannerTeleportResult.NO_ACTIVE_BANNER
        val world = Bukkit.getWorld(state.worldId)
            ?: return WarBannerTeleportResult.WORLD_UNAVAILABLE
        if (!isPhysical(state)) {
            core.destroyAt(state.worldId, state.x, state.y, state.z)
            return WarBannerTeleportResult.BANNER_MISSING
        }

        val target = Location(
            world,
            state.x + 0.5,
            state.y.toDouble(),
            state.z + 0.5,
            player.location.yaw,
            player.location.pitch,
        )
        if (!GuildHomeSafety.evaluateSafety(target).safe) {
            return WarBannerTeleportResult.UNSAFE
        }
        teleportationService.startTeleport(player, target)
        return WarBannerTeleportResult.STARTED
    }

    fun removePhysical(state: WarBannerState) {
        val world = Bukkit.getWorld(state.worldId) ?: return
        val block = world.getBlockAt(state.x, state.y, state.z)
        val banner = block.state as? Banner ?: return
        val id = banner.persistentDataContainer.get(
            PluginKeys.WAR_BANNER_ID,
            PersistentDataType.STRING,
        )
        if (id == state.bannerId.toString()) {
            block.type = Material.AIR
        }
    }

    private fun currentGuildBanner(guild: Guild): ItemStack {
        val stored = guild.banner?.let { encoded ->
            runCatching { encoded.deserializeToItemStack() }.getOrNull()
        }
        val banner = stored?.takeIf {
            it.type.name.endsWith("_BANNER") &&
                !it.type.name.endsWith("_WALL_BANNER")
        }
        return banner?.clone() ?: ItemStack(Material.WHITE_BANNER)
    }
}
