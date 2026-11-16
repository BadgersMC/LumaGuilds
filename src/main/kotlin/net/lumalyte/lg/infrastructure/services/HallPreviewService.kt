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
import org.bukkit.DyeColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.banner.Pattern
import org.bukkit.block.banner.PatternType
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
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.BannerMeta
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.inventory.meta.trim.TrimMaterial
import org.bukkit.inventory.meta.trim.TrimPattern
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
    private data class Preview(val entities: List<Entity>, val tasks: List<BukkitTask>)

    private val active = ConcurrentHashMap<UUID, Preview>()

    fun start() = plugin.server.pluginManager.registerEvents(this, plugin)

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

        hidden(world.spawn(base.clone().add(-0.6, 0.0, -0.6), BlockDisplay::class.java) {
            it.block = look.plinth.createBlockData()
            it.transformation = scaled(1.2f, 0.5f, 1.2f)
        })
        val top = base.clone().add(0.0, 0.5, 0.0)
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
            statue.equipment.setItem(EquipmentSlot.HAND, ItemStack.of(look.trophy))
        })
        hidden(world.spawn(top.clone().add(0.0, 2.35, 0.0), TextDisplay::class.java) {
            it.billboard = Display.Billboard.CENTER
            it.text(Component.text(look.nameplate(guildName), TextColor.color(look.nameColor))
                .appendNewline().append(Component.text("Prestige ${PrestigeNumeral.of(prestige)}", NamedTextColor.GOLD)))
        })
        val behind = top.clone().add(top.direction.multiply(-0.7))
        hidden(world.spawn(behind.add(0.0, 1.3, 0.0), ItemDisplay::class.java) {
            it.setItemStack(look.banner())
            it.transformation = scaled(1.6f, 1.6f, 1.6f)
        })
        hidden(world.spawn(top.clone().add(0.0, 0.0, 0.0).add(sideways(top, 0.9)).add(0.0, 0.6, 0.0), ItemDisplay::class.java) {
            it.setItemStack(ItemStack.of(look.trophy))
            it.billboard = Display.Billboard.VERTICAL
            it.transformation = scaled(0.6f, 0.6f, 0.6f)
        })

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
        active[viewer.uniqueId] = Preview(entities, listOf(effects, end))
    }

    fun clear(viewer: UUID) {
        val preview = active.remove(viewer) ?: return
        preview.tasks.forEach { runCatching { it.cancel() } }
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

    private fun sideways(at: Location, distance: Double) =
        at.direction.clone().setY(0).normalize().crossProduct(org.bukkit.util.Vector(0, 1, 0)).multiply(distance)

    private fun scaled(x: Float, y: Float, z: Float) =
        Transformation(Vector3f(), AxisAngle4f(), Vector3f(x, y, z), AxisAngle4f())

    companion object {
        const val SECONDS = 30
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

    /** Armor by prestige tier: I–II iron, III–IV diamond, V gold, VI netherite; the chosen trim on every piece. */
    fun armor(): Map<EquipmentSlot, ItemStack> {
        val metal = when {
            prestige >= 6 -> "NETHERITE"
            prestige == 5 -> "GOLDEN"
            prestige >= 3 -> "DIAMOND"
            else -> "IRON"
        }
        val trim = trim()
        return mapOf(
            EquipmentSlot.HEAD to "HELMET", EquipmentSlot.CHEST to "CHESTPLATE",
            EquipmentSlot.LEGS to "LEGGINGS", EquipmentSlot.FEET to "BOOTS",
        ).mapValues { (_, piece) ->
            ItemStack.of(Material.valueOf("${metal}_$piece")).also { item ->
                if (trim != null) item.editMeta(ArmorMeta::class.java) { it.trim = trim }
            }
        }
    }

    private fun trim(): ArmorTrim? = when (theme(HallSlot.TRIM)) {
        HallTheme.GILDED -> ArmorTrim(TrimMaterial.GOLD, TrimPattern.WAYFINDER)
        HallTheme.EMBER -> ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.RIB)
        HallTheme.WINGED -> ArmorTrim(TrimMaterial.QUARTZ, TrimPattern.FLOW)
        HallTheme.TIDAL -> ArmorTrim(TrimMaterial.LAPIS, TrimPattern.TIDE)
        HallTheme.RADIANT -> ArmorTrim(TrimMaterial.RESIN, TrimPattern.RAISER)
        HallTheme.CROWNED -> ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.SILENCE)
        null -> null
    }

    fun banner(): ItemStack {
        val (base, patterns) = when (theme(HallSlot.BANNER)) {
            HallTheme.GILDED -> Material.BLACK_BANNER to listOf(Pattern(DyeColor.YELLOW, PatternType.BORDER), Pattern(DyeColor.YELLOW, PatternType.RHOMBUS))
            HallTheme.EMBER -> Material.RED_BANNER to listOf(Pattern(DyeColor.ORANGE, PatternType.GRADIENT_UP), Pattern(DyeColor.ORANGE, PatternType.TRIANGLES_BOTTOM))
            HallTheme.WINGED -> Material.LIGHT_GRAY_BANNER to listOf(Pattern(DyeColor.WHITE, PatternType.CURLY_BORDER), Pattern(DyeColor.WHITE, PatternType.FLOWER))
            HallTheme.TIDAL -> Material.BLUE_BANNER to listOf(Pattern(DyeColor.CYAN, PatternType.FLOW), Pattern(DyeColor.CYAN, PatternType.TRIANGLES_BOTTOM))
            HallTheme.RADIANT -> Material.WHITE_BANNER to listOf(Pattern(DyeColor.YELLOW, PatternType.CIRCLE), Pattern(DyeColor.YELLOW, PatternType.GRADIENT))
            HallTheme.CROWNED -> Material.PURPLE_BANNER to listOf(Pattern(DyeColor.YELLOW, PatternType.TRIANGLES_TOP), Pattern(DyeColor.YELLOW, PatternType.STRIPE_TOP))
            null -> Material.WHITE_BANNER to emptyList()
        }
        return ItemStack.of(base).also { item -> item.editMeta(BannerMeta::class.java) { it.patterns = patterns } }
    }
}
