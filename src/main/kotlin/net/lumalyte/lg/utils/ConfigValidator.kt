package net.lumalyte.lg.utils

import net.lumalyte.lg.config.*
import org.bukkit.Material
import org.slf4j.LoggerFactory

/**
 * Utility class for validating configuration values and providing safe defaults.
 */
object ConfigValidator {
    
    private val logger = LoggerFactory.getLogger(ConfigValidator::class.java)
    
    /**
     * Validates and corrects a MainConfig instance, logging any issues found.
     *
     * @param config The configuration to validate
     * @return A validated and corrected configuration
     */
    fun validateAndCorrect(config: MainConfig): MainConfig {
        logger.info("Validating configuration...")
        
        return config.copy(
            // Core claims validation
            claimLimit = config.claimLimit.coerceAtLeast(0),
            claimBlockLimit = config.claimBlockLimit.coerceAtLeast(100),
            initialClaimSize = config.initialClaimSize.coerceAtLeast(3),
            minimumPartitionSize = config.minimumPartitionSize.coerceAtLeast(3),
            distanceBetweenClaims = config.distanceBetweenClaims.coerceAtLeast(0),
            visualiserHideDelayPeriod = config.visualiserHideDelayPeriod.coerceAtLeast(0.0),
            visualiserRefreshPeriod = config.visualiserRefreshPeriod.coerceAtLeast(0.1),
            
            // Validate model IDs
            customClaimToolModelId = config.customClaimToolModelId.coerceAtLeast(1),
            customMoveToolModelId = config.customMoveToolModelId.coerceAtLeast(1),
            
            // Validate nested configs
            guild = validateGuildConfig(config.guild),
            bank = validateBankConfig(config.bank),
            combat = validateCombatConfig(config.combat),
            chat = validateChatConfig(config.chat),
            progression = validateProgressionConfig(config.progression),
            ui = validateUIConfig(config.ui),
            teamRolePermissions = validateTeamRolePermissions(config.teamRolePermissions)
        )
    }
    
    /**
     * Validates guild configuration.
     */
    private fun validateGuildConfig(config: GuildConfig): GuildConfig {
        return config.copy(
            maxNameLength = config.maxNameLength.coerceIn(1, 64),
            minNameLength = config.minNameLength.coerceIn(1, config.maxNameLength),
            maxGuildCount = config.maxGuildCount.coerceAtLeast(1),
            createGuildCost = config.createGuildCost.coerceAtLeast(0),
            disbandRefundPercent = config.disbandRefundPercent.coerceIn(0.0, 1.0),
            modeSwitchCooldownDays = config.modeSwitchCooldownDays.coerceAtLeast(1),
            maxCustomRanks = config.maxCustomRanks.coerceIn(1, 50),
            maxRankNameLength = config.maxRankNameLength.coerceIn(1, 32),
            maxMembersPerGuild = config.maxMembersPerGuild.coerceAtLeast(1),
            homeTeleportCooldownSeconds = config.homeTeleportCooldownSeconds.coerceAtLeast(0),
            homeSetCooldownMinutes = config.homeSetCooldownMinutes.coerceAtLeast(0),
            homeTeleportWarmupSeconds = config.homeTeleportWarmupSeconds.coerceAtLeast(0)
        ).also {
            if (config.maxNameLength > 64) {
                logger.warn("Guild max name length reduced to 64 (was ${config.maxNameLength})")
            }
            if (config.minNameLength > config.maxNameLength) {
                logger.warn("Guild min name length reduced to max name length (${config.maxNameLength})")
            }
        }
    }
    
    /**
     * Validates bank configuration.
     */
    private fun validateBankConfig(config: BankConfig): BankConfig {
        return config.copy(
            minDepositAmount = config.minDepositAmount.coerceAtLeast(1),
            maxDepositAmount = maxOf(config.minDepositAmount, config.maxDepositAmount),
            maxWithdrawalPercent = config.maxWithdrawalPercent.coerceIn(0.0, 1.0),
            dailyWithdrawalLimit = config.dailyWithdrawalLimit.coerceAtLeast(1),
            depositFeePercent = config.depositFeePercent.coerceIn(0.0, 1.0),
            withdrawalFeePercent = config.withdrawalFeePercent.coerceIn(0.0, 1.0),
            maxDepositFee = config.maxDepositFee.coerceAtLeast(0),
            maxWithdrawalFee = config.maxWithdrawalFee.coerceAtLeast(0),
            interestRatePercent = config.interestRatePercent.coerceAtLeast(0.0),
            interestCompoundPeriodHours = config.interestCompoundPeriodHours.coerceAtLeast(1),
            maxBankBalance = config.maxBankBalance.coerceAtLeast(1000),
            auditLogRetentionDays = config.auditLogRetentionDays.coerceAtLeast(1),
            suspiciousTransactionThreshold = config.suspiciousTransactionThreshold.coerceAtLeast(1)
        ).also {
            if (config.maxDepositAmount < config.minDepositAmount) {
                logger.warn("Bank max deposit amount increased to min deposit amount (${config.minDepositAmount})")
            }
        }
    }
    
