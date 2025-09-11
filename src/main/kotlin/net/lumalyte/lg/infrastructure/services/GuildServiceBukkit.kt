package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.utils.serializeToString
import net.lumalyte.lg.domain.entities.RankPermission
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
    private val nexoEmojiService: NexoEmojiService
) : GuildService {
    
    private val logger = LoggerFactory.getLogger(GuildServiceBukkit::class.java)
    
    override fun createGuild(name: String, ownerId: UUID, banner: String?): Guild? {
        // Validate guild name
        if (name.isBlank() || name.length > 32) {
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
            home = null,
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
        guildRepository.getById(guildId) ?: return false
        
        // Check if actor has permission to disband guild
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to disband guild $guildId without permission")
            return false
        }
        
        // Remove all members first
        memberRepository.removeByGuild(guildId)
        
        // Remove all ranks
        rankRepository.removeByGuild(guildId)
        
        // Remove guild
        val result = guildRepository.remove(guildId)
        if (result) {
            logger.info("Guild $guildId disbanded by $actorId")
        }
        return result
    }
    
    override fun renameGuild(guildId: UUID, newName: String, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false
        
        // Check if actor has permission to rename guild
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to rename guild $guildId without permission")
            return false
        }
        
        // Validate new name
        if (newName.isBlank() || newName.length > 32) {
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
    
    override fun setHome(guildId: UUID, home: GuildHome, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false
        
        // Check if actor has permission to set home
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_HOME)) {
            logger.warn("Player $actorId attempted to set home for guild $guildId without permission")
            return false
        }
        
        val updatedGuild = guild.copy(home = home)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId home set to ${home.position} in world ${home.worldId} by $actorId")
        }
        return result
    }
    
    override fun getHome(guildId: UUID): GuildHome? {
        return guildRepository.getById(guildId)?.home
    }

    override fun removeHome(guildId: UUID, actorId: UUID): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Check if actor has permission to remove home
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_HOME)) {
            logger.warn("Player $actorId attempted to remove home for guild $guildId without permission")
            return false
        }

        // Check if home exists
        if (guild.home == null) {
            logger.warn("Player $actorId attempted to remove home for guild $guildId but no home was set")
            return false
        }

        val updatedGuild = guild.copy(home = null)
        val result = guildRepository.update(updatedGuild)
        if (result) {
            logger.info("Guild $guildId home removed by $actorId")
        }
        return result
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
