package net.lumalyte.lg.infrastructure.services

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.application.persistence.SecondVaultRepository
import net.lumalyte.lg.application.persistence.SecondVaultSpot
import net.lumalyte.lg.application.services.GuildPrestigePerks
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The shared inventory of one guild's second vault (REQ-244). */
class SecondVaultHolder(val guildId: UUID) : InventoryHolder {
    internal lateinit var shared: Inventory
    override fun getInventory(): Inventory = shared
}

/**
 * REQ-242 to REQ-248: the Prestige VI second vault. A physical chest with its own location,
 * hologram and items. It has no gold button and nothing reads it for gold. Items are kept in
 * memory while open and written to the database shortly after every change, on close, and on
 * shutdown. Slots above the current capacity are kept, not shown, like the main vault.
 */
class SecondVaultService(
    private val plugin: Plugin,
    private val repository: SecondVaultRepository,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val vaultService: GuildVaultService,
    private val perks: GuildPrestigePerks,
    private val holograms: VaultHologramService,
    private val lang: LangService,
) {
    private val logger = LoggerFactory.getLogger(SecondVaultService::class.java)
    private val spots = ConcurrentHashMap<UUID, SecondVaultSpot>()
    private val byBlock = ConcurrentHashMap<SecondVaultSpot, UUID>()
    private val inventories = ConcurrentHashMap<UUID, Inventory>()
    /** Stored items in slots above the capacity the open inventory was built with. */
    private val hidden = ConcurrentHashMap<UUID, Map<Int, String>>()
    private val dirty = ConcurrentHashMap.newKeySet<UUID>()
    private var flushTask: Int = -1
    /** One writer thread keeps saves in order, so an older snapshot never lands after a newer one. */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "LumaGuilds-SecondVault").apply { isDaemon = true }
    }

    enum class Placement { PLACED, LOCKED, ALREADY_PLACED, NEXT_TO_VAULT, FAILED }
    enum class Opening { OPENED, NOT_PLACED, NO_ACCESS }

    fun start() {
        repository.spots().forEach { (guild, spot) -> remember(guild, spot) }
        spots.forEach { (guildId, spot) ->
            val location = location(spot) ?: return@forEach
            if (location.block.type != Material.CHEST) location.block.type = Material.CHEST
            guildService.getGuild(guildId)?.let { holograms.createHologram(location, hologramText(it)) }
        }
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { flushDirty() }, 40L, 40L).taskId
        logger.info("Restored {} second vaults", spots.size)
    }

    fun stop() {
        if (flushTask != -1) Bukkit.getScheduler().cancelTask(flushTask)
        inventories.keys.forEach { save(it, async = true) }
        writer.shutdown()
        if (!writer.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
            logger.error("Second vault saves did not finish within 10 seconds")
        }
    }

    fun guildAt(location: Location): UUID? = spotOf(location)?.let(byBlock::get)

    fun spotOf(guildId: UUID): SecondVaultSpot? = spots[guildId]

    fun location(guildId: UUID): Location? = spots[guildId]?.let(::location)

    /** REQ-242: whether the guild's prestige tier allows putting items into a second vault. */
    fun unlocked(guildId: UUID): Boolean = perks.secondVault(guildId)

    fun chestItem(guild: Guild): ItemStack = ItemStack.of(Material.CHEST).also { item ->
        item.editMeta { meta ->
            meta.displayName(lang.msg("second_vault.item.name").decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(
                lang.msg("second_vault.item.guild", "guild" to guild.name),
                lang.msg("second_vault.item.place"),
                lang.msg("second_vault.item.items_only"),
                lang.msg("second_vault.item.capacity", "slots" to vaultService.getCapacityForLevel(guild.level)),
            ).map { it.decoration(TextDecoration.ITALIC, false) })
            meta.persistentDataContainer.set(PluginKeys.SECOND_VAULT_ID, PersistentDataType.STRING, guild.id.toString())
        }
    }

    /** REQ-243: same placement rules as the main vault, and never next to either vault. */
    fun place(guild: Guild, location: Location, player: Player): Placement {
        if (!unlocked(guild.id)) return Placement.LOCKED
        if (spots.containsKey(guild.id)) return Placement.ALREADY_PLACED
        if (touchesVault(location)) return Placement.NEXT_TO_VAULT
        if (vaultService.isValidVaultLocation(location, guild).getOrNull() != true) return Placement.FAILED
        val spot = SecondVaultSpot(location.world.uid, location.blockX, location.blockY, location.blockZ)
        val saved = runCatching { repository.place(guild.id, spot, Instant.now()) }
            .onFailure { logger.error("Could not save second vault for guild {}", guild.id, it) }
            .getOrDefault(false)
        if (!saved) return Placement.FAILED
        remember(guild.id, spot)
        holograms.createHologram(location, hologramText(guild))
        logger.info("{} placed the second vault for guild {} at {}", player.name, guild.name, spot)
        return Placement.PLACED
    }

    /** True when [location] is next to (or is) either vault of any guild. */
    fun touchesVault(location: Location): Boolean = sequenceOf(
        0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1,
    ).any { (dx, dz) ->
        val near = location.clone().add(dx.toDouble(), 0.0, dz.toDouble())
        guildAt(near) != null || (near.block.type == Material.CHEST && vaultService.getGuildForVaultChest(near) != null)
    }

    fun open(player: Player, guild: Guild): Opening {
        if (!spots.containsKey(guild.id)) return Opening.NOT_PLACED
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.ACCESS_VAULT)) return Opening.NO_ACCESS
        player.openInventory(inventory(guild))
        return Opening.OPENED
    }

    /** The shared inventory, rebuilt when nobody is viewing and the capacity or lock state changed. */
    private fun inventory(guild: Guild): Inventory {
        val capacity = vaultService.getCapacityForLevel(guild.level)
        val title = title(guild)
        inventories[guild.id]?.let { current ->
            if (current.viewers.isNotEmpty() || current.size == capacity && titles[guild.id] == title) return current
            save(guild.id, async = false)
            inventories.remove(guild.id)
        }
        val holder = SecondVaultHolder(guild.id)
        val inventory = Bukkit.createInventory(holder, capacity, title)
        holder.shared = inventory
        val stored = repository.items(guild.id)
        val (shown, kept) = SecondVaultSlots.split(stored, capacity)
        shown.forEach { (slot, data) -> decode(data)?.let { inventory.setItem(slot, it) } }
        hidden[guild.id] = kept
        inventories[guild.id] = inventory
        titles[guild.id] = title
        return inventory
    }

    private val titles = ConcurrentHashMap<UUID, Component>()

    private fun title(guild: Guild): Component = if (unlocked(guild.id)) {
        lang.msg("second_vault.title", "guild" to guild.name)
    } else {
        lang.msg("second_vault.title_withdraw_only", "guild" to guild.name)
    }

    fun hologramText(guild: Guild): Component = if (unlocked(guild.id)) {
        lang.msg("second_vault.hologram", "guild" to guild.name)
    } else {
        lang.msg("second_vault.hologram_withdraw_only", "guild" to guild.name)
    }

    /** Marks the vault changed; it is written within two seconds. */
    fun changed(guildId: UUID) {
        dirty += guildId
    }

    /** Called when a viewer closes the vault: write now and let the inventory go when nobody is left. */
    fun closed(guildId: UUID, inventory: Inventory) {
        if (inventories[guildId] !== inventory) return
        save(guildId, async = true)
        if (inventory.viewers.size <= 1) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (inventory.viewers.isEmpty() && inventories[guildId] === inventory && guildId !in dirty) {
                    inventories.remove(guildId)
                    hidden.remove(guildId)
                }
            })
        }
    }

    private fun flushDirty() {
        dirty.toList().forEach { save(it, async = true) }
    }

    /** Snapshots on the main thread, then writes off it unless [async] is false. */
    private fun save(guildId: UUID, async: Boolean) {
        val inventory = inventories[guildId] ?: run { dirty -= guildId; return }
        dirty -= guildId
        val snapshot = SecondVaultSlots.merge(
            inventory.contents.withIndex().filter { (_, item) -> item != null && !item.type.isAir }
                .associate { (slot, item) -> slot to encode(item!!) },
            hidden[guildId].orEmpty(),
        )
        val write = Runnable {
            runCatching { repository.saveItems(guildId, snapshot) }
                .onFailure {
                    logger.error("Could not save second vault items for guild {}; will retry", guildId, it)
                    dirty += guildId
                }
        }
        val queued = writer.submit(write)
        if (!async) queued.get()
    }

    /**
     * Removes the chest. Items stay stored unless [dropItems], in which case they drop at the chest
     * and are cleared (REQ-246, same rule as the main vault).
     */
    fun remove(guildId: UUID, dropItems: Boolean, clearBlock: Boolean) {
        val spot = spots[guildId] ?: return
        val location = location(spot)
        inventories[guildId]?.let { inventory ->
            inventory.viewers.toList().forEach { it.closeInventory() }
            save(guildId, async = false)
            inventories.remove(guildId)
            hidden.remove(guildId)
        }
        if (dropItems && location != null) {
            drop(guildId, location)
            repository.saveItems(guildId, emptyMap())
        }
        repository.removeSpot(guildId)
        forget(guildId)
        location?.let {
            holograms.removeHologram(it)
            if (clearBlock) it.block.type = Material.AIR
        }
    }

    /** REQ-246: on disband the items drop at the chest, like the main vault, and the records go. */
    fun disband(guildId: UUID) {
        runCatching {
            if (spots.containsKey(guildId)) remove(guildId, dropItems = true, clearBlock = true)
            repository.deleteAll(guildId)
        }.onFailure { logger.error("Could not clear second vault for disbanded guild {}", guildId, it) }
    }

    fun isViewing(player: Player): Boolean = player.openInventory.topInventory.holder is SecondVaultHolder

    private fun drop(guildId: UUID, location: Location) {
        repository.items(guildId).values.mapNotNull(::decode).forEach { item ->
            location.world.dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), item)
        }
    }

    private fun remember(guildId: UUID, spot: SecondVaultSpot) {
        spots[guildId] = spot
        byBlock[spot] = guildId
    }

    private fun forget(guildId: UUID) {
        spots.remove(guildId)?.let(byBlock::remove)
    }

    private fun spotOf(location: Location): SecondVaultSpot? {
        val world = location.world ?: return null
        return SecondVaultSpot(world.uid, location.blockX, location.blockY, location.blockZ)
    }

    private fun location(spot: SecondVaultSpot): Location? =
        Bukkit.getWorld(spot.worldId)?.let { Location(it, spot.x.toDouble(), spot.y.toDouble(), spot.z.toDouble()) }

    private fun encode(item: ItemStack): String = Base64.getEncoder().encodeToString(item.serializeAsBytes())

    private fun decode(data: String): ItemStack? = runCatching { ItemStack.deserializeBytes(Base64.getDecoder().decode(data)) }
        .onFailure { logger.warn("Skipping a second vault item that could not be read") }
        .getOrNull()
}

/** Pure slot bookkeeping for the second vault (REQ-248). */
object SecondVaultSlots {
    /** Splits stored slots into those shown at [capacity] and those kept above it. */
    fun <T> split(stored: Map<Int, T>, capacity: Int): Pair<Map<Int, T>, Map<Int, T>> =
        stored.filterKeys { it in 0 until capacity } to stored.filterKeys { it >= capacity }

    /** What to store: the visible slots plus the kept slots above capacity. */
    fun <T> merge(visible: Map<Int, T>, kept: Map<Int, T>): Map<Int, T> = kept + visible
}
