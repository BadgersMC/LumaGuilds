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
import net.lumalyte.lg.application.services.PaidGuildAdmission
import net.lumalyte.lg.application.services.PersonalGoldRequest
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.JoinRequirement
import org.slf4j.LoggerFactory
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
    private val rankService: RankService
) : LfgService {

    private val logger = LoggerFactory.getLogger(LfgServiceBukkit::class.java)

    override fun getAvailableGuilds(): List<Guild> {
        val config = configService.loadConfig()
        val maxMembers = config.guild.maxMembersPerGuild

        return guildRepository.getAll()
            .filter { guild ->
                // Must be open for recruitment
                guild.isOpen &&
                // Must have available slots
                memberService.getMemberCount(guild.id) < minOf(maxMembers, memberService.getMemberLimit(guild.id))
            }
            .sortedBy { it.name.lowercase() }
    }

    override fun canJoinGuild(playerId: UUID, guild: Guild): LfgJoinResult {
        val config = configService.loadConfig()
        val maxMembers = minOf(config.guild.maxMembersPerGuild, memberService.getMemberLimit(guild.id))

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
            val charge = bankService.quoteJoinFee(guild.id, guild.joinFeeAmount)
                ?: return LfgJoinResult.Error("Join fee is not supported by the current bank limits")

            if (vaultConfig.usePhysicalCurrency) {
                // Check physical currency
                val playerCurrency = physicalCurrencyService.calculatePlayerInventoryValue(playerId)
                if (playerCurrency < charge) {
                    return LfgJoinResult.InsufficientFunds(
                        required = charge,
                        current = playerCurrency,
                        currencyType = vaultConfig.physicalCurrencyMaterial
                    )
                }
            } else {
                // Check virtual currency
                val playerBalance = bankService.getPlayerBalance(playerId)
                if (playerBalance < charge) {
                    return LfgJoinResult.InsufficientFunds(
                        required = charge,
                        current = playerBalance,
                        currencyType = "Coins"
                    )
                }
            }
        }

        return LfgJoinResult.Success("You can join this guild")
    }

    @Synchronized
    override fun joinGuild(playerId: UUID, guild: Guild): LfgJoinResult {
        val currentGuild = guildRepository.getById(guild.id)
            ?: return LfgJoinResult.Error("This guild is no longer available")
        if (!currentGuild.isOpen || currentGuild.joinFeeEnabled != guild.joinFeeEnabled ||
            currentGuild.joinFeeAmount != guild.joinFeeAmount) {
            return LfgJoinResult.Error("Guild recruitment changed. Please reopen the guild browser")
        }
        val defaultRank = rankService.getDefaultRank(guild.id)
            ?: return LfgJoinResult.Error("Guild configuration error: No default rank found")
        // First validate that the player can join
        val canJoinResult = canJoinGuild(playerId, guild)
        if (canJoinResult !is LfgJoinResult.Success) {
            return canJoinResult
        }

        val config = configService.loadConfig()
        val vaultConfig = config.vault

        // Process join fee if applicable
        if (guild.joinFeeEnabled && guild.joinFeeAmount > 0) {
            val request = PersonalGoldRequest(UUID.randomUUID(), guild.id, playerId,
                guild.joinFeeAmount.toLong(), "LFG join fee")
            val result = bankService.collectJoinFee(request, vaultConfig.usePhysicalCurrency, object : PaidGuildAdmission {
                override fun isEligible(): Boolean {
                    val latest = guildRepository.getById(guild.id) ?: return false
                    return latest.isOpen && latest.joinFeeEnabled == guild.joinFeeEnabled &&
                        latest.joinFeeAmount == guild.joinFeeAmount &&
                        memberService.getPlayerGuilds(playerId).isEmpty() &&
                        memberService.getMemberCount(guild.id) < minOf(configService.loadConfig().guild.maxMembersPerGuild,
                            memberService.getMemberLimit(guild.id)) &&
                        rankService.getDefaultRank(guild.id)?.id == defaultRank.id
                }
                override fun complete(): Boolean = memberService.addMember(playerId, guild.id, defaultRank.id) != null
            })
            return when (result) {
                is GuildGoldResult.Applied -> LfgJoinResult.Success("You have joined ${guild.name}!")
                is GuildGoldResult.Rejected -> LfgJoinResult.Error("Join fee was not accepted (${result.reason})")
                is GuildGoldResult.Failed -> {
                    logger.error("Paid guild admission ${result.transactionId} did not complete; compensated=${result.compensationSucceeded}")
                    LfgJoinResult.Error(if (result.compensationSucceeded) "Join failed; your payment was returned"
                        else "Join payment requires staff review. Do not pay again. Reference: ${result.transactionId}")
                }
            }
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
        val quote = bankService.quoteJoinFee(guild.id, guild.joinFeeAmount) ?: return null

        return if (vaultConfig.usePhysicalCurrency) {
            JoinRequirement(
                amount = quote,
                isPhysicalCurrency = true,
                currencyName = vaultConfig.physicalCurrencyMaterial
            )
        } else {
            JoinRequirement(
                amount = quote,
                isPhysicalCurrency = false,
                currencyName = "Coins"
            )
        }
    }
}
