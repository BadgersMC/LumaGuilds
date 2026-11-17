package net.lumalyte.lg.infrastructure.services

import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.lumalyte.lg.domain.rewards.HallDisplay
import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import net.lumalyte.lg.domain.values.PrestigeNumeral
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import net.lumalyte.lg.infrastructure.services.hall.HallArmorItems
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.infrastructure.services.hall.HallStatuePose
import net.lumalyte.lg.infrastructure.services.hall.HallStatueBuilder
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * REQ-264: a temporary Hall statue in front of a player, wearing the guild's Hall Display choices.
 * Only that player sees it; it never saves with the world and goes after [SECONDS] seconds, on
 * quit, on a new preview or when the plugin stops.
 */
class HallPreviewService(private val plugin: Plugin) : Listener {
    private data class Preview(val entities: List<Entity>, val tasks: List<BukkitTask>, val closers: List<() -> Unit> = emptyList())

    private val active = ConcurrentHashMap<UUID, Preview>()
    private val lastPosed = ConcurrentHashMap<UUID, Boolean>()

    /** Whether [viewer]'s latest preview is animated (BetterModel had the pose model). */
    fun posed(viewer: UUID): Boolean = lastPosed[viewer] == true

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        net.lumalyte.lg.infrastructure.services.hall.HallSpawnGuard.register(plugin)
        net.lumalyte.lg.infrastructure.services.hall.HallStatueProtection.register(plugin)
    }

    fun stop() = active.keys.toList().forEach(::clear)

    @EventHandler fun quit(event: PlayerQuitEvent) = clear(event.player.uniqueId)

    /** Builds the statue three blocks in front of [viewer], facing them. */
    fun show(viewer: Player, guildName: String, prestige: Int, display: HallDisplay, skin: UUID?) {
        clear(viewer.uniqueId)
        val look = HallLook(display, prestige)
        val base = front(viewer)
        val world = base.world
        val entities = mutableListOf<Entity>()
        fun <T : Entity> hidden(entity: T): T {
            entity.isPersistent = false
            entity.isVisibleByDefault = false
            viewer.showEntity(plugin, entity)
            entities += entity
            return entity
        }

        val built = HallStatueBuilder.build(base, look, guildName, prestige, skin, register = { hidden(it) }) { statue: Mannequin ->
            HallStatuePose.orNull()?.apply(statue, { it == viewer.uniqueId }, skin)
        }
        val posed = built.closer
        val end = Bukkit.getScheduler().runTaskLater(plugin, Runnable { clear(viewer.uniqueId) }, SECONDS * 20L)
        active[viewer.uniqueId] = Preview(entities, listOf(end), listOfNotNull(posed))
        lastPosed[viewer.uniqueId] = posed != null
    }

    fun clear(viewer: UUID) {
        val preview = active.remove(viewer) ?: return
        preview.tasks.forEach { runCatching { it.cancel() } }
        preview.closers.forEach { runCatching { it() } }
        preview.entities.forEach { runCatching { it.remove() } }
    }

    private fun front(viewer: Player): Location {
        val direction = viewer.location.direction.setY(0).normalize()
        val spot = viewer.location.clone().add(direction.multiply(3.0))
        spot.y = viewer.location.y
        spot.yaw = viewer.location.yaw + 180f
        spot.pitch = 0f
        return spot.toCenterLocation().apply { y = viewer.location.blockY.toDouble() }.also { it.yaw = spot.yaw; it.pitch = 0f }
    }

    companion object {
        const val SECONDS = 30
    }
}

/** REQ-264: what each Hall theme cosmetic looks like with vanilla blocks and items. */
internal class HallLook(private val display: HallDisplay, private val prestige: Int) {
    private fun theme(slot: HallSlot): HallTheme? = display.wearing(slot)

    val plinth: Material get() = when (theme(HallSlot.PLINTH)) {
        HallTheme.GILDED -> Material.GILDED_BLACKSTONE
        HallTheme.EMBER -> Material.NETHER_BRICKS
        HallTheme.WINGED -> Material.QUARTZ_BLOCK
        HallTheme.TIDAL -> Material.PRISMARINE_BRICKS
        HallTheme.RADIANT -> Material.SHROOMLIGHT
        HallTheme.CROWNED -> Material.PURPUR_BLOCK
        null -> Material.POLISHED_DEEPSLATE
    }





    val nameColor: Int get() = theme(HallSlot.NAMEPLATE)?.color ?: 0xFFFFFF

    fun nameplate(guild: String): String = theme(HallSlot.TITLE)?.cosmetic(HallSlot.TITLE)?.replace("Name", guild) ?: guild

    /** REQ-271: the guild's chosen armor, or the tier default for parts it hasn't picked. */
    fun armor(): Map<EquipmentSlot, ItemStack> = HallArmorItems.slots.entries.associate { (part, slot) ->
        slot to HallArmorItems.item(part, display.armor[part], prestige)
    }

}