    /**
     * Validates combat configuration.
     */
    private fun validateCombatConfig(config: CombatConfig): CombatConfig {
        return config.copy(
            killCooldownMinutes = config.killCooldownMinutes.coerceAtLeast(0),
            samePlayerKillLimit = config.samePlayerKillLimit.coerceAtLeast(1),
            warDeclarationCooldownHours = config.warDeclarationCooldownHours.coerceAtLeast(0),
            warDurationHours = config.warDurationHours.coerceAtLeast(1),
            warEndGracePeriodMinutes = config.warEndGracePeriodMinutes.coerceAtLeast(0),
            maxSimultaneousWars = config.maxSimultaneousWars.coerceAtLeast(1),
            killExperience = config.killExperience.coerceAtLeast(0),
            warWinExperience = config.warWinExperience.coerceAtLeast(0),
            warLoseExperience = config.warLoseExperience.coerceAtLeast(0)
        )
    }
    
    /**
     * Validates chat configuration.
     */
    private fun validateChatConfig(config: ChatConfig): ChatConfig {
        return config.copy(
            announceCooldownMinutes = config.announceCooldownMinutes.coerceAtLeast(0),
            pingCooldownMinutes = config.pingCooldownMinutes.coerceAtLeast(0),
            maxMessageLength = config.maxMessageLength.coerceIn(1, 1000),
            maxEmojisPerMessage = config.maxEmojisPerMessage.coerceAtLeast(0),
            emojiPermissionPrefix = if (config.emojiPermissionPrefix.isBlank()) {
                "lumalyte.emoji"
            } else {
                config.emojiPermissionPrefix.trim()
            }
        ).also {
            if (config.maxMessageLength > 1000) {
                logger.warn("Chat max message length reduced to 1000 (was ${config.maxMessageLength})")
            }
        }
    }
    
    /**
     * Validates progression configuration.
     */
    private fun validateProgressionConfig(config: ProgressionConfig): ProgressionConfig {
        return config.copy(
            // Experience values validation (ensure non-negative)
            bankDepositXpPer100 = config.bankDepositXpPer100.coerceAtLeast(0),
            memberJoinedXp = config.memberJoinedXp.coerceAtLeast(0),
            playerKillXp = config.playerKillXp.coerceAtLeast(0),
            mobKillXp = config.mobKillXp.coerceAtLeast(0),
            cropBreakXp = config.cropBreakXp.coerceAtLeast(0),
            blockBreakXp = config.blockBreakXp.coerceAtLeast(0),
            blockPlaceXp = config.blockPlaceXp.coerceAtLeast(0),
            craftingXp = config.craftingXp.coerceAtLeast(0),
            smeltingXp = config.smeltingXp.coerceAtLeast(0),
            fishingXp = config.fishingXp.coerceAtLeast(0),
            enchantingXp = config.enchantingXp.coerceAtLeast(0),
            claimCreatedXp = config.claimCreatedXp.coerceAtLeast(0),
            warWonXp = config.warWonXp.coerceAtLeast(0),
            
            // Rate limiting validation
            xpCooldownMs = config.xpCooldownMs.coerceAtLeast(100L), // Minimum 100ms
            maxXpPerBatch = config.maxXpPerBatch.coerceIn(1, 1000), // Reasonable limits
            
            // Leveling curve validation
            baseXp = config.baseXp.coerceAtLeast(1.0),
            levelExponent = config.levelExponent.coerceIn(1.0, 3.0), // Reasonable range
            linearBonusPerLevel = config.linearBonusPerLevel.coerceAtLeast(0)
        )
    }
    
