package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.*
import net.lumalyte.lg.utils.deserializeToItemStack
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@CommandAlias("guild|g")
class GuildCommand : BaseCommand(), KoinComponent {
    
    private val guildService: GuildService by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val getClaimAtPosition: GetClaimAtPosition by inject()
    
    @Subcommand("create")
    @CommandPermission("bellclaims.guild.create")
    fun onCreate(player: Player, name: String, @Optional banner: String?) {
        val playerId = player.uniqueId
        
        // Check if player is already in a guild
        val existingGuilds = guildService.getPlayerGuilds(playerId)
        if (existingGuilds.isNotEmpty()) {
            player.sendMessage("§cYou are already in a guild: ${existingGuilds.first().name}")
            return
        }
        
        val guild = guildService.createGuild(name, playerId, banner)
        if (guild != null) {
            player.sendMessage("§aGuild '$name' created successfully!")
            player.sendMessage("§7You are now the Owner of the guild.")
        } else {
            player.sendMessage("§cFailed to create guild. The name may already be taken.")
        }
    }
    
    @Subcommand("rename")
    @CommandPermission("bellclaims.guild.rename")
    fun onRename(player: Player, newName: String) {
        val playerId = player.uniqueId
        
        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }
        
        val guild = guilds.first()
        val success = guildService.renameGuild(guild.id, newName, playerId)
        
