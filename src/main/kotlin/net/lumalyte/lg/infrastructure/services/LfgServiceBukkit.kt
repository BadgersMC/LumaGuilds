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
    @Suppress("LongParameterList") // Preserve the existing public eight-argument integration constructor.
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
    private val admissionChecks = LfgAdmissionChecks(
        guildRepository,
        memberService,
        rankService,
        configService,
        recruitmentEligibility,
        this,
    )

    override fun getAvailableGuilds(): List<Guild> {
        val config = configService.loadConfig()
        val maxMembers = config.guild.maxMembersPerGuild

        val guilds = guildRepository.getAll().filter(::isImmediate)
        val limits = memberService.getMemberLimits(guilds.map { it.id }.toSet())
        return guilds
            .filter { guild ->
                // Must be open for recruitment
                isImmediate(guild) &&
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
    override fun joinGuild(playerId: UUID, guild: Guild): LfgJoinResult =
        admit(playerId, guild) { current -> isImmediate(current) }

    /** Approved offers use the same compensated fee transaction without opening the guild publicly. */
    @Synchronized
    override fun joinApprovedGuild(playerId: UUID, guild: Guild, eligible: () -> Boolean): LfgJoinResult =
        admit(playerId, guild) { eligible() }

    private fun isImmediate(guild: Guild): Boolean {
        return recruitment?.profile(guild.id)?.let {
            it.mode == net.lumalyte.lg.domain.recruitment.RecruitmentMode.JOIN_IMMEDIATELY
        } ?: guild.isOpen
    }

    private fun admit(playerId: UUID, guild: Guild, admissionAllowed: (Guild) -> Boolean): LfgJoinResult {
        return when (val checked = admissionChecks.prepare(playerId, guild, admissionAllowed)) {
            is LfgAdmissionCheck.Denied -> checked.result
            is LfgAdmissionCheck.Ready -> if (guild.joinFeeEnabled && guild.joinFeeAmount > 0) {
                paidAdmission(playerId, guild, checked.rank, admissionAllowed)
            } else {
                freeAdmission(playerId, guild, checked.rank, admissionAllowed)
            }
        }
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
                override fun isEligible(): Boolean {
                    return admissionChecks.allows(playerId, guild, rank, admissionAllowed) &&
                        getJoinRequirement(guild) == quote
                }

                override fun complete(): Boolean =
                    isEligible() && memberService.addMember(playerId, guild.id, rank) != null
            }
        return bankService.collectJoinFee(request, quote.isPhysicalCurrency, admission).admissionFeedback(guild, logger)
    }

    private fun freeAdmission(
        playerId: UUID,
        guild: Guild,
        rank: UUID,
        admissionAllowed: (Guild) -> Boolean,
    ): LfgJoinResult {
        val checked = if (admissionChecks.allows(playerId, guild, rank, admissionAllowed)) {
            admissionChecks.prepare(playerId, guild, admissionAllowed)
        } else {
            LfgAdmissionCheck.Denied(LfgJoinResult.Error("Guild recruitment changed. Please reopen the guild browser"))
        }
        return when (checked) {
            is LfgAdmissionCheck.Denied -> checked.result
            is LfgAdmissionCheck.Ready -> {
                if (memberService.addMember(playerId, guild.id, checked.rank) == null) {
                    logger.error("Failed to add player $playerId to guild ${guild.id}")
                    LfgJoinResult.Error("Failed to add you to the guild")
                } else {
                    logger.info("Player $playerId joined guild ${guild.name} via LFG")
                    LfgJoinResult.Success("You have joined ${guild.name}!")
                }
            }
        }
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
