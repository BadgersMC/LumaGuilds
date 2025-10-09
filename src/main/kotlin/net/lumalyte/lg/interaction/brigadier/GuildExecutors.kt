package net.lumalyte.lg.interaction.brigadier

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.*
import net.lumalyte.lg.utils.CommandSafeExecutor
import net.lumalyte.lg.utils.GuildHomeSafety
import net.lumalyte.lg.utils.ChatUtils
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * Execution handlers for guild Brigadier commands.
 * Manages all guild-related operations.
 */
object GuildExecutors : KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val configService: ConfigService by inject()
    private val messageService: MessageService by inject()
    private val getClaimAtPosition: GetClaimAtPosition by inject()
    private val menuFactory: MenuFactory by inject()

    // === TASK 1.1: Core Guild Management Commands ===

    /**
     * Creates a new guild
     */
    fun createGuild(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val guildName = StringArgumentType.getString(context, "name")
            
            // Validate guild name
            if (guildName.length < 3 || guildName.length > 16) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Guild name must be between 3 and 16 characters long"
                )
                return@execute 0
            }
            
            // Check if player is already in a guild
            val existingGuilds = guildService.getPlayerGuilds(playerId)
            if (existingGuilds.isNotEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are already in a guild: <yellow>${existingGuilds.first().name}</yellow>"
                )
                return@execute 0
            }
            
            // Create guild
            val guild = guildService.createGuild(guildName, playerId)
            if (guild != null) {
                messageService.render(
                    player.uniqueId,
                    "<green>✅ Guild '<yellow>$guildName</yellow>' created successfully!"
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You are now the Owner of the guild."
                )

                // Broadcast guild creation to all online players
                val creationMessage = "<gold>⌂ <yellow>A new guild has been founded: <gold>$guildName</gold> by <gold>${player.name}</gold>!"
                ChatUtils.broadcastMessage(creationMessage, player)

                // Log the guild creation
                player.server.logger.info("Guild '$guildName' created by ${player.name} (${player.uniqueId})")
            } else {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to create guild. The name may already be taken."
                )
            }
            
            1
        }
    }

    /**
     * Disbands the player's guild
     */
    fun disbandGuild(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            
            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }
            
            val guild = guilds.first()
            
            // Check if player is the owner (has highest rank)
            val playerRank = rankService.getPlayerRank(playerId, guild.id)
            val highestRank = rankService.getHighestRank(guild.id)
            
            if (playerRank?.id != highestRank?.id) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Only the guild owner can disband the guild."
                )
                return@execute 0
            }
            
            val success = guildService.disbandGuild(guild.id, playerId)
            
            if (success) {
                messageService.render(
                    player.uniqueId,
                    "<green>✅ Guild '<yellow>${guild.name}</yellow>' has been disbanded."
                )
            } else {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to disband guild."
                )
            }
            
            1
        }
    }

    /**
     * Joins an existing guild
     */
    fun joinGuild(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val guildName = StringArgumentType.getString(context, "guild")
            
            // Check if player is already in a guild
            val existingGuilds = guildService.getPlayerGuilds(playerId)
            if (existingGuilds.isNotEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are already in a guild: <yellow>${existingGuilds.first().name}</yellow>"
                )
                return@execute 0
            }
            
            // Find target guild
            val allGuilds = guildService.getAllGuilds()
            val targetGuild = allGuilds.find { it.name.equals(guildName, ignoreCase = true) }
            
            if (targetGuild == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Guild '<yellow>$guildName</yellow>' not found."
                )
                return@execute 0
            }
            
            // TODO: Add guild privacy/invitation settings
            // Check if guild is accepting new members (for now, all guilds accept join requests)
            // Future: Add isPrivate or acceptingMembers property to Guild entity

            // Join guild (add as member with default rank)
            val defaultRank = rankService.getDefaultRank(targetGuild.id)
            if (defaultRank == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to join guild. No default rank is configured for this guild."
                )
                return@execute 0
            }
            val member = memberService.addMember(playerId, targetGuild.id, defaultRank.id)

            if (member != null) {
                messageService.render(
                    player.uniqueId,
                    "<green>✅ You have joined guild '<yellow>${targetGuild.name}</yellow>'!"
                )
            } else {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to join guild. You may not meet the requirements."
                )
            }
            
            1
        }
    }

    /**
     * Leaves the player's current guild
     */
    fun leaveGuild(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            
            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }
            
            val guild = guilds.first()
            
            // Check if player is the owner
            val playerRank = rankService.getPlayerRank(playerId, guild.id)
            val highestRank = rankService.getHighestRank(guild.id)
            
            if (playerRank?.id == highestRank?.id) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Guild owners cannot leave their guild. Use <yellow>/guild disband</yellow> to disband the guild."
                )
                return@execute 0
            }
            
            val success = memberService.removeMember(playerId, guild.id, playerId)
            
            if (success) {
                messageService.render(
                    player.uniqueId,
                    "<green>✅ You have left guild '<yellow>${guild.name}</yellow>'."
                )
            } else {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to leave guild."
                )
            }
            
            1
        }
    }

    // === TASK 1.2: Member Management Commands ===

    /**
     * Invites a player to the guild
     */
    fun invitePlayer(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val targetPlayerName = StringArgumentType.getString(context, "player")

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has member management permission
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to invite players."
                )
                return@execute 0
            }

            // Find target player
            val targetPlayer = player.server.getPlayer(targetPlayerName)
            if (targetPlayer == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Player '<yellow>$targetPlayerName</yellow>' is not online."
                )
                return@execute 0
            }

            if (targetPlayer == player) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You cannot invite yourself."
                )
                return@execute 0
            }

            // Check if target is already in a guild
            if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ <yellow>${targetPlayer.name}</yellow> is already in your guild!"
                )
                return@execute 0
            }

            // Open confirmation menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildInviteConfirmationMenu(menuNavigator, player, guild, targetPlayer))
            
            1
        }
    }

    /**
     * Kicks a player from the guild
     */
    fun kickPlayer(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val targetPlayerName = StringArgumentType.getString(context, "player")

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has member management permission
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to kick players."
                )
                return@execute 0
            }

            // Find target player
            val targetPlayer = player.server.getPlayer(targetPlayerName)
            if (targetPlayer == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Player '<yellow>$targetPlayerName</yellow>' is not online."
                )
                return@execute 0
            }

            if (targetPlayer == player) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You cannot kick yourself."
                )
                return@execute 0
            }

            // Check if target is in the guild
            val targetMember = memberService.getMember(targetPlayer.uniqueId, guild.id)
            if (targetMember == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ <yellow>${targetPlayer.name}</yellow> is not in your guild!"
                )
                return@execute 0
            }

            // Open confirmation menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildKickConfirmationMenu(menuNavigator, player, guild, targetMember))
            
            1
        }
    }

    /**
     * Promotes a player to a higher rank
     */
    fun promotePlayer(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val targetPlayerName = StringArgumentType.getString(context, "player")

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has rank management permission
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RANKS)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to promote players."
                )
                return@execute 0
            }

            // Find target player
            val targetPlayer = player.server.getPlayer(targetPlayerName)
            if (targetPlayer == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Player '<yellow>$targetPlayerName</yellow>' is not online."
                )
                return@execute 0
            }

            // Check if target is in the guild
            val targetMember = memberService.getMember(targetPlayer.uniqueId, guild.id)
            if (targetMember == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ <yellow>${targetPlayer.name}</yellow> is not in your guild!"
                )
                return@execute 0
            }

            // Open promotion menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildPromotionMenu(menuNavigator, player, guild))
            
            1
        }
    }

    /**
     * Demotes a player to a lower rank
     */
    fun demotePlayer(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val targetPlayerName = StringArgumentType.getString(context, "player")

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has rank management permission
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RANKS)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to demote players."
                )
                return@execute 0
            }

            // Find target player
            val targetPlayer = player.server.getPlayer(targetPlayerName)
            if (targetPlayer == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Player '<yellow>$targetPlayerName</yellow>' is not online."
                )
                return@execute 0
            }

            // Check if target is in the guild
            val targetMember = memberService.getMember(targetPlayer.uniqueId, guild.id)
            if (targetMember == null) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ <yellow>${targetPlayer.name}</yellow> is not in your guild!"
                )
                return@execute 0
            }

            // Open demotion menu (uses same menu as promotion)
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildPromotionMenu(menuNavigator, player, guild))
            
            1
        }
    }

    // === TASK 1.3: Guild Configuration Commands ===

    /**
     * Teleports to guild home
     */
    fun teleportHome(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val homeName = try {
                StringArgumentType.getString(context, "name")
            } catch (e: Exception) {
                "main"
            }

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()
            val home = guildService.getHome(guild.id, homeName)

            if (home != null) {
                // Get target location
                val world = player.server.getWorld(home.worldId)
                if (world == null) {
                    messageService.render(
                        player.uniqueId,
                        "<red>❌ Guild home world is not available."
                    )
                    return@execute 0
                }

                val targetLocation = world.getBlockAt(home.position.x, home.position.y, home.position.z).location
                targetLocation.yaw = player.location.yaw
                targetLocation.pitch = player.location.pitch

                // Check if target location is safe (if safety check is enabled)
                if (configService.loadConfig().guild.homeTeleportSafetyCheck) {
                    if (!GuildHomeSafety.checkOrAskConfirm(player, targetLocation, "/guild home confirm")) {
                        return@execute 0
                    }
                }

                // Teleport player
                player.teleport(targetLocation)
                messageService.render(
                    player.uniqueId,
                    "<green>✅ Teleported to guild home '<yellow>$homeName</yellow>'!"
                )
            } else {
                // Check if the guild has any homes at all
                val allHomes = guildService.getHomes(guild.id)
                if (allHomes.hasHomes()) {
                    messageService.render(
                        player.uniqueId,
                        "<red>❌ Home '<yellow>$homeName</yellow>' has not been set."
                    )
                    messageService.render(
                        player.uniqueId,
                        "<gray>Available homes: <yellow>${allHomes.homeNames.joinToString(", ")}</yellow>"
                    )
                    messageService.render(
                        player.uniqueId,
                        "<gray>Use <yellow>/guild home <name></yellow> to teleport to a specific home."
                    )
                } else {
                    messageService.render(
                        player.uniqueId,
                        "<red>❌ No guild homes have been set."
                    )
                    messageService.render(
                        player.uniqueId,
                        "<gray>Use <yellow>/guild sethome</yellow> to set your first home."
                    )
                }
            }
            
            1
        }
    }

    /**
     * Sets guild home location
     */
    fun setHome(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val homeName = try {
                StringArgumentType.getString(context, "name")
            } catch (e: Exception) {
                "main"
            }

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()
            val location = player.location

            // Check if claims are enabled in config
            val config = configService.loadConfig()
            if (config.claimsEnabled) {
                // Check if player is standing in a claim
                val claimResult = getClaimAtPosition.execute(location.world.uid, location.toPosition3D())
                when (claimResult) {
                    is net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult.Success -> {
                        val claim = claimResult.claim

                        // Check if the claim is guild-owned
                        if (claim.teamId == null) {
                            messageService.render(
                                player.uniqueId,
                                "<red>❌ You can only set guild home in a guild-owned claim."
                            )
                            messageService.render(
                                player.uniqueId,
                                "<gray>Use the bell menu to convert this personal claim to a guild claim first."
                            )
                            return@execute 0
                        }

                        // Check if the claim belongs to the player's guild
                        if (claim.teamId != guild.id) {
                            messageService.render(
                                player.uniqueId,
                                "<red>❌ You can only set guild home in your own guild's claims."
                            )
                            messageService.render(
                                player.uniqueId,
                                "<gray>This claim belongs to a different guild."
                            )
                            return@execute 0
                        }
                    }
                    is net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult.NoClaimFound -> {
                        messageService.render(
                            player.uniqueId,
                            "<red>❌ You must be standing in a guild-owned claim to set guild home."
                        )
                        messageService.render(
                            player.uniqueId,
                            "<gray>Place a bell and convert it to a guild claim first."
                        )
                        return@execute 0
                    }
                    is net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult.StorageError -> {
                        messageService.render(
                            player.uniqueId,
                            "<red>❌ An error occurred while checking your location."
                        )
                        return@execute 0
                    }
                }
            }

            // Check if guild already has a home
            val currentHome = guildService.getHome(guild.id)
            if (currentHome != null) {
                messageService.render(
                    player.uniqueId,
                    "<yellow>⚠️ Your guild already has a home set!"
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>Current home: <yellow>${currentHome.position.x}, ${currentHome.position.y}, ${currentHome.position.z}</yellow>"
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>New location: <yellow>${location.blockX}, ${location.blockY}, ${location.blockZ}</yellow>"
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>Use <yellow>/guild sethome confirm</yellow> to replace the current home"
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>Or use the guild menu for a confirmation dialog."
                )
                return@execute 0
            }

            // Check safety and handle confirmation system
            if (config.guild.homeTeleportSafetyCheck) {
                if (!GuildHomeSafety.checkOrAskConfirm(player, location, "/guild sethome unsafe")) {
                    return@execute 0
                }
            }

            // Set the home
            val home = GuildHome(
                worldId = location.world.uid,
                position = location.toPosition3D()
            )

            val success = guildService.setHome(guild.id, homeName, home, playerId)

            if (success) {
                messageService.render(
                    player.uniqueId,
                    "<green>✅ Guild home '<yellow>$homeName</yellow>' set successfully!"
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>Location: <yellow>${location.blockX}, ${location.blockY}, ${location.blockZ}</yellow>"
                )
            } else {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to set guild home."
                )
            }
            
            1
        }
    }

    /**
     * Sets guild description
     */
    fun setDescription(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val description = StringArgumentType.getString(context, "text")

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to manage guild settings
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_DESCRIPTION)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to manage guild description."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need the MANAGE_DESCRIPTION permission to change the guild description."
                )
                return@execute 0
            }

            // Validate description length
            if (description.length > 100) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Guild description must be 100 characters or less."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>Your description is <yellow>${description.length}</yellow> characters long."
                )
                return@execute 0
            }

            // Set the description
            val success = guildService.setDescription(guild.id, description, playerId)

            if (success) {
                messageService.render(
                    player.uniqueId,
                    "<green>✅ Guild description set!"
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>New description: <yellow>\"$description\"</yellow>"
                )
            } else {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to set guild description."
                )
            }
            
            1
        }
    }

    /**
     * Sets guild tag
     */
    fun setTag(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
        val tag = StringArgumentType.getString(context, "tag")

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to manage guild settings
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_BANNER)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to manage guild tag."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need the MANAGE_BANNER permission to change the guild tag."
                )
                return@execute 0
            }

            // Validate tag length and format
            if (tag.length > 5) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Guild tag must be 5 characters or less."
                )
                return@execute 0
            }

            if (!tag.matches(Regex("^[A-Z0-9]+$"))) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Guild tag can only contain uppercase letters and numbers."
                )
                return@execute 0
            }

            // Set the tag
            val success = guildService.setTag(guild.id, tag, playerId)

            if (success) {
                messageService.render(
                    player.uniqueId,
                    "<green>✅ Guild tag set to: <yellow>[$tag]</yellow>"
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>This will be displayed next to guild member names."
                )
            } else {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to set guild tag. The tag may already be taken."
                )
            }
            
            1
        }
    }

    /**
     * Opens banner selection menu
     */
    fun openBannerMenu(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to manage guild settings
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_BANNER)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to manage guild banner."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need the MANAGE_BANNER permission to change the guild banner."
                )
                return@execute 0
            }

            // Open banner menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildBannerMenu(menuNavigator, player, guild))
            
            1
        }
    }

    /**
     * Sets guild mode
     */
    fun setMode(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId
            val modeString = StringArgumentType.getString(context, "mode")

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if mode switching is enabled in config
            val mainConfig = configService.loadConfig()
            if (!mainConfig.guild.modeSwitchingEnabled) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Guild mode switching is disabled by server configuration."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>Guilds cannot change between Peaceful and Hostile modes."
                )
                return@execute 0
            }

            val guildMode = try {
                GuildMode.valueOf(modeString.uppercase())
            } catch (e: IllegalArgumentException) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Invalid mode. Use 'peaceful' or 'hostile'."
                )
                return@execute 0
            }

            val success = guildService.setMode(guild.id, guildMode, playerId)

            if (success) {
                messageService.render(
                    player.uniqueId,
                    "<green>✅ Guild mode changed to <yellow>${guildMode.name.lowercase().replaceFirstChar { it.uppercase() }}</yellow>!"
                )
            } else {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ Failed to change guild mode. You may not have permission."
                )
            }
            
            1
        }
    }

    // === TASK 1.4: Advanced Feature Commands ===

    /**
     * Opens war management menu
     */
    fun openWarMenu(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to manage wars (DECLARE_WAR permission)
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.DECLARE_WAR)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to manage wars for your guild."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need the DECLARE_WAR permission to access war management."
                )
                return@execute 0
            }

            // Open the war management menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
            messageService.render(
                player.uniqueId,
                "<gold>⚔ Opening war management menu..."
            )
            
            1
        }
    }

    /**
     * Opens diplomatic relations menu
     */
    fun openRelationsMenu(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to manage relations
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RELATIONS)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to manage guild relations."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need the MANAGE_RELATIONS permission to access diplomatic relations."
                )
                return@execute 0
            }

            // Open the relations menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
            messageService.render(
                player.uniqueId,
                "<gold>🤝 Opening diplomatic relations menu..."
            )
            
            1
        }
    }

    /**
     * Opens banking system menu
     */
    fun openBankMenu(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to access bank
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.DEPOSIT_TO_BANK) &&
                !memberService.hasPermission(playerId, guild.id, RankPermission.WITHDRAW_FROM_BANK)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to access the guild bank."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need bank permissions to access the banking system."
                )
                return@execute 0
            }

            // Open the bank menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
            messageService.render(
                player.uniqueId,
                "<gold>💰 Opening guild bank menu..."
            )
            
            1
        }
    }

    /**
     * Shows guild statistics
     */
    fun showStats(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Open the statistics menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildStatisticsMenu(menuNavigator, player, guild))
            messageService.render(
                player.uniqueId,
                "<gold>📊 Opening guild statistics menu..."
            )
            
            1
        }
    }

    /**
     * Opens member management menu
     */
    fun openMembersMenu(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to manage members
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to manage guild members."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need the MANAGE_MEMBERS permission to access member management."
                )
                return@execute 0
            }

            // Open the member management menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
            messageService.render(
                player.uniqueId,
                "<gold>👥 Opening member management menu..."
            )
            
            1
        }
    }

    /**
     * Opens rank management menu
     */
    fun openRanksMenu(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to manage ranks
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RANKS)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to manage guild ranks."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need the MANAGE_RANKS permission to access rank management."
                )
                return@execute 0
            }

            // Open the rank management menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
            messageService.render(
                player.uniqueId,
                "<gold>🏆 Opening rank management menu..."
            )
            
            1
        }
    }

    /**
     * Opens guild settings menu
     */
    fun openSettingsMenu(context: CommandContext<CommandSourceStack>): Int {
        return CommandSafeExecutor.execute(context) {
            val player = context.source.sender as Player
            val playerId = player.uniqueId

            // Find player's guild
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You are not in a guild."
                )
                return@execute 0
            }

            val guild = guilds.first()

            // Check if player has permission to manage guild settings
            if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)) {
                messageService.render(
                    player.uniqueId,
                    "<red>❌ You don't have permission to manage guild settings."
                )
                messageService.render(
                    player.uniqueId,
                    "<gray>You need the MANAGE_GUILD_SETTINGS permission to access guild settings."
                )
                return@execute 0
            }

            // Open the settings menu
            val menuNavigator = MenuNavigator(player, messageService)
            menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
            messageService.render(
                player.uniqueId,
                "<gold>⚙ Opening guild settings menu..."
            )
            
            1
        }
    }

    // === Legacy Methods (for backward compatibility) ===

    fun create(context: CommandContext<CommandSourceStack>): Int = createGuild(context)
    fun disband(context: CommandContext<CommandSourceStack>): Int = disbandGuild(context)
    fun invite(context: CommandContext<CommandSourceStack>): Int = invitePlayer(context)
    fun kick(context: CommandContext<CommandSourceStack>): Int = kickPlayer(context)
    fun home(context: CommandContext<CommandSourceStack>): Int = teleportHome(context)
    fun menu(context: CommandContext<CommandSourceStack>): Int = openSettingsMenu(context)
    fun ranks(context: CommandContext<CommandSourceStack>): Int = openRanksMenu(context)
}
