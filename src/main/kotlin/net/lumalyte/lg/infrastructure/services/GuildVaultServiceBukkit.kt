package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.VaultInventoryManager
import net.lumalyte.lg.application.services.VaultResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildVaultLocation
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.VaultStatus
import net.lumalyte.lg.interaction.inventory.VaultInventoryHolder
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Bukkit implementation of GuildVaultService.
 * Manages physical guild vault chests and item storage.
 * Integrates with VaultInventoryManager for the new vault system.
 */
class GuildVaultServiceBukkit(
    private val plugin: JavaPlugin,
    private val guildRepository: GuildRepository,
    private val vaultRepository: GuildVaultRepository,
    private val memberRepository: MemberRepository,
    private val configService: ConfigService,
    private val vaultInventoryManager: VaultInventoryManager,
    private val hologramService: VaultHologramService,
    private val rankService: RankService
) : GuildVaultService {

    private val logger = LoggerFactory.getLogger(GuildVaultServiceBukkit::class.java)

    // Cache for vault location to guild mapping
    private val vaultLocationCache = mutableMapOf<String, UUID>()

    // Persistent Data Container keys for marking vault chests
    private val vaultGuildIdKey = NamespacedKey(plugin, "vault_guild_id")
    private val vaultMarkerKey = NamespacedKey(plugin, "vault_marker")

    override fun placeVaultChest(guild: Guild, location: Location, player: Player): VaultResult<Guild> {
        // Check if guild already has a vault placed (but allow replacing if UNAVAILABLE)
        if (guild.vaultStatus == VaultStatus.AVAILABLE) {
            return VaultResult.Failure("Guild already has a vault placed. Break it first to move it.")
        }

        // Validate location
        val validationResult = isValidVaultLocation(location, guild)
        if (validationResult is VaultResult.Failure) {
            return VaultResult.Failure(validationResult.message)
        }

        // Place chest block
        val block = location.block
        if (block.type != Material.AIR && block.type != Material.CHEST) {
            block.type = Material.CHEST
        }

        // Add persistent metadata to the chest block
        val state = block.state
        if (state is Chest) {
            val pdc = state.persistentDataContainer
            pdc.set(vaultGuildIdKey, PersistentDataType.STRING, guild.id.toString())
            pdc.set(vaultMarkerKey, PersistentDataType.BYTE, 1.toByte())
            state.update()
            logger.debug("Added vault metadata to chest at ${location.blockX}, ${location.blockY}, ${location.blockZ} for guild ${guild.name}")
        } else {
            logger.warn("Block at vault location is not a chest! Type: ${block.type}")
        }

        // Update guild with vault location
        val vaultLocation = GuildVaultLocation(
            worldId = location.world?.uid ?: return VaultResult.Failure("Invalid world"),
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ
        )

        val updatedGuild = guild.copy(
            vaultChestLocation = vaultLocation,
            vaultStatus = VaultStatus.AVAILABLE
        )

        return if (guildRepository.update(updatedGuild)) {
            // Update cache
            vaultLocationCache[locationKey(location)] = guild.id

            // Create hologram for the vault
            hologramService.createHologram(location, updatedGuild)

            // Note: Vault contents are NOT cleared here
            // If the vault was UNAVAILABLE, the existing contents remain in the database
            // and will be loaded when the vault is next opened

            logger.info("Guild ${guild.name} placed vault at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
            if (vaultRepository.hasVaultItems(guild.id)) {
                logger.info("Guild ${guild.name} vault has existing contents that will be restored")
            }

            VaultResult.Success(updatedGuild)
        } else {
            VaultResult.Failure("Failed to save vault location to database")
        }
    }

    override fun removeVaultChest(guild: Guild, dropItems: Boolean): VaultResult<Guild> {
        if (guild.vaultChestLocation == null) {
            return VaultResult.Failure("Guild does not have a vault placed")
        }

        // Drop items if requested (NOT recommended - items should persist)
        if (dropItems) {
            logger.warn("Dropping vault items for guild ${guild.name} - this is NOT recommended!")
            dropAllVaultItems(guild)
            // Also clear from database if dropping
            vaultRepository.clearVault(guild.id)
            // Clear from in-memory cache to prevent duplication
            vaultInventoryManager.clearCache(guild.id)
        }

        // NOTE: We do NOT clear vault items from database by default
        // Items remain in the database so they can be restored when a new vault is placed

        // Flush any pending writes for this vault
        vaultInventoryManager.forceFlush(guild.id)

        // Remove chest block and hologram
        val location = getVaultLocation(guild)
        if (location != null) {
            val block = location.block
            if (block.type == Material.CHEST) {
                block.type = Material.AIR
            }
            // Remove hologram
            hologramService.removeHologram(location)
            // Remove from cache
            vaultLocationCache.remove(locationKey(location))
        }

        // Update guild status to UNAVAILABLE but keep vault location reference
        // This allows players to see where the vault was
        val updatedGuild = guild.copy(
            vaultStatus = VaultStatus.UNAVAILABLE
            // vaultChestLocation is intentionally NOT cleared
        )

        return if (guildRepository.update(updatedGuild)) {
            logger.info("Guild ${guild.name} vault status set to UNAVAILABLE (items preserved)")
            VaultResult.Success(updatedGuild)
        } else {
            VaultResult.Failure("Failed to update guild vault status")
        }
    }

    override fun openVaultInventory(player: Player, guild: Guild): VaultResult<Unit> {
        if (guild.vaultStatus != VaultStatus.AVAILABLE) {
            return VaultResult.Failure("Guild vault is not available")
        }

        if (guild.vaultLocked) {
            return VaultResult.Failure("§c⚠ VAULT LOCKED: This vault has been locked by a server administrator for security reasons. Contact staff for more information.")
        }

        if (!hasVaultPermission(player, guild, requireWithdraw = false)) {
            return VaultResult.Failure("You don't have permission to access the vault")
        }

        // Get capacity for guild level
        val capacity = getCapacityForLevel(guild.level)

        // Get or create the SHARED inventory for this guild
        // All players viewing the same guild vault share this single Inventory instance
        // This eliminates synchronization issues - Bukkit handles multi-viewer updates natively
        val sharedInventory = vaultInventoryManager.getOrCreateSharedInventory(
            guild.id,
            guild.name,
            capacity
        )

        // Open the SHARED inventory for the player
        // The VaultInventoryListener will handle all click events and viewer registration
        player.openInventory(sharedInventory)
        logger.debug("${player.name} opened shared vault for guild ${guild.name}")

        return VaultResult.Success(Unit)
    }

    override fun getVaultInventory(guild: Guild): Map<Int, ItemStack> {
        return vaultRepository.getVaultInventory(guild.id)
    }

    override fun updateVaultInventory(guild: Guild, items: Map<Int, ItemStack>): VaultResult<Unit> {
        return if (vaultRepository.saveVaultInventory(guild.id, items)) {
            VaultResult.Success(Unit)
        } else {
            VaultResult.Failure("Failed to update vault inventory")
        }
    }

    override fun calculateGoldValue(items: List<ItemStack>): Int {
        var total = 0
        for (item in items) {
            when (item.type) {
                Material.GOLD_NUGGET -> total += item.amount * 1
                Material.GOLD_INGOT -> total += item.amount * 9
                Material.GOLD_BLOCK -> total += item.amount * 81
                else -> {} // Ignore non-gold items
            }
        }
        return total
    }

    override fun getCapacityForLevel(level: Int): Int {
        // Default capacity configuration
        return when {
            level == 1 -> 9      // 1 row
            level in 2..5 -> 18  // 2 rows
            level in 6..10 -> 27 // 3 rows
            level in 11..15 -> 36 // 4 rows
            level in 16..20 -> 45 // 5 rows
            else -> 54           // 6 rows (full double chest)
        }
    }

    override fun isValidVaultLocation(location: Location, guild: Guild): VaultResult<Boolean> {
        val world = location.world ?: return VaultResult.Failure("Invalid world")

        // Check if location is in loaded chunk
        if (!world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) {
            return VaultResult.Failure("Chunk not loaded")
        }

        // If claims are disabled, allow placement anywhere
        val config = configService.loadConfig()
        if (!config.claimsEnabled) {
            logger.debug("Claims disabled - allowing vault placement anywhere")
            return VaultResult.Success(true)
        }

        // TODO: Add claim validation when claims are enabled
        // When claims are enabled, validate that the location is within a guild claim
        // For now, allow placement anywhere even when claims are enabled
        logger.debug("Claims enabled but validation not yet implemented - allowing vault placement")
        return VaultResult.Success(true)
    }

    override fun getVaultLocation(guild: Guild): Location? {
        val vaultLoc = guild.vaultChestLocation ?: return null
        val world = Bukkit.getWorld(vaultLoc.worldId) ?: return null
        return Location(world, vaultLoc.x.toDouble(), vaultLoc.y.toDouble(), vaultLoc.z.toDouble())
    }

    override fun getGuildForVaultChest(location: Location): Guild? {
        // Check cache first
        val guildId = vaultLocationCache[locationKey(location)]
        if (guildId != null) {
            return guildRepository.getById(guildId)
        }

        // Search all guilds (slow, should be rare)
        for (guild in guildRepository.getAll()) {
            if (guild.vaultChestLocation != null) {
                val vaultLoc = guild.vaultChestLocation
                if (vaultLoc.worldId == location.world?.uid &&
                    vaultLoc.x == location.blockX &&
                    vaultLoc.y == location.blockY &&
                    vaultLoc.z == location.blockZ
                ) {
                    // Update cache
                    vaultLocationCache[locationKey(location)] = guild.id
                    return guild
                }
            }
        }

        return null
    }

    override fun updateVaultStatus(guild: Guild, status: VaultStatus): Guild {
        val updatedGuild = guild.copy(vaultStatus = status)
        guildRepository.update(updatedGuild)
        return updatedGuild
    }

    override fun dropAllVaultItems(guild: Guild): VaultResult<Unit> {
        val location = getVaultLocation(guild) ?: return VaultResult.Failure("Vault location not found")
        val items = vaultRepository.getVaultInventory(guild.id)

        val world = location.world ?: return VaultResult.Failure("World not found")

        // Drop all physical items at vault location
        items.values.forEach { item ->
            world.dropItemNaturally(location, item)
        }

        // Drop gold balance as physical currency items
        val goldBalance = vaultRepository.getGoldBalance(guild.id)
        if (goldBalance > 0) {
            val currencyMaterial = getCurrencyMaterial()
            val goldItems = net.lumalyte.lg.application.utilities.GoldBalanceButton.convertToItems(goldBalance)

            goldItems.forEach { goldItem ->
                world.dropItemNaturally(location, goldItem)
            }

            logger.info("Dropped $goldBalance currency (${goldItems.size} item stacks) as $currencyMaterial from guild ${guild.name} vault")
        }

        // Clear stored vault contents after dropping to prevent duplication on re-placement
        vaultRepository.clearVault(guild.id)

        logger.info("Dropped ${items.size} items from guild ${guild.name} vault and cleared stored inventory")
        return VaultResult.Success(Unit)
    }

    /**
     * Gets the configured currency material for the vault.
     */
    private fun getCurrencyMaterial(): Material {
        val config = configService.loadConfig()
        val materialName = config.vault.physicalCurrencyMaterial
        return try {
            Material.valueOf(materialName.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid physical_currency_material '$materialName' in config, defaulting to RAW_GOLD")
            Material.RAW_GOLD
        }
    }

    override fun hasVaultPermission(player: Player, guild: Guild, requireWithdraw: Boolean): Boolean {
        // Get player's member record
        val member = memberRepository.getByPlayerAndGuild(player.uniqueId, guild.id)
            ?: return false

        // Check permissions based on requirement
        val requiredPermission = if (requireWithdraw) {
            RankPermission.WITHDRAW_FROM_VAULT
        } else {
            RankPermission.ACCESS_VAULT
        }

        // Check if player has the required permission through their rank
        return rankService.hasPermission(player.uniqueId, guild.id, requiredPermission)
    }

    /**
     * Generates a cache key for a location.
     */
    private fun locationKey(location: Location): String {
        return "${location.world?.uid}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    /**
     * Checks if a block is a vault chest by reading its PDC metadata.
     *
     * @param block The block to check.
     * @return The guild ID if it's a vault chest, null otherwise.
     */
    fun getVaultGuildIdFromBlock(block: org.bukkit.block.Block): UUID? {
        if (block.type != Material.CHEST) return null

        val state = block.state
        if (state !is Chest) return null

        val pdc = state.persistentDataContainer
        if (!pdc.has(vaultMarkerKey, PersistentDataType.BYTE)) return null

        val guildIdString = pdc.get(vaultGuildIdKey, PersistentDataType.STRING) ?: return null
        return try {
            UUID.fromString(guildIdString)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid guild ID in vault chest PDC: $guildIdString")
            null
        }
    }

    /**
     * Checks if a block is a vault chest.
     *
     * @param block The block to check.
     * @return true if the block is a vault chest.
     */
    fun isVaultChest(block: org.bukkit.block.Block): Boolean {
        return getVaultGuildIdFromBlock(block) != null
    }

    /**
     * Rebuilds the vault location cache on service initialization.
     */
    fun rebuildCache() {
        vaultLocationCache.clear()
        for (guild in guildRepository.getAll()) {
            if (guild.vaultChestLocation != null && guild.vaultStatus == VaultStatus.AVAILABLE) {
                val location = getVaultLocation(guild)
                if (location != null) {
                    vaultLocationCache[locationKey(location)] = guild.id
                }
            }
        }
        logger.info("Rebuilt vault location cache with ${vaultLocationCache.size} entries")
    }

    override fun restoreAllVaultChests(): Int {
        var restoredCount = 0
        var foundIntactCount = 0
        var recreatedCount = 0
        val guilds = guildRepository.getAll()

        logger.info("════════════════════════════════════════════════════════")
        logger.info("Starting vault chest restoration check...")
        logger.info("Total guilds in database: ${guilds.size}")
        logger.info("════════════════════════════════════════════════════════")

        // Log all guild vault statuses for debugging
        var availableCount = 0
        var unavailableCount = 0
        var neverPlacedCount = 0
        var noLocationCount = 0

        guilds.forEach { guild ->
            when {
                guild.vaultStatus == VaultStatus.AVAILABLE && guild.vaultChestLocation != null -> {
                    logger.info("  ✓ Guild '${guild.name}': AVAILABLE with location")
                    availableCount++
                }
                guild.vaultStatus == VaultStatus.AVAILABLE && guild.vaultChestLocation == null -> {
                    logger.warn("  ⚠ Guild '${guild.name}': AVAILABLE but NO LOCATION")
                    noLocationCount++
                }
                guild.vaultStatus == VaultStatus.UNAVAILABLE -> {
                    logger.info("  - Guild '${guild.name}': UNAVAILABLE")
                    unavailableCount++
                }
                guild.vaultStatus == VaultStatus.NEVER_PLACED -> {
                    neverPlacedCount++
                }
            }
        }

        logger.info("Vault status summary:")
        logger.info("  • Available with location: $availableCount")
        logger.info("  • Available but no location: $noLocationCount")
        logger.info("  • Unavailable: $unavailableCount")
        logger.info("  • Never placed: $neverPlacedCount")
        logger.info("════════════════════════════════════════════════════════")

        for (guild in guilds) {
            // Only restore vaults with AVAILABLE status
            if (guild.vaultStatus != VaultStatus.AVAILABLE) {
                continue
            }

            val vaultLocation = guild.vaultChestLocation
            if (vaultLocation == null) {
                logger.warn("Guild '${guild.name}' has AVAILABLE status but null location - skipping")
                continue
            }
            val world = Bukkit.getWorld(vaultLocation.worldId)

            if (world == null) {
                logger.warn("⚠ Guild '${guild.name}' vault world ${vaultLocation.worldId} not found - skipping")
                continue
            }

            val location = Location(world, vaultLocation.x.toDouble(), vaultLocation.y.toDouble(), vaultLocation.z.toDouble())

            // Ensure chunk is loaded before checking chest
            if (!world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)) {
                world.loadChunk(location.blockX shr 4, location.blockZ shr 4)
            }

            // Check if chest exists and restore if needed
            val block = location.block
            val chestExisted = block.type == Material.CHEST

            if (!chestExisted) {
                // Chest is missing - recreate it
                block.type = Material.CHEST
                logger.info("✓ Recreated missing vault chest for guild '${guild.name}' at (${location.blockX}, ${location.blockY}, ${location.blockZ})")
                recreatedCount++
            } else {
                logger.debug("✓ Found intact vault chest for guild '${guild.name}' at (${location.blockX}, ${location.blockY}, ${location.blockZ})")
                foundIntactCount++
            }

            // Add/update persistent metadata to the chest block
            val state = block.state
            if (state is Chest) {
                val pdc = state.persistentDataContainer
                val hasMetadata = pdc.has(vaultMarkerKey, PersistentDataType.BYTE)

                pdc.set(vaultGuildIdKey, PersistentDataType.STRING, guild.id.toString())
                pdc.set(vaultMarkerKey, PersistentDataType.BYTE, 1.toByte())
                state.update()

                if (!hasMetadata) {
                    logger.debug("  → Added missing metadata to chest")
                }
            }

            // Update cache
            vaultLocationCache[locationKey(location)] = guild.id
            restoredCount++
        }

        logger.info("════════════════════════════════════════════════════════")
        logger.info("Vault restoration complete:")
        logger.info("  • Total vaults processed: $restoredCount")
        logger.info("  • Found intact: $foundIntactCount")
        logger.info("  • Recreated missing: $recreatedCount")
        logger.info("════════════════════════════════════════════════════════")

        return restoredCount
    }
}
