package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.JoinRequirement
import org.slf4j.Logger
import java.util.UUID

/**
 * Bukkit implementation of LfgService for Looking For Guild operations.
 */
class LfgServiceBukkit(
    private val guildRepository: GuildRepository,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val physicalCurrencyService: PhysicalCurrencyService,
    private val configService: ConfigService,
    private val vaultService: GuildVaultService,
    private val bankService: BankService,
    private val rankService: RankService,
    private val logger: Logger
) : LfgService {

    override fun getAvailableGuilds(): List<Guild> {
        val config = configService.loadConfig()
        val maxMembers = config.guild.maxMembersPerGuild

        return guildRepository.getAll()
            .filter { guild ->
                // Must be open for recruitment
                guild.isOpen &&
                // Must have available slots
                memberService.getMemberCount(guild.id) < maxMembers
            }
            .sortedBy { it.name.lowercase() }
    }

    override fun canJoinGuild(playerId: UUID, guild: Guild): LfgJoinResult {
        val config = configService.loadConfig()
        val maxMembers = config.guild.maxMembersPerGuild

        // Check if player is already in a guild
        val playerGuilds = memberService.getPlayerGuilds(playerId)
        if (playerGuilds.isNotEmpty()) {
            return LfgJoinResult.AlreadyInGuild("You are already a member of a guild")
        }

        // Check if guild has available slots
        val currentMemberCount = memberService.getMemberCount(guild.id)
        if (currentMemberCount >= maxMembers) {
            return LfgJoinResult.GuildFull("This guild has reached maximum capacity")
        }

        // Check join fee requirements
        if (guild.joinFeeEnabled && guild.joinFeeAmount > 0) {
            val vaultConfig = config.vault

            if (vaultConfig.usePhysicalCurrency) {
                // Check physical currency
                val playerCurrency = physicalCurrencyService.calculatePlayerInventoryValue(playerId)
                if (playerCurrency < guild.joinFeeAmount) {
                    return LfgJoinResult.InsufficientFunds(
                        required = guild.joinFeeAmount,
                        current = playerCurrency,
                        currencyType = vaultConfig.physicalCurrencyMaterial
                    )
                }
            } else {
                // Check virtual currency
                val playerBalance = bankService.getPlayerBalance(playerId)
                if (playerBalance < guild.joinFeeAmount) {
                    return LfgJoinResult.InsufficientFunds(
                        required = guild.joinFeeAmount,
                        current = playerBalance,
                        currencyType = "Coins"
                    )
                }
            }
        }

        return LfgJoinResult.Success("You can join this guild")
    }

    override fun joinGuild(playerId: UUID, guild: Guild): LfgJoinResult {
        // First validate that the player can join
        val canJoinResult = canJoinGuild(playerId, guild)
        if (canJoinResult !is LfgJoinResult.Success) {
            return canJoinResult
        }

        val config = configService.loadConfig()
        val vaultConfig = config.vault

        // Process join fee if applicable
        if (guild.joinFeeEnabled && guild.joinFeeAmount > 0) {
            if (vaultConfig.usePhysicalCurrency) {
                // Add physical currency to guild vault
                // Note: The player's inventory will be deducted separately when they confirm
                val success = physicalCurrencyService.addCurrency(
                    guild = guild,
                    amount = guild.joinFeeAmount,
                    reason = "LFG join fee from player"
                )
                if (!success) {
                    logger.warn("Failed to add physical currency to guild vault for join fee")
                    return LfgJoinResult.Error("Failed to process join fee")
                }
            } else {
                // Virtual currency: withdraw from player and deposit to guild
                val withdrawSuccess = bankService.withdrawPlayer(
                    playerId = playerId,
                    amount = guild.joinFeeAmount,
                    reason = "LFG join fee for ${guild.name}"
                )
                if (!withdrawSuccess) {
                    logger.warn("Failed to withdraw virtual currency from player for join fee")
                    return LfgJoinResult.Error("Failed to process join fee")
                }

                val depositResult = bankService.deposit(
                    guildId = guild.id,
                    playerId = playerId,
                    amount = guild.joinFeeAmount,
                    description = "LFG join fee"
                )
                if (depositResult == null) {
                    logger.warn("Failed to deposit join fee to guild bank")
                    // Refund the player
                    bankService.depositPlayer(playerId, guild.joinFeeAmount, "Refund: Failed guild join")
                    return LfgJoinResult.Error("Failed to process join fee")
                }
            }
        }

        // Get the default rank for new members
        val defaultRank = rankService.getDefaultRank(guild.id)
        if (defaultRank == null) {
            logger.error("No default rank found for guild ${guild.id}")
            return LfgJoinResult.Error("Guild configuration error: No default rank found")
        }

        // Add the player as a member
        val member = memberService.addMember(playerId, guild.id, defaultRank.id)
        if (member == null) {
            logger.error("Failed to add player $playerId to guild ${guild.id}")
            return LfgJoinResult.Error("Failed to add you to the guild")
        }

        logger.info("Player $playerId joined guild ${guild.name} via LFG")
        return LfgJoinResult.Success("You have joined ${guild.name}!")
    }

    override fun getJoinRequirement(guild: Guild): JoinRequirement? {
        // No requirement if join fee is disabled or amount is 0
        if (!guild.joinFeeEnabled || guild.joinFeeAmount <= 0) {
            return null
        }

        val config = configService.loadConfig()
        val vaultConfig = config.vault

        return if (vaultConfig.usePhysicalCurrency) {
            JoinRequirement(
                amount = guild.joinFeeAmount,
                isPhysicalCurrency = true,
                currencyName = vaultConfig.physicalCurrencyMaterial
            )
        } else {
            JoinRequirement(
                amount = guild.joinFeeAmount,
                isPhysicalCurrency = false,
                currencyName = "Coins"
            )
        }
    }
}
