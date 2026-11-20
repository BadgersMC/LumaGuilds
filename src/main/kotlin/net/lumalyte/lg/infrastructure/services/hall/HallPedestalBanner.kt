package net.lumalyte.lg.infrastructure.services.hall

import net.lumalyte.lg.domain.rewards.HallPedestal
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Banner
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BannerMeta

/**
 * REQ-278: the real banner staff hang under a Hall pedestal. A wall banner on any side of the column
 * of blocks under the pedestal, up to [DEPTH] blocks down, belongs to that pedestal; it is repainted
 * as the banner of the guild holding the pedestal, or plain white while nobody holds it.
 */
internal object HallPedestalBanner {
    const val DEPTH = 4
    private val SIDES = listOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)

    /** Block positions (x, y, z, side) where a wall banner hanging on the pedestal's column would be, nearest first. */
    fun spots(pedestal: HallPedestal): List<Pair<Triple<Int, Int, Int>, BlockFace>> {
        val x = Math.floor(pedestal.x).toInt()
        val y = Math.floor(pedestal.y).toInt()
        val z = Math.floor(pedestal.z).toInt()
        return (1..DEPTH).flatMap { down ->
            SIDES.map { side -> Triple(x + side.modX, y - down, z + side.modZ) to side }
        }
    }

    /** The wall banner hanging on [pedestal]'s column (facing away from it), if staff have placed one. */
    fun find(world: World, pedestal: HallPedestal): Block? = spots(pedestal).firstNotNullOfOrNull { (at, side) ->
        val block = world.getBlockAt(at.first, at.second, at.third)
        val data = block.blockData
        block.takeIf { isWallBanner(it.type.name) && data is Directional && data.facing == side }
    }

    /** True when [block] is one of the pedestal's banner spots, for a banner just placed there. */
    fun belongsTo(block: Block, pedestal: HallPedestal): Boolean =
        block.world.name == pedestal.world && find(block.world, pedestal)?.location == block.location

    fun isWallBanner(material: String) = material.endsWith("_WALL_BANNER")

    /** The wall-banner block for a guild's saved banner item ([itemType] like RED_BANNER), white for none. */
    fun wallMaterial(itemType: String?): String {
        val base = itemType?.takeIf { it.endsWith("_BANNER") && !isWallBanner(it) }?.removeSuffix("_BANNER") ?: "WHITE"
        return "${base}_WALL_BANNER"
    }

    /** Repaints [block] as [banner] (plain white for null); returns whether it changed. */
    fun paint(block: Block, banner: ItemStack?): Boolean {
        if (!isWallBanner(block.type.name)) return false
        val target = Material.matchMaterial(wallMaterial(banner?.type?.name)) ?: Material.WHITE_WALL_BANNER
        val patterns = (banner?.itemMeta as? BannerMeta)?.patterns.orEmpty()
        val now = block.state as? Banner
        if (block.type == target && now != null && now.patterns == patterns) return false
        val facing = (block.blockData as? Directional)?.facing
        if (block.type != target) {
            block.setType(target, false)
            val data = block.blockData
            if (facing != null && data is Directional) { data.facing = facing; block.setBlockData(data, false) }
        }
        val state = block.state as? Banner ?: return false
        state.patterns = patterns
        state.update(true, false)
        return true
    }
}
