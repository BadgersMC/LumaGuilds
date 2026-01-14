package net.lumalyte.lg.infrastructure.hytale.adapters

import com.hypixel.hytale.math.vector.Vector3d
import com.hypixel.hytale.math.vector.Vector3f
import com.hypixel.hytale.math.vector.Transform
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import net.lumalyte.lg.domain.values.Position3D
import kotlin.math.floor

/**
 * Converts Hytale Vector3d to domain Position3D (block coordinates)
 */
fun Vector3d.toPosition3D(): Position3D {
    return Position3D(
        x = floor(this.x).toInt(),
        y = floor(this.y).toInt(),
        z = floor(this.z).toInt()
    )
}

/**
 * Converts domain Position3D to Hytale Vector3d (center of block)
 */
fun Position3D.toVector3d(): Vector3d {
    return Vector3d(
        this.x.toDouble() + 0.5,  // Center of block
        this.y.toDouble(),
        this.z.toDouble() + 0.5
    )
}

/**
 * Converts TransformComponent to domain Position3D
 */
fun TransformComponent.toPosition3D(): Position3D {
    return this.position.toPosition3D()
}

/**
 * Creates a Transform from domain Position3D (no rotation)
 */
fun Position3D.toTransform(): Transform {
    return Transform(this.toVector3d())
}

/**
 * Creates a Transform from domain Position3D with rotation
 * NOTE: Hytale uses RADIANS, domain would use degrees if it had rotation
 */
fun Position3D.toTransform(yawRadians: Float, pitchRadians: Float): Transform {
    return Transform(
        this.toVector3d(),
        Vector3f(pitchRadians, yawRadians, 0f)
    )
}
