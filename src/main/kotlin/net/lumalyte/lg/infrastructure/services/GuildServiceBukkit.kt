package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.DepartureReason
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildHomes
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.events.GuildBannerSetEvent
import net.lumalyte.lg.domain.events.GuildCreatedEvent
import net.lumalyte.lg.domain.events.GuildDisbandedEvent
import net.lumalyte.lg.domain.events.GuildHomeSetEvent
import net.lumalyte.lg.domain.events.GuildTrackingChangedEvent
import net.lumalyte.lg.utils.serializeToString
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class GuildServiceBukkit(
    private val guildRepository: GuildRepository,
    private val rankRepository: RankRepository,
    private val memberRepository: MemberRepository,
    private val rankService: net.lumalyte.lg.application.services.RankService,
    private val memberService: net.lumalyte.lg.application.services.MemberService,
    private val nexoEmojiService: NexoEmojiService,
    private val vaultService: net.lumalyte.lg.application.services.GuildVaultService,
    private val hologramService: net.lumalyte.lg.infrastructure.services.VaultHologramService,
    private val relationRepository: RelationRepository,
    private val historyRepository: MembershipHistoryRepository
) : GuildService {

    private val logger = LoggerFactory.getLogger(GuildServiceBukkit::class.java)

    companion object {
        // Allow only alphanumerics and spaces. Blocks color codes (&r, etc.),
        // punctuation [&!@#$%^&*()"',.] and any other non-[a-zA-Z0-9 ] char.
        private val GUILD_NAME_ALLOWED = Regex("^[A-Za-z0-9 ]+$")
    }

    private fun isGuildNameValid(name: String): Boolean {
        if (name.isBlank() || name.length > 32) return false
        return GUILD_NAME_ALLOWED.matches(name)
    }

    override fun createGuild(name: String, ownerId: UUID, banner: String?): Guild? {
        // Validate guild name
        if (!isGuildNameValid(name)) {
            logger.warn("Invalid guild name: $name")
            return null
        }
        
        // Check if name is already taken
        if (guildRepository.isNameTaken(name)) {
            logger.warn("Guild name already taken: $name")
            return null
        }
        
        // Create guild
        val guildId = UUID.randomUUID()
        val guild = Guild(
            id = guildId,
            name = name,
            banner = banner,
            homes = GuildHomes.EMPTY,
            level = 1,
            bankBalance = 0,
            mode = GuildMode.HOSTILE,
            modeChangedAt = null,
            createdAt = Instant.now()
        )
        
        if (guildRepository.add(guild)) {
            // Create default ranks
            if (rankService.createDefaultRanks(guildId, ownerId)) {
                // Add owner as member with highest rank
                val highestRank = rankRepository.getHighestRank(guildId)
                if (highestRank != null) {
                    memberService.addMember(ownerId, guildId, highestRank.id)
                }

                // Fire event to create default guild channels (decoupled)
                Bukkit.getPluginManager().callEvent(GuildCreatedEvent(guild, ownerId))

                logger.info("Created guild: $name with owner: $ownerId")
                return guild
            } else {
                // Rollback guild creation if ranks couldn't be created
                guildRepository.remove(guildId)
                logger.error("Failed to create default ranks for guild: $name")
                return null
            }
        }

        logger.error("Failed to create guild: $name")
        return null
    }

    override fun disbandGuild(guildId: UUID, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // System UUID for admin/console operations
        val systemUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")

        // Check if actor has permission to disband guild (bypass for system/admin operations)
        if (actorId != systemUuid && !hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to disband guild $guildId without permission")
            return false
        }

        // Clean up vault chest and hologram if exists
        val vaultLocation = vaultService.getVaultLocation(guild)
        if (vaultLocation != null) {
            // Remove hologram first
            hologramService.removeHologram(vaultLocation)
            logger.info("Removed vault hologram for disbanded guild ${guild.name}")

            // Remove vault chest and data (with items dropped)
            when (val removeResult = vaultService.removeVaultChest(guild, dropItems = true)) {
                is net.lumalyte.lg.application.services.VaultResult.Success -> {
                    logger.info("Removed vault chest for disbanded guild ${guild.name}")
                }
                is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                    logger.warn("Failed to remove vault chest during disbandment: ${removeResult.message}")
                }
            }
        }

        // Capture member IDs before removal so the disbandment event carries them
        val memberIds = memberService.getGuildMembers(guildId).map { it.playerId }.toSet()

        // Remove all members
        memberRepository.removeByGuild(guildId)

        // Close membership history stints for all members (guild disbanded)
        memberIds.forEach { memberId ->
            historyRepository.closeStint(memberId, guildId, DepartureReason.DISBANDED)
        }

        // Remove all ranks
        rankRepository.removeByGuild(guildId)

        // Remove all relations to prevent stale entries in other guilds' ally/enemy lists
        val removedRelations = relationRepository.removeByGuild(guildId)
        if (removedRelations > 0) {
            logger.info("Removed $removedRelations relation(s) for disbanded guild $guildId")
        }

        // Remove guild
        val result = guildRepository.remove(guildId)
        if (result) {
            logger.info("Guild $guildId disbanded by $actorId")
            Bukkit.getPluginManager().callEvent(GuildDisbandedEvent(guild, memberIds, actorId))
        }
        return result
    }
    
    override fun renameGuild(guildId: UUID, newName: String, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Check if actor has permission to rename the guild
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            logger.warn("Player $actorId attempted to rename guild $guildId without MANAGE_GUILD_SETTINGS permission")
            return false
        }

        // Validate new name
        if (!isGuildNameValid(newName)) {
            logger.warn("Invalid guild name: $newName")
            return false
        }

        // Check if new name is already taken
        if (guildRepository.isNameTaken(newName)) {
            logger.warn("Guild name already taken: $newName")
            return false
        }

        val updatedGuild = guild.copy(name = newName)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId renamed to '$newName' by $actorId")
        }
        return result
    }
    
    override fun setBanner(guildId: UUID, banner: org.bukkit.inventory.ItemStack?, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Check if actor has permission to set banner
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_BANNER)) {
            logger.warn("Player $actorId attempted to set banner for guild $guildId without permission")
            return false
        }

        // Serialize the ItemStack to string for database storage
        val bannerData = banner?.serializeToString()
        val updatedGuild = guild.copy(banner = bannerData)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            val bannerText = if (banner != null) "${banner.type.name} with patterns" else "cleared (default white banner)"
            logger.info("Guild $guildId banner set to '$bannerText' by $actorId")
            Bukkit.getPluginManager().callEvent(GuildBannerSetEvent(guildId, actorId))
        }
        return result
    }

    override fun setEmoji(guildId: UUID, emoji: String?, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false
        
        // Check if actor has permission to set emoji
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_EMOJI)) {
            logger.warn("Player $actorId attempted to set emoji for guild $guildId without permission")
            return false
        }
        
        // If emoji is provided, validate format and permissions
        emoji?.let { emojiValue ->
            // Validate emoji format
            if (!nexoEmojiService.isValidEmojiFormat(emojiValue)) {
                logger.warn("Invalid emoji format: $emojiValue")
                return false
            }
            
            // Check if player has specific emoji permission
            val player = Bukkit.getPlayer(actorId)
            if (player == null) {
                logger.warn("Player $actorId not found online")
                return false
            }
            
            if (!nexoEmojiService.hasEmojiPermission(player, emojiValue)) {
                logger.warn("Player ${player.name} does not have permission to use emoji: $emojiValue")
                return false
            }
        }
        
        val updatedGuild = guild.copy(emoji = emoji)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId emoji set to '$emoji' by $actorId")
        }
        return result
    }
    
    override fun getEmoji(guildId: UUID): String? {
        return guildRepository.getById(guildId)?.emoji
    }

    override fun setTag(guildId: UUID, tag: String?, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Check if actor has permission to set tag
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_EMOJI)) { // Reuse emoji permission for tags
            logger.warn("Player $actorId attempted to set tag for guild $guildId without permission")
            return false
        }

        // If tag is provided, validate MiniMessage format
        tag?.let { tagValue ->
            // Reject interactive MiniMessage event tags (click/hover/insertion) — defense in depth
            net.lumalyte.lg.utils.GuildTagValidator.rejectionReason(tagValue)?.let { reason ->
                logger.warn("Rejected guild tag with interactive MiniMessage tag for guild $guildId: $reason")
                return false
            }
            // Validate tag format using MiniMessage
            try {
                // We'll add MiniMessage validation in the next step
                // For now, just do basic length validation
                val visibleChars = countVisibleCharacters(tagValue)
                if (visibleChars > 32) {
                    logger.warn("Tag too long: $visibleChars visible characters (max 32)")
                    return false
                }
            } catch (e: Exception) {
            // Non-critical operation - catching all exceptions to prevent service failure
                // Tag validation - catching parsing/format errors
                logger.warn("Invalid tag format: $tagValue - ${e.message}")
                return false
            }
        }

        val updatedGuild = guild.copy(tag = tag)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId tag set to '$tag' by $actorId")
        }
        return result
    }

    override fun getTag(guildId: UUID): String? {
        return guildRepository.getById(guildId)?.tag
    }

    override fun setDescription(guildId: UUID, description: String?, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Check if actor has permission to set description
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_EMOJI)) { // Reuse emoji permission for description
            logger.warn("Player $actorId attempted to set description for guild $guildId without permission")
            return false
        }

        // Validate description length if provided
        description?.let { descValue ->
            if (descValue.length > 100) {
                logger.warn("Description too long: ${descValue.length} characters (max 100)")
                return false
            }
        }

        val updatedGuild = guild.copy(description = description)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId description set by $actorId")
        }
        return result
    }

    override fun getDescription(guildId: UUID): String? {
        return guildRepository.getById(guildId)?.description
    }
    
    override fun setHome(guildId: UUID, homeName: String, home: GuildHome, actorId: UUID): Boolean {
        try {
            val guild = guildRepository.getById(guildId) ?: return false

            // Check if actor has permission to set home
            if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
                logger.warn("Player $actorId attempted to set home for guild $guildId without permission")
                return false
            }

            // Check if the guild has available home slots
            val availableSlots = getAvailableHomeSlots(guildId)
            val currentHomes = guild.homes.size
            if (currentHomes >= availableSlots && !guild.homes.homes.containsKey(homeName)) {
                logger.warn("Player $actorId attempted to set home for guild $guildId but maximum slots ($availableSlots) reached")
                return false
            }

            val existing = guild.homes.getHome(homeName)
            val effectiveAllowed = if (existing != null) {
                existing.allowedRankIds
            } else {
                val ownerRank = rankRepository.getHighestRank(guildId)
                if (ownerRank != null) setOf(ownerRank.id) else emptySet()
            }
            val effectiveHome = home.copy(allowedRankIds = effectiveAllowed)
            val updatedHomes = guild.homes.withHome(homeName, effectiveHome)
            val updatedGuild = guild.copy(homes = updatedHomes)
            val result = guildRepository.update(updatedGuild)
            if (result) {
                logger.info("Guild $guildId home '$homeName' set to ${home.position} in world ${home.worldId} by $actorId")
                Bukkit.getPluginManager().callEvent(GuildHomeSetEvent(guildId, actorId))
            }
            return result
        } catch (e: Exception) {
            // Non-critical operation - catching all exceptions to prevent service failure
            logger.error("Error setting home for guild $guildId", e)
            return false
        }
    }
    
    override fun getHome(guildId: UUID): GuildHome? {
        return guildRepository.getById(guildId)?.homes?.defaultHome
    }

    override fun getHome(guildId: UUID, homeName: String): GuildHome? {
        return guildRepository.getById(guildId)?.homes?.getHome(homeName)
    }

    override fun getHomes(guildId: UUID): GuildHomes {
        return guildRepository.getById(guildId)?.homes ?: GuildHomes.EMPTY
    }

    override fun getAvailableHomeSlots(guildId: UUID): Int {
        try {
            val guild = guildRepository.getById(guildId) ?: return 1 // Default to 1 slot if guild not found

            // Get progression service to check for ADDITIONAL_HOMES perk
            // For now, use a simple calculation based on guild level
            // This can be enhanced later to use the actual perk system
            val baseSlots = 1
            val additionalSlots = when {
                guild.level >= 30 -> 5  // Level 30: 5 additional homes
                guild.level >= 20 -> 3  // Level 20: 3 additional homes
                guild.level >= 15 -> 2  // Level 15: 2 additional homes
                guild.level >= 7 -> 1   // Level 7: 1 additional home
                else -> 0
            }
            return baseSlots + additionalSlots
        } catch (e: Exception) {
            // Non-critical operation - catching all exceptions to prevent service failure
            logger.error("Error calculating available home slots for guild $guildId", e)
            return 1 // Default fallback
        }
    }

    override fun removeHome(guildId: UUID, homeName: String, actorId: UUID): Boolean {
        try {
            val guild = guildRepository.getById(guildId) ?: return false

            // Check if actor has permission to remove home
            if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
                logger.warn("Player $actorId attempted to remove home for guild $guildId without permission")
                return false
            }

            // Check if the specific home exists
            if (!guild.homes.homes.containsKey(homeName)) {
                logger.warn("Player $actorId attempted to remove home '$homeName' for guild $guildId but it doesn't exist")
                return false
            }

            val updatedHomes = guild.homes.withoutHome(homeName)
            val updatedGuild = guild.copy(homes = updatedHomes)
            val result = guildRepository.update(updatedGuild)
            if (result) {
                logger.info("Guild $guildId home '$homeName' removed by $actorId")
            }
            return result
        } catch (e: Exception) {
            // Non-critical operation - catching all exceptions to prevent service failure
            logger.error("Error removing home for guild $guildId", e)
            return false
        }
    }

    override fun removeHome(guildId: UUID, actorId: UUID): Boolean {
        try {
            val guild = guildRepository.getById(guildId) ?: return false

            // Check if actor has permission to remove home
            if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
                logger.warn("Player $actorId attempted to remove home for guild $guildId without permission")
                return false
            }

            // Check if any homes exist
            if (!guild.homes.hasHomes()) {
                logger.warn("Player $actorId attempted to remove homes for guild $guildId but no homes were set")
                return false
            }

            val updatedGuild = guild.copy(homes = GuildHomes.EMPTY)
            val result = guildRepository.update(updatedGuild)
            if (result) {
                logger.info("Guild $guildId all homes removed by $actorId")
            }
            return result
        } catch (e: Exception) {
            // Non-critical operation - catching all exceptions to prevent service failure
            logger.error("Error removing all homes for guild $guildId", e)
            return false
        }
    }
    
    override fun getAllyHomes(guildId: UUID): Map<String, GuildHome> {
        try {
            val guild = guildRepository.getById(guildId) ?: return emptyMap()

            // Check if requesting guild has the perk
            val progressionService = org.koin.core.context.GlobalContext.get()
                .get<net.lumalyte.lg.application.services.ProgressionService>()
            if (!progressionService.hasPerkUnlocked(guildId, net.lumalyte.lg.application.services.PerkType.ALLY_HOME_ACCESS)) {
                return emptyMap()
            }

            // Get active ally relations
            val allyRelations = relationRepository.getByGuildAndType(
                guildId, net.lumalyte.lg.domain.entities.RelationType.ALLY
            ).filter { it.isActive() }

            val result = mutableMapOf<String, GuildHome>()
            for (relation in allyRelations) {
                val allyGuildId = relation.getOtherGuild(guildId)

                // Ally must also have the perk unlocked (mutual requirement)
                if (!progressionService.hasPerkUnlocked(allyGuildId, net.lumalyte.lg.application.services.PerkType.ALLY_HOME_ACCESS)) {
                    continue
                }

                val allyGuild = guildRepository.getById(allyGuildId) ?: continue
                val allyHome = allyGuild.allyHome ?: continue
                result[allyGuild.name] = allyHome
            }
            return result
        } catch (e: Exception) {
            logger.error("Error getting ally homes for guild $guildId", e)
            return emptyMap()
        }
    }

    override fun setAllyHome(guildId: UUID, home: GuildHome, actorId: UUID): Boolean {
        try {
            val guild = guildRepository.getById(guildId) ?: return false
            if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
                logger.warn("Player $actorId attempted to set ally home for guild $guildId without permission")
                return false
            }
            val updatedGuild = guild.copy(allyHome = home)
            val result = guildRepository.update(updatedGuild)
            if (result) {
                logger.info("Guild $guildId ally home set by $actorId")
            }
            return result
        } catch (e: Exception) {
            logger.error("Error setting ally home for guild $guildId", e)
            return false
        }
    }

    override fun removeAllyHome(guildId: UUID, actorId: UUID): Boolean {
        try {
            val guild = guildRepository.getById(guildId) ?: return false
            if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MEMBERS)) {
                logger.warn("Player $actorId attempted to remove ally home for guild $guildId without permission")
                return false
            }
            if (guild.allyHome == null) return false
            val updatedGuild = guild.copy(allyHome = null)
            val result = guildRepository.update(updatedGuild)
            if (result) {
                logger.info("Guild $guildId ally home removed by $actorId")
            }
            return result
        } catch (e: Exception) {
            logger.error("Error removing ally home for guild $guildId", e)
            return false
        }
    }

    override fun getAllyHome(guildId: UUID): GuildHome? {
        return guildRepository.getById(guildId)?.allyHome
    }

    override fun setMode(guildId: UUID, mode: GuildMode, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false
        
        // Check if actor has permission to change mode
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MODE)) {
            logger.warn("Player $actorId attempted to change mode for guild $guildId without permission")
            return false
        }
        
        val updatedGuild = guild.copy(
            mode = mode,
            modeChangedAt = Instant.now()
        )
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId mode changed to ${mode.name} by $actorId")
        }
        return result
    }
    
    override fun getMode(guildId: UUID): GuildMode {
        return guildRepository.getById(guildId)?.mode ?: GuildMode.HOSTILE
    }
    
    override fun getGuild(guildId: UUID): Guild? = guildRepository.getById(guildId)
    
    override fun getGuildByName(name: String): Guild? = guildRepository.getByName(name)
    
    override fun getPlayerGuilds(playerId: UUID): Set<Guild> {
        val guildIds = memberRepository.getGuildsByPlayer(playerId)
        return guildIds.mapNotNull { guildRepository.getById(it) }.toSet()
    }
    
    override fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean = 
        memberRepository.isPlayerInGuild(playerId, guildId)
    
    override fun hasPermission(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean {
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        return rank.permissions.contains(permission)
    }
    
    override fun getGuildCount(): Int = guildRepository.getCount()

    override fun getAllGuilds(): Set<Guild> = guildRepository.getAll()

    override fun setOpen(guildId: UUID, isOpen: Boolean, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Check permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            return false
        }

        val updatedGuild = guild.copy(isOpen = isOpen)
        return guildRepository.update(updatedGuild)
    }

    override fun setJoinFeeEnabled(guildId: UUID, enabled: Boolean, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Check permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            logger.warn("Player $actorId attempted to set join fee enabled for guild $guildId without permission")
            return false
        }

        val updatedGuild = guild.copy(joinFeeEnabled = enabled)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId join fee ${if (enabled) "enabled" else "disabled"} by $actorId")
        }
        return result
    }

    override fun setJoinFeeAmount(guildId: UUID, amount: Int, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Validate amount
        if (amount < 0) {
            logger.warn("Invalid join fee amount: $amount (must be non-negative)")
            return false
        }

        // Check permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            logger.warn("Player $actorId attempted to set join fee amount for guild $guildId without permission")
            return false
        }

        val updatedGuild = guild.copy(joinFeeAmount = amount)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId join fee amount set to $amount by $actorId")
        }
        return result
    }

    override fun getJoinFeeSettings(guildId: UUID): Pair<Boolean, Int>? {
        val guild = guildRepository.getById(guildId) ?: return null
        return Pair(guild.joinFeeEnabled, guild.joinFeeAmount)
    }

    override fun setTrackingEnabled(guildId: UUID, enabled: Boolean, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            return false
        }

        val updatedGuild = guild.copy(trackingEnabled = enabled)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            Bukkit.getPluginManager().callEvent(GuildTrackingChangedEvent(guildId, enabled))
        }
        return result
    }

    override fun canUseHome(playerId: UUID, guildId: UUID, homeName: String): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false
        val home = guild.homes.getHome(homeName) ?: return false
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val ownerRank = rankRepository.getHighestRank(guildId)
        if (ownerRank != null && member.rankId == ownerRank.id) return true
        return member.rankId in home.allowedRankIds
    }

    override fun canUseAllyHome(playerId: UUID, sourceGuildId: UUID, targetGuildId: UUID): Boolean {
        if (guildRepository.getById(sourceGuildId) == null) return false
        val targetGuild = guildRepository.getById(targetGuildId) ?: return false
        if (targetGuild.allyHome == null) return false
        if (sourceGuildId !in targetGuild.allyHomeAllowedGuilds) return false
        // Guard against stale or manually-injected whitelist entries: the two guilds must
        // currently be actively allied, not merely whitelisted from a prior alliance.
        val activeAlliance = relationRepository
            .getByGuildAndType(sourceGuildId, net.lumalyte.lg.domain.entities.RelationType.ALLY)
            .any { rel -> rel.isActive() && (rel.guildA == targetGuildId || rel.guildB == targetGuildId) }
        if (!activeAlliance) return false
        val member = memberRepository.getByPlayerAndGuild(playerId, sourceGuildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        val ownerRank = rankRepository.getHighestRank(sourceGuildId)
        if (ownerRank != null && rank.id == ownerRank.id) return true
        return RankPermission.USE_ALLY_HOMES in rank.permissions
    }

    override fun setHomeAllowedRanks(
        guildId: UUID, homeName: String, allowedRankIds: Set<UUID>, actorId: UUID
    ): Boolean {
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_HOME)) {
            logger.warn("Player $actorId attempted to set home rank whitelist for guild $guildId without MANAGE_HOME")
            return false
        }
        val guild = guildRepository.getById(guildId) ?: return false
        val home = guild.homes.getHome(homeName) ?: return false
        // Drop any rank IDs that don't belong to this guild — keeps persisted whitelist clean
        // for downstream iteration/display and rejects malicious or buggy callers.
        val validIds = rankRepository.getByGuild(guildId).map { it.id }.toSet()
        val sanitized = allowedRankIds.filter { it in validIds }.toSet()
        val rejected = allowedRankIds - sanitized
        if (rejected.isNotEmpty()) {
            logger.warn("setHomeAllowedRanks: dropping ${rejected.size} non-guild rank id(s) for guild $guildId home '$homeName': $rejected")
        }
        val updatedHome = home.copy(allowedRankIds = sanitized)
        val updatedGuild = guild.copy(homes = guild.homes.withHome(homeName, updatedHome))
        return guildRepository.update(updatedGuild)
    }

    override fun setAllyHomeAllowedGuilds(
        guildId: UUID, allowedGuildIds: Set<UUID>, actorId: UUID
    ): Boolean {
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_HOME)) {
            logger.warn("Player $actorId attempted to set ally-home guild whitelist for guild $guildId without MANAGE_HOME")
            return false
        }
        val guild = guildRepository.getById(guildId) ?: return false
        // Filter to real, currently-existing guilds; canUseAllyHome additionally checks the
        // active-ALLY relation at teleport time so phantom IDs are inert, but persisting them
        // pollutes the table over time.
        val sanitized = allowedGuildIds.filter { it != guildId && guildRepository.getById(it) != null }.toSet()
        val rejected = allowedGuildIds - sanitized
        if (rejected.isNotEmpty()) {
            logger.warn("setAllyHomeAllowedGuilds: dropping ${rejected.size} unknown/self guild id(s) for guild $guildId: $rejected")
        }
        val updatedGuild = guild.copy(allyHomeAllowedGuilds = sanitized)
        return guildRepository.update(updatedGuild)
    }

    override fun setBankFrozen(guildId: UUID, frozen: Boolean, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            logger.warn("Player $actorId attempted to set bank freeze for guild $guildId without MANAGE_GUILD_SETTINGS permission")
            return false
        }

        val updatedGuild = guild.copy(bankFrozen = frozen)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId bank freeze set to $frozen by $actorId")
        }
        return result
    }

    /**
     * Counts visible characters in a tag, excluding formatting codes.
     * MiniMessage tags like <color>, <gradient>, etc. are excluded from the count.
     *
     * @param tag The tag string to count characters in.
     * @return The number of visible characters.
     */
    private fun countVisibleCharacters(tag: String): Int {
        // Remove MiniMessage tags using regex
        // This is a simplified approach - a full MiniMessage parser would be more accurate
        val withoutTags = tag
            .replace(Regex("<[^>]*>"), "")  // Remove all <tag> elements
            .replace(Regex("&[0-9a-fk-or]"), "")  // Remove legacy color codes
            .replace(Regex("§[0-9a-fk-or]"), "")  // Remove section sign color codes

        return withoutTags.length
    }
}
