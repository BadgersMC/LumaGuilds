package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.*
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.GuildHomeSafety
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import net.kyori.adventure.text.Component
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@CommandAlias("guild|g")
class GuildCommand : BaseCommand(), KoinComponent {
    
    private val guildService: GuildService by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val getClaimAtPosition: GetClaimAtPosition by inject()
    private val configService: ConfigService by inject()

    // Teleportation tracking for command-based teleports
    private data class TeleportSession(
        val player: Player,
        val targetLocation: org.bukkit.Location,
        val startLocation: org.bukkit.Location,
        var countdownTask: BukkitRunnable? = null,
        var remainingSeconds: Int = 5
    )

    private val activeTeleports = mutableMapOf<java.util.UUID, TeleportSession>()
    
    @Subcommand("create")
    @CommandPermission("lumaguilds.guild.create")
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

            // Broadcast guild creation to all online players
            val creationMessage = "§6⌂ §eA new guild has been founded: §6$name §eby §6${player.name}§e!"
            net.lumalyte.lg.utils.ChatUtils.broadcastMessage(creationMessage, player)

            // Log the guild creation
            player.server.logger.info("Guild '${name}' created by ${player.name} (${player.uniqueId})")
        } else {
            player.sendMessage("§cFailed to create guild. The name may already be taken.")
        }
    }
    
    @Subcommand("rename")
    @CommandPermission("lumaguilds.guild.rename")
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
    @CommandPermission("lumaguilds.guild.sethome")
    fun onSetHome(player: Player, @Optional confirm: String?) {
        // Check if this is a confirmation for an unsafe location
        if (confirm?.lowercase() == "unsafe") {
            val pendingLocation = GuildHomeSafety.consumePending(player)
            if (pendingLocation != null) {
                // Get player's guild
                val playerId = player.uniqueId
                val guilds = guildService.getPlayerGuilds(playerId)
                if (guilds.isEmpty()) {
                    player.sendMessage("§cYou are not in a guild.")
                    return
                }
                val guild = guilds.first()
                setGuildHomeCommand(player, guild, pendingLocation)
                return
            } else {
                player.sendMessage("§cNo pending unsafe location to confirm, or confirmation expired.")
                return
            }
        }
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
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

        // Check if guild already has a home (separate from safety confirmation)
        val currentHome = guildService.getHome(guild.id)
        if (currentHome != null && confirm?.lowercase() != "confirm" && confirm?.lowercase() != "unsafe") {
            player.sendMessage("§c⚠️ Your guild already has a home set!")
            player.sendMessage("§7Current home: §f${currentHome.position.x}, ${currentHome.position.y}, ${currentHome.position.z}")
            player.sendMessage("§7New location: §f${location.blockX}, ${location.blockY}, ${location.blockZ}")
            player.sendMessage("§7Use §6/guild sethome confirm §7to replace the current home")
            player.sendMessage("§7Or use the guild menu for a confirmation dialog.")
            return
        }

        // Check safety and handle confirmation system
        if (config.guild.homeTeleportSafetyCheck) {
            if (!GuildHomeSafety.checkOrAskConfirm(player, location, "/guild sethome unsafe")) {
                return
            }
        }

        // Set the home
        setGuildHomeCommand(player, guild, location)
    }
    
    @Subcommand("home")
    @CommandPermission("lumaguilds.guild.home")
    fun onHome(player: Player, @Optional confirm: String?) {
        // Check if this is a confirmation for an unsafe teleport
        if (confirm?.lowercase() == "confirm") {
            val pendingLocation = GuildHomeSafety.consumePending(player)
            if (pendingLocation != null) {
                startTeleportCountdown(player, pendingLocation)
                return
            } else {
                player.sendMessage("§cNo pending unsafe teleport to confirm, or confirmation expired.")
                return
            }
        }

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
            // Check if player already has an active teleport
            if (activeTeleports.containsKey(playerId)) {
                player.sendMessage("§cYou already have a teleport in progress. Please wait for it to complete.")
                return
            }

            // Get target location
            val world = player.server.getWorld(home.worldId)
            if (world == null) {
                player.sendMessage("§cGuild home world is not available.")
                return
            }

            val targetLocation = world.getBlockAt(home.position.x, home.position.y, home.position.z).location
            targetLocation.yaw = player.location.yaw
            targetLocation.pitch = player.location.pitch

            // Check if target location is safe (if safety check is enabled)
            if (configService.loadConfig().guild.homeTeleportSafetyCheck) {
                if (!GuildHomeSafety.checkOrAskConfirm(player, targetLocation, "/guild home confirm")) {
                    return
                }
            }

            // Start teleport countdown
            startTeleportCountdown(player, targetLocation)
        } else {
            player.sendMessage("§cGuild home has not been set.")
        }
    }
    
    @Subcommand("ranks")
    @CommandPermission("lumaguilds.guild.ranks")
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
    @CommandPermission("lumaguilds.guild.emoji")
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
    @CommandPermission("lumaguilds.guild.mode")
    @CommandCompletion("peaceful|hostile")
    fun onMode(player: Player, mode: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if mode switching is enabled in config
        val mainConfig = configService.loadConfig()
        if (!mainConfig.guild.modeSwitchingEnabled) {
            player.sendMessage("§c❌ Guild mode switching is disabled by server configuration.")
            player.sendMessage("§7Guilds cannot change between Peaceful and Hostile modes.")
            return
        }

        val guildMode = try {
            GuildMode.valueOf(mode.uppercase())
        } catch (e: IllegalArgumentException) {
            player.sendMessage("§cInvalid mode. Use 'peaceful' or 'hostile'.")
            return
        }

        val success = guildService.setMode(guild.id, guildMode, playerId)

        if (success) {
            player.sendMessage("§a✅ Guild mode changed to ${guildMode.name.lowercase().replaceFirstChar { it.uppercase() }}!")
        } else {
            player.sendMessage("§c❌ Failed to change guild mode. You may not have permission.")
        }
    }
    
    @Subcommand("info")
    @CommandCompletion("@guilds")
    fun onInfo(player: Player, @Optional targetGuild: String?) {
        val menuNavigator = MenuNavigator(player)

        if (targetGuild != null) {
            // Show info about another guild by name
            val guilds = guildService.getAllGuilds()
            val targetGuildObj = guilds.find { it.name.equals(targetGuild, ignoreCase = true) }

            if (targetGuildObj == null) {
                player.sendMessage("§cGuild '$targetGuild' not found.")
                return
            }

            // Open the target guild's info menu (no permission restrictions)
            menuNavigator.openMenu(GuildInfoMenu(menuNavigator, player, targetGuildObj))
        } else {
            // Show player's own guild info
            val guilds = guildService.getPlayerGuilds(player.uniqueId)
            if (guilds.isEmpty()) {
                player.sendMessage("§cYou are not in a guild.")
                return
            }

            val guild = guilds.first()
            menuNavigator.openMenu(GuildInfoMenu(menuNavigator, player, guild))
        }
    }
    
    @Subcommand("disband")
    @CommandPermission("lumaguilds.guild.disband")
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
    @CommandPermission("lumaguilds.guild.menu")
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
    @CommandPermission("lumaguilds.guild.invite")
    @CommandCompletion("@players @guilds")
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
    @CommandPermission("lumaguilds.guild.kick")
    @CommandCompletion("@guildmembers")
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

    @Subcommand("tag")
    @CommandPermission("lumaguilds.guild.tag")
    fun onTag(player: Player, @Optional tag: String?) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player has permission to manage guild settings
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_BANNER)) {
            player.sendMessage("§cYou don't have permission to manage guild tag.")
            player.sendMessage("§7You need the MANAGE_BANNER permission to change the guild tag.")
            return
        }

        if (tag == null) {
            // Open tag edit menu directly if player has permission
            val menuNavigator = MenuNavigator(player)
            menuNavigator.openMenu(TagEditorMenu(menuNavigator, player, guild))
            return
        }

        // Validate tag length and format
        if (tag.length > 5) {
            player.sendMessage("§cGuild tag must be 5 characters or less.")
            return
        }

        if (!tag.matches(Regex("^[A-Z0-9]+$"))) {
            player.sendMessage("§cGuild tag can only contain uppercase letters and numbers.")
            return
        }

        // Set the tag
        val success = guildService.setTag(guild.id, tag, playerId)

        if (success) {
            player.sendMessage("§a✅ Guild tag set to: §f[$tag]")
            player.sendMessage("§7This will be displayed next to guild member names.")
        } else {
            player.sendMessage("§c❌ Failed to set guild tag. The tag may already be taken.")
        }
    }

    @Subcommand("description|desc")
    @CommandPermission("lumaguilds.guild.description")
    fun onDescription(player: Player, @Optional description: String?) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player has permission to manage guild settings
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_DESCRIPTION)) {
            player.sendMessage("§cYou don't have permission to manage guild description.")
            player.sendMessage("§7You need the MANAGE_DESCRIPTION permission to change the guild description.")
            return
        }

        if (description == null) {
            // Open description edit menu directly if player has permission
            val menuNavigator = MenuNavigator(player)
            menuNavigator.openMenu(DescriptionEditorMenu(menuNavigator, player, guild))
            return
        }

        // Validate description length
        if (description.length > 100) {
            player.sendMessage("§cGuild description must be 100 characters or less.")
            player.sendMessage("§7Your description is ${description.length} characters long.")
            return
        }

        // Set the description
        val success = guildService.setDescription(guild.id, description, playerId)

        if (success) {
            player.sendMessage("§a✅ Guild description set!")
            player.sendMessage("§7New description: §f\"$description\"")
        } else {
            player.sendMessage("§c❌ Failed to set guild description.")
        }
    }

    @Subcommand("war")
    @CommandPermission("lumaguilds.guild.war")
    fun onWar(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player has permission to manage wars (DECLARE_WAR permission)
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage("§cYou don't have permission to manage wars for your guild.")
            player.sendMessage("§7You need the DECLARE_WAR permission to access war management.")
            return
        }

        // Open the war management menu
        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(GuildWarManagementMenu(menuNavigator, player, guild))
        player.sendMessage("§6⚔ Opening war management menu...")
    }

    private fun setGuildHomeCommand(player: Player, guild: net.lumalyte.lg.domain.entities.Guild, location: org.bukkit.Location) {
        val home = GuildHome(
            worldId = location.world.uid,
            position = location.toPosition3D()
        )

        // Check if location is safe (if safety check is enabled)
        if (configService.loadConfig().guild.homeTeleportSafetyCheck) {
            val safetyResult = GuildHomeSafety.evaluateSafety(location)
            if (!safetyResult.safe) {
                player.sendMessage("§e[Warning] §7That home looks unsafe: §c${safetyResult.reason}")
                player.sendMessage("§7Use §6/guild sethome confirm §7within 10s to set anyway.")
                return
            }
        }

        val success = guildService.setHome(guild.id, home, player.uniqueId)

        if (success) {
            player.sendMessage("§a✅ Guild home set successfully!")
            player.sendMessage("§7Location: §f${location.blockX}, ${location.blockY}, ${location.blockZ}")
            player.sendMessage("§7This location is within your guild's claim area.")
            player.sendMessage("§7Members can now use §6/guild home §7to teleport here.")
        } else {
            player.sendMessage("§c❌ Failed to set guild home. You may not have permission.")
        }
    }

    // Teleport countdown helper methods
    private fun startTeleportCountdown(player: Player, targetLocation: org.bukkit.Location) {
        val playerId = player.uniqueId

        // Cancel any existing teleport
        cancelTeleport(playerId)

        val session = TeleportSession(
            player = player,
            targetLocation = targetLocation,
            startLocation = player.location.clone(),
            remainingSeconds = 5
        )

        activeTeleports[playerId] = session

        player.sendMessage("§e◷ Teleportation countdown started! Don't move for 5 seconds...")
        player.sendActionBar(Component.text("§eTeleporting to guild home in §f5§e seconds..."))

        val countdownTask = object : BukkitRunnable() {
            override fun run() {
                val currentSession = activeTeleports[playerId] ?: return

                // Check if player moved
                if (hasPlayerMoved(currentSession)) {
                    cancelTeleport(playerId)
                    player.sendMessage("§c❌ Teleportation canceled - you moved!")
                    return
                }

                currentSession.remainingSeconds--

                if (currentSession.remainingSeconds <= 0) {
                    // Teleport the player
                    player.teleport(currentSession.targetLocation)
                    player.sendMessage("§a✅ Welcome to your guild home!")
                    player.sendActionBar(Component.text("§aTeleported to guild home!"))

                    // Clean up
                    activeTeleports.remove(playerId)
                } else {
                    // Update action bar
                    player.sendActionBar(Component.text("§eTeleporting to guild home in §f${currentSession.remainingSeconds}§e seconds..."))
                }
            }
        }

        session.countdownTask = countdownTask
        val plugin = player.server.pluginManager.getPlugin("LumaGuilds")
            ?: return // Plugin not found, cannot schedule countdown
        countdownTask.runTaskTimer(plugin, 20L, 20L) // Every second
    }

    private fun cancelTeleport(playerId: java.util.UUID) {
        val session = activeTeleports[playerId] ?: return

        session.countdownTask?.cancel()
        activeTeleports.remove(playerId)
    }

    private fun hasPlayerMoved(session: TeleportSession): Boolean {
        val currentLocation = session.player.location
        val startLocation = session.startLocation

        // Check if player moved more than 0.1 blocks in any direction
        return Math.abs(currentLocation.x - startLocation.x) > 0.1 ||
               Math.abs(currentLocation.y - startLocation.y) > 0.1 ||
               Math.abs(currentLocation.z - startLocation.z) > 0.1 ||
               currentLocation.world != startLocation.world
    }

    private fun isLocationSafe(location: org.bukkit.Location): Boolean {
        return GuildHomeSafety.evaluateSafety(location).safe
    }

}