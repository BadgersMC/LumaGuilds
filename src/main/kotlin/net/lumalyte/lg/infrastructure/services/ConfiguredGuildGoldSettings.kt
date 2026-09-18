package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildGoldSettings
import net.lumalyte.lg.application.services.GuildGoldSettingsProvider
import net.lumalyte.lg.domain.gold.GuildGoldCapacity
import net.lumalyte.lg.domain.gold.GuildGoldPolicy
import java.util.UUID

/** One operation-local snapshot; no cross-operation cache of progression or bank limits. */
class ConfiguredGuildGoldSettings(
    private val config: ConfigService,
    private val progression: ProgressionRepository,
    private val rewards: ProgressionConfigService,
    private val guildRewards: net.lumalyte.lg.application.services.GuildRewardService? = null
) : GuildGoldSettingsProvider {
    override fun settingsFor(guildId: UUID): GuildGoldSettings {
        val bank = config.loadConfig().bank
        val entitlements = guildRewards?.entitlementsIfEnabled(guildId)
        if (entitlements != null) return GuildGoldSettings(
            GuildGoldPolicy(
                bank.minDepositAmount.toLong(), bank.maxDepositAmount.toLong(),
                bank.maxWithdrawalPercent, bank.dailyWithdrawalLimit.toLong(),
                bank.depositFeePercent, bank.withdrawalFeePercent * entitlements.withdrawalFeeMultiplier,
                bank.maxDepositFee.toLong(), bank.maxWithdrawalFee.toLong(),
                bank.maxBankBalance.toLong(), bank.suspiciousTransactionThreshold.toLong(),
                bank.autoLockSuspiciousAccounts
            ), GuildGoldCapacity(entitlements.currentRunBankCapacity, entitlements.permanentBankCapacity))
        val level = progression.getGuildProgression(guildId)?.currentLevel
        val levelRewards = rewards.getProgressionConfig().getActiveLevelRewards()
        val feeMultiplier = (1..(level ?: 0)).fold(1.0) { current, reached ->
            minOf(current, levelRewards[reached]?.withdrawalFeeMultiplier ?: 1.0)
        }
        val tier = level?.let { BankServiceBukkit.computeProgressionBankLimit(levelRewards, it) }
        return GuildGoldSettings(
            GuildGoldPolicy(
                bank.minDepositAmount.toLong(), bank.maxDepositAmount.toLong(),
                bank.maxWithdrawalPercent, bank.dailyWithdrawalLimit.toLong(),
                bank.depositFeePercent, bank.withdrawalFeePercent * feeMultiplier,
                bank.maxDepositFee.toLong(), bank.maxWithdrawalFee.toLong(),
                bank.maxBankBalance.toLong(), bank.suspiciousTransactionThreshold.toLong(),
                bank.autoLockSuspiciousAccounts
            ),
            GuildGoldCapacity((tier ?: bank.maxBankBalance).toLong(), 0)
        )
    }
}
