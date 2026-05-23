package net.lumalyte.lg.infrastructure.adapters.bukkit

import net.lumalyte.lg.domain.values.Position
import net.lumalyte.lg.domain.values.Position2D
import net.lumalyte.lg.domain.values.Position3D
import org.bukkit.Location
import org.bukkit.World

// blockX/blockZ floor the coordinate; plain toInt() truncates toward zero,
// which picks the wrong block for negative coordinates.
fun Location.toPosition2D(): Position2D = Position2D(this.blockX, this.blockZ)

// blockX/Y/Z floor the coordinate; plain toInt() truncates toward zero,
// which picks the wrong block for negative coordinates.
fun Location.toPosition3D(): Position3D = Position3D(this.blockX, this.blockY, this.blockZ)

// Center the location in the block (add 0.5 to x and z coordinates)
@Suppress("MagicNumber")
fun Position.toLocation(world: World): Location =
    Location(world, this.x.toDouble() + 0.5, this.y?.toDouble() ?: 0.0, this.z.toDouble() + 0.5)