    /**
     * Validates UI configuration.
     */
    private fun validateUIConfig(config: UIConfig): UIConfig {
        return config.copy(
            guildMenuItem = validateMenuItemConfig(config.guildMenuItem, "Guild Menu Item"),
            bankMenuItem = validateMenuItemConfig(config.bankMenuItem, "Bank Menu Item"),
            rankMenuItem = validateMenuItemConfig(config.rankMenuItem, "Rank Menu Item"),
            relationMenuItem = validateMenuItemConfig(config.relationMenuItem, "Relation Menu Item"),
            warMenuItem = validateMenuItemConfig(config.warMenuItem, "War Menu Item"),
            modeMenuItem = validateMenuItemConfig(config.modeMenuItem, "Mode Menu Item"),
            homeMenuItem = validateMenuItemConfig(config.homeMenuItem, "Home Menu Item"),
            leaderboardMenuItem = validateMenuItemConfig(config.leaderboardMenuItem, "Leaderboard Menu Item"),
            chatMenuItem = validateMenuItemConfig(config.chatMenuItem, "Chat Menu Item"),
            partyMenuItem = validateMenuItemConfig(config.partyMenuItem, "Party Menu Item"),
            backButton = validateMenuItemConfig(config.backButton, "Back Button"),
            nextButton = validateMenuItemConfig(config.nextButton, "Next Button"),
            confirmButton = validateMenuItemConfig(config.confirmButton, "Confirm Button"),
            cancelButton = validateMenuItemConfig(config.cancelButton, "Cancel Button"),
            closeButton = validateMenuItemConfig(config.closeButton, "Close Button"),
            onlineIndicator = validateMenuItemConfig(config.onlineIndicator, "Online Indicator"),
            offlineIndicator = validateMenuItemConfig(config.offlineIndicator, "Offline Indicator"),
            peacefulModeIndicator = validateMenuItemConfig(config.peacefulModeIndicator, "Peaceful Mode Indicator"),
            hostileModeIndicator = validateMenuItemConfig(config.hostileModeIndicator, "Hostile Mode Indicator"),
            ownerIcon = validateMenuItemConfig(config.ownerIcon, "Owner Icon"),
            coOwnerIcon = validateMenuItemConfig(config.coOwnerIcon, "Co-Owner Icon"),
            adminIcon = validateMenuItemConfig(config.adminIcon, "Admin Icon"),
            modIcon = validateMenuItemConfig(config.modIcon, "Mod Icon"),
            memberIcon = validateMenuItemConfig(config.memberIcon, "Member Icon"),
            menuSize = config.menuSize.let { size ->
                when {
                    size <= 9 -> 9
                    size <= 18 -> 18
                    size <= 27 -> 27
                    size <= 36 -> 36
                    size <= 45 -> 45
                    else -> 54
                }
            },
            menuUpdateIntervalTicks = config.menuUpdateIntervalTicks.coerceAtLeast(1)
        ).also {
            if (config.menuSize !in listOf(9, 18, 27, 36, 45, 54)) {
                logger.warn("Menu size adjusted to nearest valid value (was ${config.menuSize}, now ${it.menuSize})")
            }
        }
    }
    
    /**
     * Validates a menu item configuration.
     */
    private fun validateMenuItemConfig(config: MenuItemConfig, name: String): MenuItemConfig {
        // Validate material exists
        val validMaterial = try {
            Material.valueOf(config.material.uppercase())
            config.material.uppercase()
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid material '${config.material}' for $name, using STONE")
            "STONE"
        }
        
        return config.copy(
            material = validMaterial,
            name = if (config.name.isBlank()) name else config.name,
            customModelData = config.customModelData?.coerceAtLeast(1)
        )
    }
    
    /**
     * Validates team role permissions.
     */
    private fun validateTeamRolePermissions(config: TeamRolePermissions): TeamRolePermissions {
        val validPermissions = setOf(
            "BUILD", "HARVEST", "CONTAINER", "DISPLAY", "VEHICLE", "SIGN", 
            "REDSTONE", "DOOR", "TRADE", "HUSBANDRY", "DETONATE", "EVENT", "SLEEP", "VIEW"
        )
        
        val validatedRoles = config.roleMappings.mapValues { (roleName, permissions) ->
            val validatedPermissions = permissions.filter { permission ->
                if (permission in validPermissions) {
                    true
                } else {
                    logger.warn("Invalid permission '$permission' for role '$roleName', removing")
                    false
                }
            }.toSet()
            
            if (validatedPermissions.isEmpty()) {
                logger.warn("Role '$roleName' has no valid permissions, adding VIEW permission")
                setOf("VIEW")
            } else {
                validatedPermissions
            }
        }
        
        val validatedDefaults = config.defaultPermissions.filter { permission ->
            if (permission in validPermissions) {
                true
            } else {
                logger.warn("Invalid default permission '$permission', removing")
                false
            }
        }.toSet().ifEmpty {
            logger.warn("No valid default permissions, using VIEW")
            setOf("VIEW")
        }
        
        return config.copy(
            roleMappings = validatedRoles,
            defaultPermissions = validatedDefaults,
            cacheInvalidationDelaySeconds = config.cacheInvalidationDelaySeconds.coerceAtLeast(1)
        )
    }
    
    /**
     * Logs configuration validation summary.
     */
    fun logConfigSummary(config: MainConfig) {
        logger.info("Configuration loaded successfully:")
        logger.info("- Claims: limit=${config.claimLimit}, blocks=${config.claimBlockLimit}")
        logger.info("- Guild: max members=${config.guild.maxMembersPerGuild}, mode cooldown=${config.guild.modeSwitchCooldownDays} days")
        logger.info("- Bank: fee=${(config.bank.withdrawalFeePercent * 100).toInt()}%, daily limit=${config.bank.dailyWithdrawalLimit}")
        logger.info("- Combat: kill cooldown=${config.combat.killCooldownMinutes}min, max wars=${config.combat.maxSimultaneousWars}")
        logger.info("- Chat: announce cooldown=${config.chat.announceCooldownMinutes}min, emojis=${config.chat.enableEmojis}")
        logger.info("- UI: menu size=${config.ui.menuSize}, animations=${config.ui.enableMenuAnimations}")
        logger.info("- Roles: ${config.teamRolePermissions.roleMappings.size} configured")
    }
}
