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
import net.lumalyte.lg.infrastructure.services.hall.HallSwordPlacement
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

    fun start() = plugin.server.pluginManager.registerEvents(this, plugin)

    fun stop() = active.keys.toList().forEach(::clear)

    @EventHandler fun quit(event: PlayerQuitEvent) = clear(event.player.uniqueId)

    /** Builds the statue three blocks in front of [viewer], facing them. */
    fun show(viewer: Player, guildName: String, prestige: Int, display: HallDisplay, skin: UUID?, banner: ItemStack? = null) {
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

        // The plinth's origin is a corner, so it is spawned at the statue's spot with no yaw and shifted
        // back half its width: otherwise the statue's yaw swung it round that corner, off-centre.
        hidden(world.spawn(base.clone().also { it.yaw = 0f; it.pitch = 0f }, BlockDisplay::class.java) {
            it.block = look.plinth.createBlockData()
            it.transformation = Transformation(Vector3f(-PLINTH_WIDTH / 2, 0f, -PLINTH_WIDTH / 2), AxisAngle4f(),
                Vector3f(PLINTH_WIDTH, PLINTH_HEIGHT, PLINTH_WIDTH), AxisAngle4f())
        })
        val top = base.clone().add(0.0, PLINTH_HEIGHT.toDouble(), 0.0)
        hidden(world.spawn(top, Mannequin::class.java) { statue ->
            statue.setAI(false)
            statue.setGravity(false)
            statue.isInvulnerable = true
            statue.isSilent = true
            statue.isImmovable = true
            statue.isCollidable = false
            statue.description = Component.empty()
            skin?.let { id -> statue.profile = ResolvableProfile.resolvableProfile(Bukkit.createProfile(id)) }
            look.armor().forEach { (slot, item) -> statue.equipment.setItem(slot, item) }
            statue.equipment.setItem(EquipmentSlot.OFF_HAND, ItemStack.of(Material.AIR))
            statue.equipment.setItem(EquipmentSlot.HAND, ItemStack.of(look.trophy))
        })
        hidden(world.spawn(top.clone().add(0.0, 2.35, 0.0), TextDisplay::class.java) {
            it.billboard = Display.Billboard.CENTER
            it.text(Component.text(look.nameplate(guildName), TextColor.color(look.nameColor))
                .appendNewline().append(Component.text("Prestige ${PrestigeNumeral.of(prestige)}", NamedTextColor.GOLD)))
        })
        // REQ-270: the guild's own banner, beside the statue with its front to the viewer.
        val bannerSpot = top.clone().add(sideways(top, -1.15)).add(top.direction.clone().setY(0).normalize().multiply(-0.25))
        hidden(world.spawn(bannerSpot.add(0.0, 1.1, 0.0), ItemDisplay::class.java) {
            it.setItemStack(banner?.clone()?.also { b -> b.amount = 1 } ?: ItemStack.of(Material.WHITE_BANNER))
            it.setRotation(top.yaw, 0f)
            // With no extra turn the banner item showed its back to the viewer; half a turn shows the front.
            it.transformation = Transformation(Vector3f(), AxisAngle4f(Math.PI.toFloat(), 0f, 1f, 0f),
                Vector3f(1.2f, 1.2f, 1.2f), AxisAngle4f())
        })
        hidden(world.spawn(top.clone().add(0.0, 0.0, 0.0).add(sideways(top, 0.9)).add(0.0, 0.6, 0.0), ItemDisplay::class.java) {
            it.setItemStack(ItemStack.of(look.trophy))
            it.billboard = Display.Billboard.VERTICAL
            it.transformation = scaled(0.6f, 0.6f, 0.6f)
        })

        // REQ-272: the chosen pose through BetterModel's prestige limb model, when it is installed.
        val statue = entities.filterIsInstance<Mannequin>().firstOrNull()
        val posed = statue?.let { HallStatuePose.orNull()?.apply(it, viewer.uniqueId, skin, look.pose) }
        if (posed != null && HallStatuePose.animationFor(look.pose) == HallStatuePose.GUARD) hidden(guardSword(top))
        viewer.playSound(top, look.sound, 1f, 1f)
        var tick = 0
        val effects = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!viewer.isOnline) return@Runnable
            tick += 5
            for (y in 0..12) {
                val point = top.clone().add(0.0, y * 0.35, 0.0)
                viewer.spawnParticle(Particle.DUST, point, 2, 0.35, 0.05, 0.35, 0.0, Particle.DustOptions(look.spotlight, 1.2f))
            }
            viewer.spawnParticle(look.aura, top.clone().add(0.0, 1.0, 0.0), 6, 0.45, 0.7, 0.45, 0.01)
            if (tick % 100 == 0) viewer.playSound(top, look.sound, 0.6f, 1f)
        }, 5L, 5L)
        val end = Bukkit.getScheduler().runTaskLater(plugin, Runnable { clear(viewer.uniqueId) }, SECONDS * 20L)
        active[viewer.uniqueId] = Preview(entities, listOf(effects, end), listOfNotNull(posed))
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

    /**
     * The Guard pose's vanilla netherite sword, planted point-down in front of the statue with both
     * hands on the grip (the pose mockup). A display entity, so it is the real item; the model's own
     * held-item bones are hidden during Guard.
     */
    private fun guardSword(top: Location): ItemDisplay {
        val forward = top.direction.clone().setY(0).normalize()
        val spot = top.clone().add(forward.multiply(HallSwordPlacement.FORWARD)).add(0.0, HallSwordPlacement.CENTER_HEIGHT, 0.0)
        return top.world.spawn(spot, ItemDisplay::class.java) {
            it.setItemStack(ItemStack.of(Material.NETHERITE_SWORD))
            it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
            it.setRotation(top.yaw, 0f)
            it.transformation = Transformation(Vector3f(), AxisAngle4f(HallSwordPlacement.TURN, 0f, 0f, 1f),
                Vector3f(HallSwordPlacement.SCALE, HallSwordPlacement.SCALE, HallSwordPlacement.SCALE), AxisAngle4f())
        }
    }

    private fun sideways(at: Location, distance: Double) =
        at.direction.clone().setY(0).normalize().crossProduct(org.bukkit.util.Vector(0, 1, 0)).multiply(distance)

    private fun scaled(x: Float, y: Float, z: Float) =
        Transformation(Vector3f(), AxisAngle4f(), Vector3f(x, y, z), AxisAngle4f())

    companion object {
        const val SECONDS = 30
        private const val PLINTH_WIDTH = 1.2f
        private const val PLINTH_HEIGHT = 0.5f

        /** REQ-270: the guild's saved banner item, read fresh; null when it has none. */
        fun guildBanner(guild: net.lumalyte.lg.domain.entities.Guild): ItemStack? {
            val fresh = org.koin.core.context.GlobalContext.getOrNull()
                ?.getOrNull<net.lumalyte.lg.application.services.GuildService>()?.getGuild(guild.id) ?: guild
            return fresh.banner?.let { runCatching { it.deserializeToItemStack() }.getOrNull() }
                ?.takeIf { it.type.name.endsWith("_BANNER") }
        }
    }
}

