package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.GuildVaultRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.VaultResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildVaultLocation
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.VaultStatus
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Bukkit implementation of GuildVaultService.
 * Manages physical guild vault chests and item storage.
 */
class GuildVaultServiceBukkit(
    private val guildRepository: GuildRepository,
    private val vaultRepository: GuildVaultRepository,
    private val memberRepository: MemberRepository,
    private val configService: ConfigService
) : GuildVaultService {

    private val logger = LoggerFactory.getLogger(GuildVaultServiceBukkit::class.java)

    // Cache for vault location to guild mapping
    private val vaultLocationCache = mutableMapOf<String, UUID>()

    override fun placeVaultChest(guild: Guild, location: Location, player: Player): VaultResult<Guild> {
        // Check if guild already has a vault
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
            logger.info("Guild ${guild.name} placed vault at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
            VaultResult.Success(updatedGuild)
        } else {
            VaultResult.Failure("Failed to save vault location to database")
        }
    }

    override fun removeVaultChest(guild: Guild, dropItems: Boolean): VaultResult<Guild> {
        if (guild.vaultChestLocation == null) {
            return VaultResult.Failure("Guild does not have a vault placed")
        }

        // Drop items if requested
        if (dropItems) {
            dropAllVaultItems(guild)
        }

        // Clear vault items from database
        vaultRepository.clearVault(guild.id)

        // Remove chest block
        val location = getVaultLocation(guild)
        if (location != null) {
            val block = location.block
            if (block.type == Material.CHEST) {
                block.type = Material.AIR
            }
            // Remove from cache
            vaultLocationCache.remove(locationKey(location))
        }

        // Update guild
        val updatedGuild = guild.copy(
            vaultChestLocation = null,
            vaultStatus = VaultStatus.UNAVAILABLE
        )

        return if (guildRepository.update(updatedGuild)) {
            logger.info("Guild ${guild.name} vault removed")
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

        // Get vault items
        val items = vaultRepository.getVaultInventory(guild.id)
        val capacity = getCapacityForLevel(guild.level)

        // Create inventory GUI
        val inventory = Bukkit.createInventory(
            null,
            capacity,
            "§6${guild.name} Vault - Level ${guild.level}"
        )

        // Fill inventory with items
        items.forEach { (slot, item) ->
            if (slot < capacity) {
                inventory.setItem(slot, item)
            }
        }

        // Open for player
        player.openInventory(inventory)
        logger.debug("${player.name} opened vault for guild ${guild.name}")

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

        // Drop all items at vault location
        items.values.forEach { item ->
            world.dropItemNaturally(location, item)
        }

        // Clear stored vault contents after dropping to prevent duplication on re-placement
        vaultRepository.clearVault(guild.id)

        logger.info("Dropped ${items.size} items from guild ${guild.name} vault and cleared stored inventory")
        return VaultResult.Success(Unit)
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

        // TODO: Check rank permissions when RankService is integrated
        // For now, allow all guild members
        return true
    }

    /**
     * Generates a cache key for a location.
     */
    private fun locationKey(location: Location): String {
        return "${location.world?.uid}:${location.blockX}:${location.blockY}:${location.blockZ}"
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
}
