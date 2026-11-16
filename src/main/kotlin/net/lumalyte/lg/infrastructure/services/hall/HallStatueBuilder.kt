package net.lumalyte.lg.infrastructure.services.hall

import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.lumalyte.lg.domain.values.PrestigeNumeral
import net.lumalyte.lg.infrastructure.services.HallLook
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Mannequin
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID

/**
 * REQ-264, REQ-275: one Hall statue on its plinth (nameplate, banner, trophy, Guard sword), shared by
 * the viewer-only preview and the Hall of Prestige pedestals. [register] receives every entity right
 * after it spawns, so the caller decides who sees it; [pose] wraps the statue in the animated model.
 */
internal object HallStatueBuilder {
    const val PLINTH_WIDTH = 1.2f
    const val PLINTH_HEIGHT = 0.5f

    class Built(val entities: List<Entity>, val top: Location, val closer: (() -> Unit)?, val sword: ItemDisplay?)

    fun build(
        base: Location,
        look: HallLook,
        guildName: String,
        prestige: Int,
        skin: UUID?,
        banner: ItemStack?,
        register: (Entity) -> Unit,
        pose: (Mannequin) -> (() -> Unit)?,
    ): Built {
        val world = base.world
        val entities = mutableListOf<Entity>()
        fun <T : Entity> add(entity: T): T = entity.also { entities += it; register(it) }

        // The plinth's origin is a corner, so it is spawned at the statue's spot with no yaw and shifted
        // back half its width: otherwise the statue's yaw swung it round that corner, off-centre.
        add(world.spawn(base.clone().also { it.yaw = 0f; it.pitch = 0f }, BlockDisplay::class.java) {
            it.block = look.plinth.createBlockData()
            it.transformation = Transformation(Vector3f(-PLINTH_WIDTH / 2, 0f, -PLINTH_WIDTH / 2), AxisAngle4f(),
                Vector3f(PLINTH_WIDTH, PLINTH_HEIGHT, PLINTH_WIDTH), AxisAngle4f())
        })
        val top = base.clone().add(0.0, PLINTH_HEIGHT.toDouble(), 0.0)
        val statue = add(world.spawn(top, Mannequin::class.java) { statue ->
            statue.setAI(false)
            statue.setGravity(false)
            statue.isInvulnerable = true
            statue.isSilent = true
            statue.isImmovable = true
            statue.isCollidable = false
            statue.description = Component.empty()
            skin?.let { id -> statue.profile = ResolvableProfile.resolvableProfile(Bukkit.createProfile(id)) }
            look.armor().forEach { (slot, item) -> statue.equipment.setItem(slot, item) }
            EquipmentSlot.entries.filter { it.isHand || it.isArmor }.forEach { statue.equipment.setDropChance(it, 0f) }
            statue.equipment.setItem(EquipmentSlot.OFF_HAND, ItemStack.of(Material.AIR))
            statue.equipment.setItem(EquipmentSlot.HAND, ItemStack.of(look.trophy))
        })
        add(world.spawn(top.clone().add(0.0, 2.35, 0.0), TextDisplay::class.java) {
            it.billboard = Display.Billboard.CENTER
            it.text(Component.text(look.nameplate(guildName), TextColor.color(look.nameColor))
                .appendNewline().append(Component.text("Prestige ${PrestigeNumeral.of(prestige)}", NamedTextColor.GOLD)))
        })
        // REQ-270: the guild's own banner, beside the statue with its front to the viewer.
        val bannerSpot = top.clone().add(sideways(top, -1.15)).add(forward(top).multiply(-0.25))
        add(world.spawn(bannerSpot.add(0.0, 1.1, 0.0), ItemDisplay::class.java) {
            it.setItemStack(banner?.clone()?.also { b -> b.amount = 1 } ?: ItemStack.of(Material.WHITE_BANNER))
            it.setRotation(top.yaw, 0f)
            // With no extra turn the banner item showed its back to the viewer; half a turn shows the front.
            it.transformation = Transformation(Vector3f(), AxisAngle4f(Math.PI.toFloat(), 0f, 1f, 0f),
                Vector3f(1.2f, 1.2f, 1.2f), AxisAngle4f())
        })
        add(world.spawn(top.clone().add(sideways(top, 0.9)).add(0.0, 0.6, 0.0), ItemDisplay::class.java) {
            it.setItemStack(ItemStack.of(look.trophy))
            it.billboard = Display.Billboard.VERTICAL
            it.transformation = Transformation(Vector3f(), AxisAngle4f(), Vector3f(0.6f, 0.6f, 0.6f), AxisAngle4f())
        })

        // REQ-272: the chosen pose through BetterModel's prestige limb model, when it is installed.
        val closer = pose(statue)
        val sword = if (closer != null && HallStatuePose.animationFor(look.pose) == HallStatuePose.GUARD) add(guardSword(top)) else null
        return Built(entities, top, closer, sword)
    }

    /**
     * The Guard pose's vanilla netherite sword, planted point-down in front of the statue with both
     * hands on the grip (the pose mockup). A display entity, so it is the real item; the model's own
     * held-item bones are hidden during Guard.
     */
    private fun guardSword(top: Location): ItemDisplay {
        val spot = top.clone().add(forward(top).multiply(HallSwordPlacement.FORWARD)).add(0.0, HallSwordPlacement.CENTER_HEIGHT, 0.0)
        return top.world.spawn(spot, ItemDisplay::class.java) {
            it.setItemStack(ItemStack.of(Material.NETHERITE_SWORD))
            it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.NONE
            it.setRotation(top.yaw, 0f)
            it.transformation = Transformation(Vector3f(), AxisAngle4f(HallSwordPlacement.TURN, 0f, 0f, 1f),
                Vector3f(HallSwordPlacement.SCALE, HallSwordPlacement.SCALE, HallSwordPlacement.SCALE), AxisAngle4f())
        }
    }

    private fun forward(at: Location) = at.direction.clone().setY(0).normalize()

    private fun sideways(at: Location, distance: Double) =
        forward(at).crossProduct(org.bukkit.util.Vector(0, 1, 0)).multiply(distance)
}
