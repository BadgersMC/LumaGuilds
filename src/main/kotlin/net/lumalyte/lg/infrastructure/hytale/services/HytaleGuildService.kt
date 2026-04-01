package net.lumalyte.lg.infrastructure.hytale.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.Item
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Hytale implementation of GuildService.
 *
 * This service provides guild management operations including:
 * - Guild creation, renaming, and disbanding
 * - Banner and emoji management
 * - Home location management
 * - Guild mode configuration
 * - Permission checking
 */
class HytaleGuildService(
    private val guildRepository: GuildRepository,
    private val memberRepository: MemberRepository,
    private val rankRepository: RankRepository
) : GuildService {

    private val log = LoggerFactory.getLogger(HytaleGuildService::class.java)

    override fun createGuild(name: String, ownerId: UUID, banner: String?): Guild? {
        log.debug("Creating guild '$name' for owner $ownerId")

        // Check if a guild with this name already exists
        if (guildRepository.getByName(name) != null) {
            log.debug("Guild with name '$name' already exists")
            return null
        }

        // Create the guild (Guild doesn't have ownerId field, owner is determined by ranks/members)
        val guild = Guild(
            id = UUID.randomUUID(),
            name = name,
            banner = banner,
            createdAt = Instant.now()
        )

        // Save the guild
        if (!guildRepository.add(guild)) {
            log.error("Failed to save guild '$name' to database")
            return null
        }

        // Create owner rank (highest permissions)
        val ownerRank = Rank(
            id = UUID.randomUUID(),
            guildId = guild.id,
            name = "Owner",
            priority = 1000,
            permissions = RankPermission.entries.toSet()
        )

        // Create member rank (default for new members)
        val memberRank = Rank(
            id = UUID.randomUUID(),
            guildId = guild.id,
            name = "Member",
            priority = 0,
            permissions = setOf(
                RankPermission.SEND_ANNOUNCEMENTS,
                RankPermission.SEND_PINGS,
                RankPermission.DEPOSIT_TO_BANK,
                RankPermission.VIEW_BANK_TRANSACTIONS,
                RankPermission.ACCESS_VAULT,
                RankPermission.DEPOSIT_TO_VAULT
            )
        )

        // Save ranks
        if (!rankRepository.add(ownerRank) || !rankRepository.add(memberRank)) {
            log.error("Failed to save ranks for guild '$name'")
            guildRepository.remove(guild.id)
            return null
        }

        // Add owner as member with owner rank
        val ownerMember = Member(
            playerId = ownerId,
            guildId = guild.id,
            rankId = ownerRank.id,
            joinedAt = Instant.now()
        )

        if (!memberRepository.add(ownerMember)) {
            log.error("Failed to add owner as member for guild '$name'")
            rankRepository.remove(ownerRank.id)
            rankRepository.remove(memberRank.id)
            guildRepository.remove(guild.id)
            return null
        }

        log.info("Created guild '$name' (${guild.id}) with owner $ownerId")
        return guild
    }

    override fun disbandGuild(guildId: UUID, actorId: UUID): Boolean {
        log.debug("Attempting to disband guild $guildId by actor $actorId")

        val guild = guildRepository.getById(guildId) ?: return false

        // Check if actor is the guild owner (highest rank member)
        if (!isGuildOwner(actorId, guildId)) {
            log.debug("Actor $actorId is not the owner of guild $guildId")
            return false
        }

        // Delete all members
        if (!memberRepository.removeByGuild(guildId)) {
            log.error("Failed to remove members for guild $guildId")
            return false
        }

        // Delete all ranks
        if (!rankRepository.removeByGuild(guildId)) {
            log.error("Failed to remove ranks for guild $guildId")
            return false
        }

        // Delete the guild
        if (!guildRepository.remove(guildId)) {
            log.error("Failed to delete guild $guildId from database")
            return false
        }

        log.info("Disbanded guild '${guild.name}' ($guildId)")
        return true
    }

    private fun isGuildOwner(playerId: UUID, guildId: UUID): Boolean {
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        val highestRank = rankRepository.getHighestRank(guildId) ?: return false
        return rank.id == highestRank.id
    }

    override fun renameGuild(guildId: UUID, newName: String, actorId: UUID): Boolean {
        log.debug("Attempting to rename guild $guildId to '$newName' by actor $actorId")

        val guild = guildRepository.getById(guildId) ?: return false

        // Check if actor has permission
        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            log.debug("Actor $actorId does not have MANAGE_GUILD_SETTINGS permission in guild $guildId")
            return false
        }

        // Check if new name is already taken
        val existingGuild = guildRepository.getByName(newName)
        if (existingGuild != null && existingGuild.id != guildId) {
            log.debug("Guild with name '$newName' already exists")
            return false
        }

        // Update the guild name
        val updated = guild.copy(name = newName)
        if (!guildRepository.update(updated)) {
            log.error("Failed to update guild $guildId name to '$newName'")
            return false
        }

        log.info("Renamed guild $guildId from '${guild.name}' to '$newName'")
        return true
    }

    override fun setBanner(guildId: UUID, banner: Item?, actorId: UUID): Boolean {
        log.debug("Setting banner for guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_BANNER)) {
            log.debug("Actor $actorId does not have MANAGE_BANNER permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        // Update banner (banner is stored as string in Guild entity)
        val updated = guild.copy(banner = banner?.type)
        if (!guildRepository.update(updated)) {
            log.error("Failed to update guild $guildId banner")
            return false
        }

        log.info("Updated banner for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun setEmoji(guildId: UUID, emoji: String?, actorId: UUID): Boolean {
        log.debug("Setting emoji for guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_EMOJI)) {
            log.debug("Actor $actorId does not have MANAGE_EMOJI permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updated = guild.copy(emoji = emoji)
        if (!guildRepository.update(updated)) {
            log.error("Failed to update guild $guildId emoji")
            return false
        }

        log.info("Updated emoji for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun getEmoji(guildId: UUID): String? {
        return guildRepository.getById(guildId)?.emoji
    }

    override fun setTag(guildId: UUID, tag: String?, actorId: UUID): Boolean {
        log.debug("Setting tag for guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            log.debug("Actor $actorId does not have MANAGE_GUILD_SETTINGS permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updated = guild.copy(tag = tag)
        if (!guildRepository.update(updated)) {
            log.error("Failed to update guild $guildId tag")
            return false
        }

        log.info("Updated tag for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun getTag(guildId: UUID): String? {
        return guildRepository.getById(guildId)?.tag
    }

    override fun setDescription(guildId: UUID, description: String?, actorId: UUID): Boolean {
        log.debug("Setting description for guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_DESCRIPTION)) {
            log.debug("Actor $actorId does not have MANAGE_DESCRIPTION permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updated = guild.copy(description = description)
        if (!guildRepository.update(updated)) {
            log.error("Failed to update guild $guildId description")
            return false
        }

        log.info("Updated description for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun getDescription(guildId: UUID): String? {
        return guildRepository.getById(guildId)?.description
    }

    override fun setHome(guildId: UUID, homeName: String, home: GuildHome, actorId: UUID): Boolean {
        log.debug("Setting home '$homeName' for guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_HOME)) {
            log.debug("Actor $actorId does not have MANAGE_HOME permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        // Check if we have slots available
        val availableSlots = getAvailableHomeSlots(guildId)
        val currentHomes = guild.homes.homes

        if (!currentHomes.containsKey(homeName) && currentHomes.size >= availableSlots) {
            log.debug("Guild $guildId has no available home slots (max: $availableSlots)")
            return false
        }

        // Add or update the home
        val updatedHomes = currentHomes.toMutableMap()
        updatedHomes[homeName] = home

        val updated = guild.copy(homes = GuildHomes(updatedHomes))
        if (!guildRepository.update(updated)) {
            log.error("Failed to update homes for guild $guildId")
            return false
        }

        log.info("Set home '$homeName' for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun getHome(guildId: UUID): GuildHome? {
        return guildRepository.getById(guildId)?.homes?.homes?.get("main")
    }

    override fun getHome(guildId: UUID, homeName: String): GuildHome? {
        return guildRepository.getById(guildId)?.homes?.homes?.get(homeName)
    }

    override fun getHomes(guildId: UUID): GuildHomes {
        return guildRepository.getById(guildId)?.homes ?: GuildHomes(emptyMap())
    }

    override fun getAvailableHomeSlots(guildId: UUID): Int {
        // Base slots: 1
        // TODO: Add progression-based slot increases
        return 1
    }

    override fun removeHome(guildId: UUID, homeName: String, actorId: UUID): Boolean {
        log.debug("Removing home '$homeName' from guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_HOME)) {
            log.debug("Actor $actorId does not have MANAGE_HOME permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updatedHomes = guild.homes.homes.toMutableMap()
        if (updatedHomes.remove(homeName) == null) {
            log.debug("Home '$homeName' does not exist in guild $guildId")
            return false
        }

        val updated = guild.copy(homes = GuildHomes(updatedHomes))
        if (!guildRepository.update(updated)) {
            log.error("Failed to update homes for guild $guildId")
            return false
        }

        log.info("Removed home '$homeName' from guild '${guild.name}' ($guildId)")
        return true
    }

    override fun removeHome(guildId: UUID, actorId: UUID): Boolean {
        log.debug("Removing all homes from guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_HOME)) {
            log.debug("Actor $actorId does not have MANAGE_HOME permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updated = guild.copy(homes = GuildHomes(emptyMap()))
        if (!guildRepository.update(updated)) {
            log.error("Failed to clear homes for guild $guildId")
            return false
        }

        log.info("Removed all homes from guild '${guild.name}' ($guildId)")
        return true
    }

    override fun setMode(guildId: UUID, mode: GuildMode, actorId: UUID): Boolean {
        log.debug("Setting mode to $mode for guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_MODE)) {
            log.debug("Actor $actorId does not have MANAGE_MODE permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updated = guild.copy(mode = mode, modeChangedAt = Instant.now())
        if (!guildRepository.update(updated)) {
            log.error("Failed to update mode for guild $guildId")
            return false
        }

        log.info("Set mode to $mode for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun getMode(guildId: UUID): GuildMode {
        return guildRepository.getById(guildId)?.mode ?: GuildMode.PEACEFUL
    }

    override fun getGuild(guildId: UUID): Guild? {
        return guildRepository.getById(guildId)
    }

    override fun getGuildByName(name: String): Guild? {
        return guildRepository.getByName(name)
    }

    override fun getPlayerGuilds(playerId: UUID): Set<Guild> {
        return guildRepository.getByPlayer(playerId)
    }

    override fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean {
        return memberRepository.isPlayerInGuild(playerId, guildId)
    }

    override fun hasPermission(playerId: UUID, guildId: UUID, permission: RankPermission): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false

        // Check if player is the owner (has highest rank)
        if (isGuildOwner(playerId, guildId)) {
            return true
        }

        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false

        return rank.permissions.contains(permission)
    }

    override fun getGuildCount(): Int {
        return guildRepository.getCount()
    }

    override fun getAllGuilds(): Set<Guild> {
        return guildRepository.getAll()
    }

    override fun setOpen(guildId: UUID, isOpen: Boolean, actorId: UUID): Boolean {
        log.debug("Setting open status to $isOpen for guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            log.debug("Actor $actorId does not have MANAGE_GUILD_SETTINGS permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updated = guild.copy(isOpen = isOpen)
        if (!guildRepository.update(updated)) {
            log.error("Failed to update open status for guild $guildId")
            return false
        }

        log.info("Set open status to $isOpen for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun setJoinFeeEnabled(guildId: UUID, enabled: Boolean, actorId: UUID): Boolean {
        log.debug("Setting join fee enabled to $enabled for guild $guildId by actor $actorId")

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            log.debug("Actor $actorId does not have MANAGE_GUILD_SETTINGS permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updated = guild.copy(joinFeeEnabled = enabled)
        if (!guildRepository.update(updated)) {
            log.error("Failed to update join fee enabled status for guild $guildId")
            return false
        }

        log.info("Set join fee enabled to $enabled for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun setJoinFeeAmount(guildId: UUID, amount: Int, actorId: UUID): Boolean {
        log.debug("Setting join fee amount to $amount for guild $guildId by actor $actorId")

        if (amount < 0) {
            log.debug("Join fee amount cannot be negative")
            return false
        }

        if (!hasPermission(actorId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)) {
            log.debug("Actor $actorId does not have MANAGE_GUILD_SETTINGS permission in guild $guildId")
            return false
        }

        val guild = guildRepository.getById(guildId) ?: return false

        val updated = guild.copy(joinFeeAmount = amount)
        if (!guildRepository.update(updated)) {
            log.error("Failed to update join fee amount for guild $guildId")
            return false
        }

        log.info("Set join fee amount to $amount for guild '${guild.name}' ($guildId)")
        return true
    }

    override fun getJoinFeeSettings(guildId: UUID): Pair<Boolean, Int>? {
        val guild = guildRepository.getById(guildId) ?: return null
        return Pair(guild.joinFeeEnabled, guild.joinFeeAmount)
    }
}