/** REQ-264: what each Hall theme cosmetic looks like with vanilla blocks, items, particles and sounds. */
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

    val trophy: Material get() = when (theme(HallSlot.TROPHY)) {
        HallTheme.GILDED -> Material.TOTEM_OF_UNDYING
        HallTheme.EMBER -> Material.FIRE_CHARGE
        HallTheme.WINGED -> Material.ELYTRA
        HallTheme.TIDAL -> Material.HEART_OF_THE_SEA
        HallTheme.RADIANT -> Material.BEACON
        HallTheme.CROWNED -> Material.NETHER_STAR
        null -> Material.AIR
    }

    val aura: Particle get() = when (theme(HallSlot.AURA)) {
        HallTheme.GILDED -> Particle.WAX_ON
        HallTheme.EMBER -> Particle.SMALL_FLAME
        HallTheme.WINGED -> Particle.WHITE_ASH
        HallTheme.TIDAL -> Particle.BUBBLE_POP
        HallTheme.RADIANT -> Particle.GLOW
        HallTheme.CROWNED -> Particle.ENCHANT
        null -> Particle.END_ROD
    }

    val sound: Sound get() = when (theme(HallSlot.SOUND)) {
        HallTheme.GILDED -> Sound.BLOCK_BELL_USE
        HallTheme.EMBER -> Sound.ITEM_FIRECHARGE_USE
        HallTheme.WINGED -> Sound.ENTITY_PHANTOM_FLAP
        HallTheme.TIDAL -> Sound.BLOCK_CONDUIT_AMBIENT_SHORT
        HallTheme.RADIANT -> Sound.BLOCK_AMETHYST_BLOCK_CHIME
        HallTheme.CROWNED -> Sound.BLOCK_ENCHANTMENT_TABLE_USE
        null -> Sound.BLOCK_NOTE_BLOCK_CHIME
    }

    val spotlight: Color get() = Color.fromRGB((theme(HallSlot.SPOTLIGHT) ?: return Color.WHITE).color)

    val nameColor: Int get() = theme(HallSlot.NAMEPLATE)?.color ?: 0xFFFFFF

    fun nameplate(guild: String): String = theme(HallSlot.TITLE)?.cosmetic(HallSlot.TITLE)?.replace("Name", guild) ?: guild

    /** REQ-271: the guild's chosen armor, or the tier default for parts it hasn't picked. */
    fun armor(): Map<EquipmentSlot, ItemStack> = HallArmorItems.slots.entries.associate { (part, slot) ->
        slot to HallArmorItems.item(part, display.armor[part], prestige)
    }

    /** The chosen pose's name (Victory, Guard and so on); Guard before any theme is owned. */
    val pose: String get() = theme(HallSlot.POSE)?.cosmetic(HallSlot.POSE) ?: "Guard"
}
