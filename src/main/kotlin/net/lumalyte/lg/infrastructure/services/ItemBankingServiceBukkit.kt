package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildChestRepository
import net.lumalyte.lg.application.persistence.GuildChestAccessLogRepository
import net.lumalyte.lg.application.services.AuditService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.DiplomacyService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ItemBankingService
import net.lumalyte.lg.config.ItemBankingConfig
import net.lumalyte.lg.domain.entities.GuildChest
import net.lumalyte.lg.domain.entities.GuildChestAccessLog
import net.lumalyte.lg.domain.entities.GuildChestAction
import net.lumalyte.lg.domain.values.Position3D
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class ItemBankingServiceBukkit(
    private val guildChestRepository: GuildChestRepository,
    private val guildChestAccessLogRepository: GuildChestAccessLogRepository,
    private val guildService: GuildService,
    private val diplomacyService: DiplomacyService,
    private val configService: ConfigService,
    private val auditService: AuditService
) : ItemBankingService {

    private val logger = LoggerFactory.getLogger(ItemBankingServiceBukkit::class.java)

    override fun createGuildChest(guildId: UUID, worldId: UUID, x: Int, y: Int, z: Int, playerId: UUID): GuildChest? {
        try {
            val config = getItemBankingConfig()

            // Check if guild already has a chest at this location
            if (guildChestRepository.hasChestAtLocation(guildId, worldId, x, y, z)) {
                logger.warn("Guild $guildId already has a chest at location $worldId:$x,$y,$z")
                return null
            }

            // Check if player has permission to create chests for their guild
            val playerGuilds = guildService.getPlayerGuildIds(playerId)
            if (!playerGuilds.contains(guildId)) {
                logger.warn("Player $playerId is not a member of guild $guildId")
                return null
            }

            // Check guild level requirements
            val maxSize = getMaxChestSize(guildId)
            val defaultSize = config.defaultChestSize.coerceAtMost(maxSize)

            // Create the guild chest
            val chest = GuildChest(
                id = UUID.randomUUID(),
                guildId = guildId,
                worldId = worldId,
                location = Position3D(x, y, z),
                chestSize = defaultSize,
                maxSize = maxSize,
                lastAccessed = Instant.now(),
                createdAt = Instant.now()
            )

            return if (guildChestRepository.add(chest)) {
                logger.info("Created guild chest for guild $guildId at $worldId:$x,$y,$z")

                // Audit the chest creation
                auditService.recordChestCreation(
                    actorId = playerId,
                    guildId = guildId,
                    chestId = chest.id,
                    location = "World: $worldId, X:$x, Y:$y, Z:$z"
                )

                chest
            } else {
                logger.error("Failed to save guild chest to database")
                null
            }
        } catch (e: Exception) {
            logger.error("Error creating guild chest for guild $guildId", e)
            return null
        }
    }

    override fun getGuildChestAt(worldId: UUID, x: Int, y: Int, z: Int): GuildChest? {
        try {
            return guildChestRepository.getByLocation(worldId, x, y, z)
        } catch (e: Exception) {
            logger.error("Error getting guild chest at $worldId:$x,$y,$z", e)
            return null
        }
    }

    override fun getGuildChests(guildId: UUID): List<GuildChest> {
        try {
            return guildChestRepository.getByGuild(guildId)
        } catch (e: Exception) {
            logger.error("Error getting guild chests for guild $guildId", e)
            return emptyList()
        }
    }

    override fun removeGuildChest(chestId: UUID, playerId: UUID): Boolean {
        try {
            val chest = guildChestRepository.getById(chestId)
            if (chest == null) {
                logger.warn("Guild chest $chestId not found for removal")
                return false
            }

            // Check if player has permission to remove chests for their guild
            val playerGuilds = guildService.getPlayerGuildIds(playerId)
            if (!playerGuilds.contains(chest.guildId)) {
                logger.warn("Player $playerId does not have permission to remove chest $chestId")
                return false
            }

            return guildChestRepository.remove(chestId)
        } catch (e: Exception) {
            logger.error("Error removing guild chest $chestId", e)
            return false
        }
    }

    override fun canAccessGuildChest(playerId: UUID, guildId: UUID, worldId: UUID, x: Int, y: Int, z: Int): Boolean {
        try {
            val config = getItemBankingConfig()

            // Check if item banking is enabled
            if (!config.enabled) {
                return false
            }

            // Check if physical access is allowed
            if (!config.allowPhysicalAccess) {
                return false
            }

            // Get the guild chest
            val chest = guildChestRepository.getByLocation(worldId, x, y, z)
            if (chest == null) {
                return false
            }

            // Check if the chest belongs to the guild
            if (chest.guildId != guildId) {
                return false
            }

            // Check if the chest is locked
            if (chest.isLocked) {
                return false
            }

            // Check if the player is a member of the guild
            val playerGuilds = guildService.getPlayerGuildIds(playerId)
            if (!playerGuilds.contains(guildId)) {
                return false
            }

            // Check if the location is in a denied region (WorldGuard integration)
            if (config.worldGuardIntegration && isInDeniedRegion(worldId, x, y, z)) {
                return false
            }

            // Check permission requirement
            if (config.requirePermissionForAccess) {
                // This would need to be implemented with permission checking
                return true // Placeholder
            }

            return true
        } catch (e: Exception) {
            logger.error("Error checking guild chest access for player $playerId", e)
            return false
        }
    }

    override fun logChestAccess(chestId: UUID, playerId: UUID, action: GuildChestAction, itemType: String?, itemAmount: Int): Boolean {
        try {
            val log = GuildChestAccessLog(
                id = UUID.randomUUID(),
                chestId = chestId,
                playerId = playerId,
                action = action,
                timestamp = Instant.now(),
                itemType = itemType,
                itemAmount = itemAmount
            )

            return guildChestAccessLogRepository.add(log)
        } catch (e: Exception) {
            logger.error("Error logging chest access for chest $chestId", e)
            return false
        }
    }

    override fun getChestAccessLogs(chestId: UUID): List<GuildChestAccessLog> {
        try {
            return guildChestAccessLogRepository.getByChest(chestId)
        } catch (e: Exception) {
            logger.error("Error getting access logs for chest $chestId", e)
            return emptyList()
        }
    }

    override fun getPlayerAccessLogs(playerId: UUID): List<GuildChestAccessLog> {
        try {
            return guildChestAccessLogRepository.getByPlayer(playerId)
        } catch (e: Exception) {
            logger.error("Error getting access logs for player $playerId", e)
            return emptyList()
        }
    }

    override fun getSuspiciousAccessLogs(): List<GuildChestAccessLog> {
        try {
            return guildChestAccessLogRepository.getSuspiciousActivities()
        } catch (e: Exception) {
            logger.error("Error getting suspicious access logs", e)
            return emptyList()
        }
    }

    override fun isInDeniedRegion(worldId: UUID, x: Int, y: Int, z: Int): Boolean {
        // This would need WorldGuard integration
        // For now, return false (allow access)
        return false
    }

    override fun getMaxChestSize(guildId: UUID): Int {
        try {
            val guild = guildService.getGuild(guildId)
            if (guild == null) {
                return getItemBankingConfig().defaultChestSize
            }

            val config = getItemBankingConfig()
            val baseSize = config.defaultChestSize

            // Calculate additional slots from guild level
            var additionalSlots = 0
            for ((level, slots) in config.unlockLevels) {
                if (guild.level >= level) {
                    additionalSlots = slots
                }
            }

            return (baseSize + additionalSlots).coerceAtMost(config.maxChestSize)
        } catch (e: Exception) {
            logger.error("Error calculating max chest size for guild $guildId", e)
            return getItemBankingConfig().defaultChestSize
        }
    }

    override fun autoEnemyGuilds(victimGuildId: UUID, attackerGuildId: UUID, reason: String): Boolean {
        try {
            val config = getItemBankingConfig()

            if (!config.autoEnemyOnChestBreak) {
                return false
            }

            // Check if guilds are already enemies
            val existingRelations = diplomacyService.getRelations(victimGuildId)
            val alreadyEnemies = existingRelations.any {
                it.targetGuildId == attackerGuildId &&
                it.type.name == "ENEMY" &&
                it.isActive()
            }

            if (alreadyEnemies) {
                logger.info("Guilds $victimGuildId and $attackerGuildId are already enemies")
                return true
            }

            // Create enemy relation
            val success = diplomacyService.createRelation(victimGuildId, attackerGuildId, net.lumalyte.lg.domain.entities.DiplomaticRelationType.ENEMY)

            if (success) {
                // Log the diplomatic event
                diplomacyService.logDiplomaticEvent(
                    victimGuildId,
                    attackerGuildId,
                    "auto_enemy_chest_break",
                    reason
                )
                logger.info("Auto-enemied guilds $victimGuildId and $attackerGuildId due to chest break")
            }

            return success
        } catch (e: Exception) {
            logger.error("Error auto-enemying guilds $victimGuildId and $attackerGuildId", e)
            return false
        }
    }

    override fun getItemBankingConfig(): ItemBankingConfig {
        return configService.loadConfig().itemBanking
    }

    override fun getItemValue(itemType: String, amount: Int): Double {
        val config = getItemBankingConfig()
        return config.currencyItems[itemType] ?: 0.0 * amount
    }

    override fun getDefaultCurrencyItem(): String {
        return getItemBankingConfig().defaultCurrencyItem
    }

    override fun updateLastAccessed(chestId: UUID, accessTime: java.time.Instant): Boolean {
        return guildChestRepository.updateLastAccessed(chestId, accessTime)
    }
}