package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.Position3D
import java.time.Instant
import java.util.UUID

/**
 * Represents a guild's physical chest for item storage.
 * @property id The unique identifier for the guild chest.
 * @property guildId The ID of the guild that owns this chest.
 * @property worldId The ID of the world where the chest is located.
 * @property location The coordinates where the chest is placed.
 * @property chestSize The current size of the chest (number of slots).
 * @property maxSize The maximum size this chest can be expanded to.
 * @property isLocked Whether the chest is currently locked.
 * @property lastAccessed When the chest was last accessed.
 * @property createdAt When the chest was created.
 * @property metadata Additional metadata for the chest.
 */
data class GuildChest(
    val id: UUID,
    val guildId: UUID,
    val worldId: UUID,
    val location: Position3D,
    val chestSize: Int = 54,
    val maxSize: Int = 270,
    val isLocked: Boolean = false,
    val lastAccessed: Instant,
    val createdAt: Instant,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Checks if the chest can be expanded.
     */
    fun canExpand(): Boolean = chestSize < maxSize

    /**
     * Gets the available slots in the chest.
     */
    fun getAvailableSlots(): Int = chestSize

    /**
     * Checks if the chest is accessible to a player.
     */
    fun isAccessible(playerId: UUID, guildId: UUID): Boolean {
        return this.guildId == guildId && !isLocked
    }
}

/**
 * Represents the contents of a guild chest.
 * @property chestId The ID of the chest.
 * @property items Map of slot index to item stack data.
 * @property lastUpdated When the contents were last updated.
 */
data class GuildChestContents(
    val chestId: UUID,
    val items: Map<Int, String>, // Slot index -> Serialized ItemStack
    val lastUpdated: Instant
)

/**
 * Represents a guild chest access log entry.
 * @property id The unique identifier for the access log.
 * @property chestId The ID of the chest that was accessed.
 * @property playerId The ID of the player who accessed the chest.
 * @property action The action performed (OPEN, DEPOSIT, WITHDRAW, etc.).
 * @property timestamp When the access occurred.
 * @property itemType The type of item involved (if applicable).
 * @property itemAmount The amount of items involved (if applicable).
 * @property metadata Additional metadata for the access.
 */
data class GuildChestAccessLog(
    val id: UUID,
    val chestId: UUID,
    val playerId: UUID,
    val action: GuildChestAction,
    val timestamp: Instant,
    val itemType: String? = null,
    val itemAmount: Int = 0,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Actions that can be performed on a guild chest.
 */
enum class GuildChestAction {
    OPEN,
    DEPOSIT,
    WITHDRAW,
    BREAK_ATTEMPT,
    BREAK_SUCCESS,
    LOCK,
    UNLOCK,
    EXPAND
}

/**
 * Configuration for guild chest access permissions.
 * @property allowPhysicalAccess Whether physical access to chests is allowed.
 * @property allowGuiAccess Whether GUI access to chests is allowed.
 * @property requirePermission Whether permission is required for access.
 * @property accessPermission The permission required for access.
 * @property allowedRegions Regions where chest access is allowed (WorldGuard integration).
 */
data class GuildChestAccessConfig(
    val allowPhysicalAccess: Boolean = true,
    val allowGuiAccess: Boolean = true,
    val requirePermission: Boolean = false,
    val accessPermission: String = "lumaguilds.chest.access",
    val allowedRegions: Set<String> = emptySet(),
    val deniedRegions: Set<String> = setOf("spawn", "pvp", "shop")
)
