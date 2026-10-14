package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PaidGuildAdmission
import net.lumalyte.lg.application.services.PersonalGoldRequest
import net.lumalyte.lg.application.services.PhysicalCurrencyService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.gold.GuildGoldResult
import net.lumalyte.lg.domain.values.JoinRequirement
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Bukkit implementation of LfgService for Looking For Guild operations.
 */
class LfgServiceBukkit internal constructor(
    private val guildRepository: GuildRepository,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val physicalCurrencyService: PhysicalCurrencyService,
    private val configService: ConfigService,
    private val vaultService: GuildVaultService,
    private val bankService: BankService,
    private val rankService: RankService,
    private val recruitment: net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository? = null,
    private val recruitmentEligibility: net.lumalyte.lg.application.services.GuildRecruitmentEligibility? = null,
) : LfgService {
    /** Preserves the existing eight-argument integration constructor. */
    constructor(
        guildRepository: GuildRepository,
        guildService: GuildService,
        memberService: MemberService,
        physicalCurrencyService: PhysicalCurrencyService,
        configService: ConfigService,
        vaultService: GuildVaultService,
        bankService: BankService,
        rankService: RankService,
    ) : this(
        guildRepository, guildService, memberService, physicalCurrencyService,
        configService, vaultService, bankService, rankService, null, null,
    )

    private val logger = LoggerFactory.getLogger(LfgServiceBukkit::class.java)

    override fun getAvailableGuilds(): List<Guild> {
        val config = configService.loadConfig()
        val maxMembers = config.guild.maxMembersPerGuild

        val guilds = guildRepository.getAll().filter { it.isOpen }
        val limits = memberService.getMemberLimits(guilds.map { it.id }.toSet())
        return guilds
            .filter { guild ->
                // Must be open for recruitment
                guild.isOpen &&
                    // Must have available slots
                    memberService.getMemberCount(guild.id) < minOf(maxMembers, limits[guild.id] ?: 0)
            }
            .sortedBy { it.name.lowercase() }
    }

    override fun canJoinGuild(playerId: UUID, guild: Guild): LfgJoinResult {
        if (recruitmentEligibility?.allows(playerId, guild.id) == false) {
            return LfgJoinResult.Error("You are not eligible to join this guild")
        }
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
            val charge =
                bankService.quoteJoinFee(guild.id, guild.joinFeeAmount)
                    ?: return LfgJoinResult.Error("Join fee is not supported by the current bank limits")

            if (vaultConfig.usePhysicalCurrency) {
                // Check physical currency
                val playerCurrency = physicalCurrencyService.calculatePlayerInventoryValue(playerId)
                if (playerCurrency < charge) {
                    return LfgJoinResult.InsufficientFunds(
                        required = charge,
                        current = playerCurrency,
                        currencyType = vaultConfig.physicalCurrencyMaterial,
                    )
                }
            } else {
                // Check virtual currency
                val playerBalance = bankService.getPlayerBalance(playerId)
                if (playerBalance < charge) {
                    return LfgJoinResult.InsufficientFunds(
                        required = charge,
                        current = playerBalance,
                        currencyType = "Coins",
                    )
                }
            }
        }

        return LfgJoinResult.Success("You can join this guild")
    }

    @Synchronized
    override fun joinGuild(playerId: UUID, guild: Guild): LfgJoinResult {
        return admit(playerId, guild) { current -> isImmediate(current) }
    }

    /** Approved offers use the same compensated fee transaction without opening the guild publicly. */
    @Synchronized
    override fun joinApprovedGuild(playerId: UUID, guild: Guild, eligible: () -> Boolean): LfgJoinResult {
        return admit(playerId, guild) { eligible() }
    }

    private fun isImmediate(guild: Guild): Boolean = recruitment?.profile(guild.id)?.let {
            it.mode == net.lumalyte.lg.domain.recruitment.RecruitmentMode.JOIN_IMMEDIATELY
        } ?: guild.isOpen

    private fun admit(playerId: UUID, guild: Guild, admissionAllowed: (Guild) -> Boolean): LfgJoinResult {
        val current =
            guildRepository.getById(guild.id)
                ?: return LfgJoinResult.Error("This guild is no longer available")
        if (!admissionAllowed(current) || !sameFee(current, guild)) return recruitmentChanged()
        val rank =
            rankService.getDefaultRank(guild.id)
                ?: return LfgJoinResult.Error("Guild configuration error: No default rank found")
        val validation = canJoinGuild(playerId, current)
        if (validation !is LfgJoinResult.Success) return validation
        return if (guild.joinFeeEnabled && guild.joinFeeAmount > 0) {
            paidAdmission(playerId, guild, rank.id, admissionAllowed)
        } else {
            freeAdmission(playerId, guild, rank.id, admissionAllowed)
        }
    }

    private fun sameFee(
        current: Guild,
        quoted: Guild,
    ): Boolean = current.joinFeeEnabled == quoted.joinFeeEnabled && current.joinFeeAmount == quoted.joinFeeAmount

    private fun recruitmentChanged(): LfgJoinResult =
        LfgJoinResult.Error("Guild recruitment changed. Please reopen the guild browser")

    private fun eligibleForAdmission(
        playerId: UUID,
        guild: Guild,
        rank: UUID,
        admissionAllowed: (Guild) -> Boolean,
    ): Boolean {
        val current = guildRepository.getById(guild.id) ?: return false
        if (!admissionAllowed(current) || !sameFee(current, guild)) return false
        if (recruitmentEligibility?.allows(playerId, guild.id) == false) return false
        if (memberService.getPlayerGuilds(playerId).isNotEmpty()) return false
        val limit = minOf(configService.loadConfig().guild.maxMembersPerGuild, memberService.getMemberLimit(guild.id))
        return memberService.getMemberCount(guild.id) < limit && rankService.getDefaultRank(guild.id)?.id == rank
    }

    private fun paidAdmission(
        playerId: UUID,
        guild: Guild,
        rank: UUID,
        admissionAllowed: (Guild) -> Boolean,
    ): LfgJoinResult {
        val quote = getJoinRequirement(guild) ?: return LfgJoinResult.Error("Joining fee is unavailable")
        val request =
            PersonalGoldRequest(UUID.randomUUID(), guild.id, playerId, guild.joinFeeAmount.toLong(), "LFG join fee")
        val admission =
            object : PaidGuildAdmission {
                override fun isEligible(): Boolean =
                    eligibleForAdmission(playerId, guild, rank, admissionAllowed) && getJoinRequirement(guild) == quote

                override fun complete(): Boolean =
                    isEligible() && memberService.addMember(playerId, guild.id, rank) != null
            }
        return paymentResult(bankService.collectJoinFee(request, quote.isPhysicalCurrency, admission), guild)
    }

    private fun paymentResult(result: GuildGoldResult, guild: Guild): LfgJoinResult =
        when (result) {
            is GuildGoldResult.Applied -> LfgJoinResult.Success("You have joined ${guild.name}!")
            is GuildGoldResult.Rejected -> LfgJoinResult.Error("Join fee was not accepted (${result.reason})")
            is GuildGoldResult.Failed -> {
                logger.error(
                    "Paid guild admission {} did not complete; compensated={}",
                    result.transactionId,
                    result.compensationSucceeded,
                )
                LfgJoinResult.Error(
                    if (result.compensationSucceeded) {
                        "Join failed; your payment was returned"
                    } else {
                        "Join payment requires staff review. Do not pay again. Reference: ${result.transactionId}"
                    },
                )
            }
        }

    private fun freeAdmission(
        playerId: UUID,
        guild: Guild,
        rank: UUID,
        admissionAllowed: (Guild) -> Boolean,
    ): LfgJoinResult {
        if (!eligibleForAdmission(playerId, guild, rank, admissionAllowed)) return recruitmentChanged()
        val latest = guildRepository.getById(guild.id) ?: return LfgJoinResult.Error("Guild no longer exists")
        val validation = canJoinGuild(playerId, latest)
        if (validation !is LfgJoinResult.Success) return validation
        if (memberService.addMember(playerId, guild.id, rank) == null) {
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
                currencyName = vaultConfig.physicalCurrencyMaterial,
            )
        } else {
            JoinRequirement(
                amount = quote,
                isPhysicalCurrency = false,
                currencyName = "Coins",
            )
        }
    }
}