        if (success) {
            player.sendMessage("§aGuild renamed to '$newName' successfully!")
        } else {
            player.sendMessage("§cFailed to rename guild. The new name may already be taken.")
        }
    }
    
    @Subcommand("sethome")
    @CommandPermission("bellclaims.guild.sethome")
    fun onSetHome(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()
        val location = player.location

        // Check if player is standing in a claim
        val claimResult = getClaimAtPosition.execute(location.world.uid, location.toPosition3D())
        when (claimResult) {
            is net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult.Success -> {
                val claim = claimResult.claim

                // Check if the claim is guild-owned
                if (claim.teamId == null) {
                    player.sendMessage("§cYou can only set guild home in a guild-owned claim.")
                    player.sendMessage("§7Use the bell menu to convert this personal claim to a guild claim first.")
                    return
                }

                // Check if the claim belongs to the player's guild
                if (claim.teamId != guild.id) {
                    player.sendMessage("§cYou can only set guild home in your own guild's claims.")
                    player.sendMessage("§7This claim belongs to a different guild.")
                    return
                }

                // Player is in their guild's claim, proceed with setting home
                val home = GuildHome(
                    worldId = location.world.uid,
                    position = location.toPosition3D()
                )

                val success = guildService.setHome(guild.id, home, playerId)

                if (success) {
                    player.sendMessage("§aGuild home set successfully!")
                    player.sendMessage("§7Location: ${location.blockX}, ${location.blockY}, ${location.blockZ}")
                    player.sendMessage("§7This location is within your guild's claim area.")
                } else {
                    player.sendMessage("§cFailed to set guild home. You may not have permission.")
                }
            }
            is net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult.NoClaimFound -> {
                player.sendMessage("§cYou must be standing in a guild-owned claim to set guild home.")
                player.sendMessage("§7Place a bell and convert it to a guild claim first.")
                return
            }
            is net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult.StorageError -> {
                player.sendMessage("§cAn error occurred while checking your location.")
                return
            }
        }
    }
    
    @Subcommand("home")
    @CommandPermission("bellclaims.guild.home")
    fun onHome(player: Player) {
        val playerId = player.uniqueId
        
        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }
        
        val guild = guilds.first()
        val home = guildService.getHome(guild.id)
        
        if (home != null) {
            // Teleport to guild home
            val world = player.server.getWorld(home.worldId)
            if (world != null) {
                val location = world.getBlockAt(home.position.x, home.position.y, home.position.z).location
                location.yaw = player.location.yaw
                location.pitch = player.location.pitch
                
                player.teleport(location)
                player.sendMessage("§aWelcome to your guild home!")
            } else {
                player.sendMessage("§cGuild home world is not available.")
            }
        } else {
            player.sendMessage("§cGuild home has not been set.")
        }
    }
    
    @Subcommand("ranks")
    @CommandPermission("bellclaims.guild.ranks")
    fun onRanks(player: Player) {
        val playerId = player.uniqueId
        
        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }
        
        val guild = guilds.first()
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        
        player.sendMessage("§6=== Guild Ranks ===")
        player.sendMessage("§7Guild: §f${guild.name}")
        player.sendMessage("")
        
        for (rank in ranks) {
            val memberCount = memberService.getMembersByRank(guild.id, rank.id).size
            val permissions = if (rank.permissions.isNotEmpty()) {
                rank.permissions.joinToString(", ") { it.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() } }
            } else "None"
            
            player.sendMessage("§e${rank.name} §7(Priority: ${rank.priority})")
            player.sendMessage("§7  Members: §f$memberCount")
            player.sendMessage("§7  Permissions: §f$permissions")
            player.sendMessage("")
        }
    }
    
    @Subcommand("emoji")
    @CommandPermission("bellclaims.guild.emoji")
    fun onEmoji(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player has permission to manage emoji
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        val hasEmojiPermission = playerRank?.permissions?.any { permission ->
            permission in setOf(
                RankPermission.MANAGE_BANNER,
                RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_CLAIMS
            )
        } ?: false

        val highestRank = rankService.getHighestRank(guild.id)
        val isOwner = playerRank?.id == highestRank?.id

        if (!hasEmojiPermission && !isOwner) {
            player.sendMessage("§cYou don't have permission to change the guild emoji.")
            return
        }

        // Open emoji menu
        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(GuildEmojiMenu(menuNavigator, player, guild))
    }

    @Subcommand("mode")
    @CommandPermission("bellclaims.guild.mode")
    fun onMode(player: Player, mode: String) {
        val playerId = player.uniqueId
        
        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }
        
        val guild = guilds.first()
        val guildMode = try {
            GuildMode.valueOf(mode.uppercase())
        } catch (e: IllegalArgumentException) {
            player.sendMessage("§cInvalid mode. Use 'peaceful' or 'hostile'.")
            return
        }
        
        val success = guildService.setMode(guild.id, guildMode, playerId)
        
        if (success) {
            player.sendMessage("§aGuild mode changed to ${guildMode.name.lowercase().replaceFirstChar { it.uppercase() }}!")
        } else {
            player.sendMessage("§cFailed to change guild mode. You may not have permission.")
        }
    }
    
    @Subcommand("info")
    @CommandPermission("bellclaims.guild.info")
    fun onInfo(player: Player, @Optional targetPlayer: String?) {
        val playerId = player.uniqueId
        
        if (targetPlayer != null) {
            // Show info about another player's guild
            // This would require additional implementation to find players by name
            player.sendMessage("§cPlayer lookup not yet implemented.")
            return
        }
        
        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }
        
        val guild = guilds.first()
        val memberCount = memberService.getMemberCount(guild.id)
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        
        player.sendMessage("§6=== Guild Information ===")
        player.sendMessage("§7Name: §f${guild.name}")
        player.sendMessage("§7Level: §f${guild.level}")
        player.sendMessage("§7Members: §f$memberCount")
        player.sendMessage("§7Mode: §f${guild.mode.name.lowercase().replaceFirstChar { it.uppercase() }}")
        player.sendMessage("§7Your Rank: §f${playerRank?.name ?: "Unknown"}")
        
        if (guild.banner != null) {
            val bannerItem = guild.banner.deserializeToItemStack()
            val bannerDisplay = if (bannerItem != null) {
                bannerItem.type.name.lowercase().replace("_", " ")
            } else {
                "Error loading banner"
            }
            player.sendMessage("§7Banner: §f$bannerDisplay")
        }
        
        if (guild.home != null) {
            player.sendMessage("§7Home: §f${guild.home.position.x}, ${guild.home.position.y}, ${guild.home.position.z}")
        }
        
        player.sendMessage("§7Created: §f${guild.createdAt}")
    }
    
    @Subcommand("disband")
    @CommandPermission("bellclaims.guild.disband")
    fun onDisband(player: Player) {
        val playerId = player.uniqueId
        
        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }
        
        val guild = guilds.first()
        
        // Check if player is the owner (has highest rank)
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        val highestRank = rankService.getHighestRank(guild.id)
        
        if (playerRank?.id != highestRank?.id) {
            player.sendMessage("§cOnly the guild owner can disband the guild.")
            return
        }
        
        val success = guildService.disbandGuild(guild.id, playerId)
        
        if (success) {
            player.sendMessage("§aGuild '${guild.name}' has been disbanded.")
        } else {
            player.sendMessage("§cFailed to disband guild.")
        }
    }

    @Subcommand("menu")
    @CommandPermission("bellclaims.guild.menu")
    fun onMenu(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player has permission (owner or admin)
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        val highestRank = rankService.getHighestRank(guild.id)

        if (playerRank?.id != highestRank?.id) {
            // Check if player has management permissions
            val hasManagementPerms = playerRank?.permissions?.any { permission ->
                permission in setOf(
                    RankPermission.MANAGE_RANKS,
                    RankPermission.MANAGE_MEMBERS,
                    RankPermission.MANAGE_BANNER,
                    RankPermission.MANAGE_CLAIMS
                )
            } ?: false

            if (!hasManagementPerms) {
                player.sendMessage("§cYou don't have permission to access the guild control panel.")
                player.sendMessage("§7Only guild owners and members with management permissions can access this menu.")
                return
            }
        }

        // Open the guild control panel
        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
    }

    @Subcommand("invite")
    @CommandPermission("bellclaims.guild.invite")
    fun onInvite(player: Player, targetPlayerName: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player has member management permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            player.sendMessage("§cYou don't have permission to invite players.")
            return
        }

        // Find target player
        val targetPlayer = player.server.getPlayer(targetPlayerName)
        if (targetPlayer == null) {
            player.sendMessage("§cPlayer '$targetPlayerName' is not online.")
            return
        }

        if (targetPlayer == player) {
            player.sendMessage("§cYou cannot invite yourself.")
            return
        }

        // Check if target is already in a guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            player.sendMessage("§c${targetPlayer.name} is already in your guild!")
            return
        }

        // Open confirmation menu
        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(GuildInviteConfirmationMenu(menuNavigator, player, guild, targetPlayer))
    }

    @Subcommand("kick")
    @CommandPermission("bellclaims.guild.kick")
    fun onKick(player: Player, targetPlayerName: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player has member management permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            player.sendMessage("§cYou don't have permission to kick players.")
            return
        }

        // Find target player
        val targetPlayer = player.server.getPlayer(targetPlayerName)
        if (targetPlayer == null) {
            player.sendMessage("§cPlayer '$targetPlayerName' is not online.")
            return
        }

        if (targetPlayer == player) {
            player.sendMessage("§cYou cannot kick yourself.")
            return
        }

        // Check if target is in the guild
        val targetMember = memberService.getMember(targetPlayer.uniqueId, guild.id)
        if (targetMember == null) {
            player.sendMessage("§c${targetPlayer.name} is not in your guild!")
            return
        }

        // Open confirmation menu
        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(GuildKickConfirmationMenu(menuNavigator, player, guild, targetMember))
    }
}
