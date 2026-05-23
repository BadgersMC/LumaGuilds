package net.lumalyte.lg.infrastructure.adapters.bukkit

import net.lumalyte.lg.domain.values.Position
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.Location
import org.bukkit.World

fun Location.toPosition2D(): Position2D =
    // blockX/blockZ floor the coordinate; plain toInt() truncates toward zero,
    // which picks the wrong block for negative coordinates.
    Position2D(this.blockX, this.blockZ)

fun Location.toPosition3D(): Position3D =
    // blockX/Y/Z floor the coordinate; plain toInt() truncates toward zero,
    // which picks the wrong block for negative coordinates.
    Position3D(this.blockX, this.blockY, this.blockZ)

fun Position.toLocation(world: World): Location =
    // Center the location in the block (add 0.5 to x and z coordinates)
    Location(world, this.x.toDouble() + 0.5, this.y?.toDouble() ?: 0.0, this.z.toDouble() + 0.5)
