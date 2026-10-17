package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.LeaderboardRepository
import net.lumalyte.lg.application.persistence.SpawnBannerRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.domain.entities.EntityType
import net.lumalyte.lg.domain.entities.ExtendedLeaderboardType
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.LeaderboardPeriod
import net.lumalyte.lg.domain.entities.SpawnBannerCategory
import net.lumalyte.lg.domain.entities.SpawnBannerState
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Banner
import org.bukkit.block.Block
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Rotatable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class SpawnBannerServiceBukkit(
    private val plugin: LumaGuilds,
    private val repository: SpawnBannerRepository,
    private val leaderboardRepository: LeaderboardRepository,
    private val guildService: GuildService,
    private val lang: LangService,
) {
    private var refreshTask: BukkitTask? = null

    fun start() {
        if (refreshTask != null) return
        plugin.server.scheduler.runTask(plugin, Runnable { refreshAll() })
        refreshTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable { refreshAll() },
            REFRESH_TICKS,
            REFRESH_TICKS,
        )
    }

    fun stop() {
        refreshTask?.cancel()
        refreshTask = null
    }

    fun createItem(rank: Int, category: SpawnBannerCategory): ItemStack {
        val item = ItemStack(Material.WHITE_BANNER)
        item.editMeta { meta ->
            meta.displayName(lang.msg("spawn_banner.item.name"))
            meta.lore(
                listOf(
                    lang.msg("spawn_banner.item.rank", "rank" to rank),
                    lang.msg("spawn_banner.item.category", "category" to category.commandName),
                    lang.msg("spawn_banner.item.instructions"),
                )
            )
            meta.persistentDataContainer.set(
                PluginKeys.SPAWN_BANNER_ITEM,
                PersistentDataType.BYTE,
                1,
            )
            meta.persistentDataContainer.set(
                PluginKeys.SPAWN_BANNER_RANK,
                PersistentDataType.INTEGER,
                rank,
            )
            meta.persistentDataContainer.set(
                PluginKeys.SPAWN_BANNER_CATEGORY,
                PersistentDataType.STRING,
                category.name,
            )
        }
        return item
    }

    fun itemBinding(item: ItemStack?): Pair<Int, SpawnBannerCategory>? {
        if (item == null || !item.type.name.endsWith("_BANNER")) return null
        val meta = item.itemMeta ?: return null
        if (!meta.persistentDataContainer.has(
                PluginKeys.SPAWN_BANNER_ITEM,
                PersistentDataType.BYTE,
            )
        ) return null
        val rank = meta.persistentDataContainer.get(
            PluginKeys.SPAWN_BANNER_RANK,
            PersistentDataType.INTEGER,
        ) ?: return null
        val categoryName = meta.persistentDataContainer.get(
            PluginKeys.SPAWN_BANNER_CATEGORY,
            PersistentDataType.STRING,
        ) ?: return null
        val category = runCatching { SpawnBannerCategory.valueOf(categoryName) }.getOrNull()
            ?: return null
        return rank to category
    }

    fun register(block: Block, rank: Int, category: SpawnBannerCategory): SpawnBannerState? {
        val state = SpawnBannerState(
            bannerId = UUID.randomUUID(),
            worldId = block.world.uid,
            x = block.x,
            y = block.y,
            z = block.z,
            rank = rank,
            category = category,
            createdAt = System.currentTimeMillis(),
        )
        if (!repository.save(state)) return null
        if (!renderPlaced(block, state)) {
            repository.deleteAt(state.worldId, state.x, state.y, state.z)
            return null
        }
        return state
    }

    fun removeAt(block: Block): SpawnBannerState? {
        val state = repository.getAt(block.world.uid, block.x, block.y, block.z)
            ?: return null
        return if (repository.deleteAt(state.worldId, state.x, state.y, state.z)) state else null
    }

    fun getAt(block: Block): SpawnBannerState? =
        repository.getAt(block.world.uid, block.x, block.y, block.z)

    fun all(): List<SpawnBannerState> = repository.getAll()

    fun configuredDrop(state: SpawnBannerState): ItemStack =
        createItem(state.rank, state.category)

    fun currentGuild(state: SpawnBannerState): Guild? = resolveGuild(state)

    fun refreshAll(): Int =
        repository.getAll().count { refresh(it) }

    fun refreshCategory(category: SpawnBannerCategory): Int =
        repository.getAll()
            .asSequence()
            .filter { it.category == category }
            .count { refresh(it) }

    fun refreshLeaderboard(
        type: ExtendedLeaderboardType,
        period: LeaderboardPeriod,
    ): Int = SpawnBannerCategory.entries
        .filter { it.leaderboardType == type && it.period == period }
        .sumOf(::refreshCategory)

    fun refreshGuild(guildId: UUID): Int =
        repository.getAll().count { state ->
            resolveGuild(state)?.id == guildId && refresh(state)
        }

    fun refresh(state: SpawnBannerState): Boolean {
        val world = Bukkit.getWorld(state.worldId) ?: return false
        if (!world.isChunkLoaded(state.x shr 4, state.z shr 4)) return false
        val block = world.getBlockAt(state.x, state.y, state.z)
        val banner = block.state as? Banner
        if (banner == null) {
            repository.deleteAt(state.worldId, state.x, state.y, state.z)
            return false
        }
        val id = banner.persistentDataContainer.get(
            PluginKeys.SPAWN_BANNER_ID,
            PersistentDataType.STRING,
        )
        if (id != state.bannerId.toString()) {
            repository.deleteAt(state.worldId, state.x, state.y, state.z)
            return false
        }
        return renderPlaced(block, state)
    }

    private fun resolveGuild(state: SpawnBannerState): Guild? {
        val entry = leaderboardRepository.getLeaderboardEntriesPaged(
            state.category.leaderboardType,
            state.category.period,
            state.rank - 1,
            1,
        ).firstOrNull() ?: return null
        if (entry.entityType != EntityType.GUILD) return null
        return guildService.getGuild(entry.entityId)
    }

    private fun renderPlaced(block: Block, state: SpawnBannerState): Boolean = runCatching {
        val desired = currentGuildBanner(resolveGuild(state))
        val desiredMeta = desired.itemMeta as? BannerMeta ?: return false
        val wall = block.type.name.endsWith("_WALL_BANNER")
        val base = desired.type.name.removeSuffix("_BANNER")
        val desiredType = Material.matchMaterial(
            if (wall) "${base}_WALL_BANNER" else "${base}_BANNER"
        ) ?: if (wall) Material.WHITE_WALL_BANNER else Material.WHITE_BANNER

        val existing = block.state as? Banner
        val metadataMatches = existing != null &&
            block.type == desiredType &&
            existing.patterns == desiredMeta.patterns &&
            existing.persistentDataContainer.get(
                PluginKeys.SPAWN_BANNER_ID,
                PersistentDataType.STRING,
            ) == state.bannerId.toString() &&
            existing.persistentDataContainer.get(
                PluginKeys.SPAWN_BANNER_RANK,
                PersistentDataType.INTEGER,
            ) == state.rank &&
            existing.persistentDataContainer.get(
                PluginKeys.SPAWN_BANNER_CATEGORY,
                PersistentDataType.STRING,
            ) == state.category.name
        if (metadataMatches) return true

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
            PluginKeys.SPAWN_BANNER_ID,
            PersistentDataType.STRING,
            state.bannerId.toString(),
        )
        banner.persistentDataContainer.set(
            PluginKeys.SPAWN_BANNER_RANK,
            PersistentDataType.INTEGER,
            state.rank,
        )
        banner.persistentDataContainer.set(
            PluginKeys.SPAWN_BANNER_CATEGORY,
            PersistentDataType.STRING,
            state.category.name,
        )
        banner.update(true, false)
        true
    }.getOrDefault(false)

    private fun currentGuildBanner(guild: Guild?): ItemStack {
        val stored = guild?.banner?.let { encoded ->
            runCatching { encoded.deserializeToItemStack() }.getOrNull()
        }
        return stored?.takeIf {
            it.type.name.endsWith("_BANNER") &&
                !it.type.name.endsWith("_WALL_BANNER")
        }?.clone() ?: ItemStack(Material.WHITE_BANNER)
    }

    companion object {
        internal const val REFRESH_TICKS = 20L * 60L
    }
}
