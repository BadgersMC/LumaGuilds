package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.DepartureReason
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.*
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.GuildHomeSafety
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import java.util.UUID
import org.koin.core.component.inject

@CommandAlias("guild|g")
class GuildCommand : BaseCommand(), KoinComponent {

    private val guildService: GuildService by inject()
    private val guildRepository: net.lumalyte.lg.application.persistence.GuildRepository by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val vaultService: net.lumalyte.lg.application.services.GuildVaultService by inject()
    private val warService: net.lumalyte.lg.application.services.WarService by inject()
    private val getClaimAtPosition: GetClaimAtPosition by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: MenuFactory by inject()
    private val progressionService: net.lumalyte.lg.application.services.ProgressionService by inject()
    private val historyRepository: MembershipHistoryRepository by inject()
    private val guildChatListener: net.lumalyte.lg.interaction.listeners.GuildChatListener by inject()
    private val adminOverrideService: net.lumalyte.lg.application.services.AdminOverrideService by inject()
    private val teleportationService: net.lumalyte.lg.infrastructure.services.TeleportationService by inject()

    private val lastHomeTeleport = mutableMapOf<java.util.UUID, Long>()

    private fun notifyGuildMembers(guildId: java.util.UUID, message: String) {
        val members = memberService.getGuildMembers(guildId)
        members.forEach { member ->
            val onlinePlayer = Bukkit.getPlayer(member.playerId)
            if (onlinePlayer != null && onlinePlayer.isOnline) {
                onlinePlayer.sendMessage(message)
            }
        }
    }

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

        // Pre-validate guild name with helpful error messages

        // Check for MiniMessage/HTML-like formatting tags
        if (name.contains("<") && name.contains(">")) {
            player.sendMessage("§c❌ Invalid guild name!")
            player.sendMessage("§7Guild names cannot contain formatting tags like §f<bold>§7, §f<gradient>§7, etc.")
            player.sendMessage("§7")
            player.sendMessage("§e💡 TIP: Use §6/guild tag §eto set a fancy formatted tag instead!")
            player.sendMessage("§7Example: §6/guild tag <gradient:#FF0000:#00FF00>MyGuild</gradient>")
            player.sendMessage("§7")
            player.sendMessage("§7Guild name = Plain text only")
            player.sendMessage("§7Guild tag = Fancy formatting with colors")
            return
        }

        // Check for blank name
        if (name.isBlank()) {
            player.sendMessage("§c❌ Guild name cannot be blank!")
            return
        }

        // Check for length
        if (name.length > 32) {
            player.sendMessage("§c❌ Guild name is too long!")
            player.sendMessage("§7Maximum length: §f32 characters")
            player.sendMessage("§7Your name: §f${name.length} characters")
            return
        }

        // Check for invalid characters (only allow letters, numbers, spaces, and basic punctuation)
        if (!name.matches(Regex("^[a-zA-Z0-9 '&-]+$"))) {
            player.sendMessage("§c❌ Invalid guild name!")
            player.sendMessage("§7Guild names can only contain:")
            player.sendMessage("§7 • Letters (a-z, A-Z)")
            player.sendMessage("§7 • Numbers (0-9)")
            player.sendMessage("§7 • Spaces")
            player.sendMessage("§7 • Basic punctuation: ' & -")
            player.sendMessage("§7")
            player.sendMessage("§e💡 TIP: Use §6/guild tag §eto add colors and formatting!")
            return
        }

        val guild = guildService.createGuild(name, playerId, banner)
        if (guild != null) {
            player.sendMessage("§a✅ Guild '$name' created successfully!")
            player.sendMessage("§7You are now the Owner of the guild.")
            player.sendMessage("§7")
            player.sendMessage("§e💡 Customize your guild:")
            player.sendMessage("§7 • Set fancy tag: §6/guild tag")
            player.sendMessage("§7 • Open menu: §6/guild menu")

            // Broadcast guild creation to all online players
            val creationMessage = "§6⌂ §eA new guild has been founded: §6$name §eby §6${player.name}§e!"
            net.lumalyte.lg.utils.ChatUtils.broadcastMessage(creationMessage, player)

            // Log the guild creation
            player.server.logger.info("Guild '${name}' created by ${player.name} (${player.uniqueId})")
        } else {
            player.sendMessage("§c❌ Failed to create guild!")
            player.sendMessage("§7The name §f'$name' §7is already taken by another guild.")
            player.sendMessage("§7Please choose a different name.")
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

        // Check if player has permission to rename guild
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)) {
            player.sendMessage("§c❌ You don't have permission to rename the guild!")
            player.sendMessage("§7You need the §fMANAGE_GUILD_SETTINGS §7permission to rename the guild.")
            return
        }

        // Pre-validate guild name with helpful error messages

        // Check for MiniMessage/HTML-like formatting tags
        if (newName.contains("<") && newName.contains(">")) {
            player.sendMessage("§c❌ Invalid guild name!")
            player.sendMessage("§7Guild names cannot contain formatting tags like §f<bold>§7, §f<gradient>§7, etc.")
            player.sendMessage("§7")
            player.sendMessage("§e💡 TIP: Use §6/guild tag §eto set a fancy formatted tag instead!")
            player.sendMessage("§7Guild name = Plain text only")
            player.sendMessage("§7Guild tag = Fancy formatting with colors")
            return
        }

        // Check for blank name
        if (newName.isBlank()) {
            player.sendMessage("§c❌ Guild name cannot be blank!")
            return
        }

        // Check for length
        if (newName.length > 32) {
            player.sendMessage("§c❌ Guild name is too long!")
            player.sendMessage("§7Maximum length: §f32 characters")
            player.sendMessage("§7Your name: §f${newName.length} characters")
            return
        }

        // Check for invalid characters
        if (!newName.matches(Regex("^[a-zA-Z0-9 '&-]+$"))) {
            player.sendMessage("§c❌ Invalid guild name!")
            player.sendMessage("§7Guild names can only contain:")
            player.sendMessage("§7 • Letters (a-z, A-Z)")
            player.sendMessage("§7 • Numbers (0-9)")
            player.sendMessage("§7 • Spaces")
            player.sendMessage("§7 • Basic punctuation: ' & -")
            player.sendMessage("§7")
            player.sendMessage("§e💡 TIP: Use §6/guild tag §eto add colors and formatting!")
            return
        }

        val success = guildService.renameGuild(guild.id, newName, playerId)

        if (success) {
            player.sendMessage("§a✅ Guild renamed to §f'$newName'§a successfully!")
        } else {
            player.sendMessage("§c❌ Failed to rename guild!")
            player.sendMessage("§7The name §f'$newName' §7may already be taken by another guild.")
            player.sendMessage("§7Please choose a different name.")
        }
    }
    
    @Subcommand("sethome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onSetHome(player: Player, @Optional homeName: String?, @Optional confirm: String?) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()
        val location = player.location

        // Handle the case where user types "/guild sethome confirm" - treat "confirm" as confirmation, not home name
        val adjustedHomeName = if (homeName?.lowercase() == "confirm") null else homeName
        val adjustedConfirm = if (homeName?.lowercase() == "confirm") "confirm" else confirm

        val targetHomeName = adjustedHomeName ?: "main"

        // Check if this is a confirmation for an unsafe location
        if (adjustedConfirm?.lowercase() == "unsafe") {
            val pendingLocation = GuildHomeSafety.consumePending(player)
            if (pendingLocation != null) {
                setGuildHomeCommand(player, guild, pendingLocation, targetHomeName)
                return
            } else {
                player.sendMessage("§cNo pending unsafe location to confirm, or confirmation expired.")
                return
            }
        }

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

        // Block only when the *named* home being set already exists. Looking up by guild.id
        // alone returns the default ("main") home, which incorrectly blocked /g sethome <name>
        // for any guild that had a main home and tricked players into overwriting main when
        // they followed the "use /guild sethome confirm" hint.
        val currentHome = guildService.getHome(guild.id, targetHomeName)
        if (currentHome != null && adjustedConfirm?.lowercase() != "confirm" && adjustedConfirm?.lowercase() != "unsafe") {
            val confirmCommand = if (adjustedHomeName != null) "/guild sethome $adjustedHomeName confirm" else "/guild sethome confirm"
            val homeLabel = if (targetHomeName == "main") "main home" else "home '$targetHomeName'"
            player.sendMessage("§c⚠️ Your guild already has a $homeLabel set!")
            player.sendMessage("§7Use §6$confirmCommand §7to replace it")
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
        setGuildHomeCommand(player, guild, location, targetHomeName)
    }
    
    @Subcommand("home")
    @CommandPermission("lumaguilds.guild.home")
    @CommandCompletion("@guildhomes")
    fun onHome(player: Player, @Optional homeName: String?, @Optional confirm: String?) {
        // Handle "/guild home confirm" — ACF puts "confirm" into homeName, not confirm param
        val isConfirm = confirm?.lowercase() == "confirm" || homeName?.lowercase() == "confirm"
        if (isConfirm) {
            val pendingLocation = GuildHomeSafety.consumePending(player)
            if (pendingLocation != null) {
                teleportationService.startTeleport(player, pendingLocation) {
                    lastHomeTeleport[player.uniqueId] = System.currentTimeMillis()
                }
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
        val targetHomeName = homeName ?: "main"
        val home = guildService.getHome(guild.id, targetHomeName)

        if (home != null) {
            if (!guildService.canUseHome(playerId, guild.id, targetHomeName)) {
                player.sendMessage("§c❌ You don't have permission to use the home '$targetHomeName'.")
                player.sendMessage("§7Ask a guild manager to grant your rank access.")
                return
            }
            // Check if player already has an active teleport
            if (teleportationService.hasActiveTeleport(playerId)) {
                player.sendMessage("§cYou already have a teleport in progress. Please wait for it to complete.")
                return
            }

            // Check teleport cooldown (with progression-based multiplier)
            val config = configService.loadConfig()
            val baseCooldownSeconds = config.guild.homeTeleportCooldownSeconds
            val cooldownMultiplier = progressionService.getHomeCooldownMultiplier(guild.id)
            val cooldownSeconds = (baseCooldownSeconds * cooldownMultiplier).toLong()

            val lastTeleport = lastHomeTeleport[playerId]
            if (lastTeleport != null) {
                val elapsedSeconds = (System.currentTimeMillis() - lastTeleport) / 1000
                if (elapsedSeconds < cooldownSeconds) {
                    val remainingSeconds = cooldownSeconds - elapsedSeconds
                    player.sendMessage("§c◷ Please wait ${remainingSeconds}s before teleporting again.")
                    return
                }
            }

            // Get target location
            val world = player.server.getWorld(home.worldId)
            if (world == null) {
                player.sendMessage("§cGuild home world is not available.")
                return
            }

            val targetLocation = Location(
                world,
                home.position.x.toDouble() + 0.5,  // Center of block
                home.position.y.toDouble(),
                home.position.z.toDouble() + 0.5,  // Center of block
                player.location.yaw,
                player.location.pitch
            )

            // Check if target location is safe (if safety check is enabled)
            if (configService.loadConfig().guild.homeTeleportSafetyCheck) {
                if (!GuildHomeSafety.checkOrAskConfirm(player, targetLocation, "/guild home confirm")) {
                    return
                }
            }

            // Start teleport countdown via centralized service
            teleportationService.startTeleport(player, targetLocation) {
                lastHomeTeleport[playerId] = System.currentTimeMillis()
            }
        } else {
            // Check if the guild has any homes at all
            val allHomes = guildService.getHomes(guild.id)
            if (allHomes.hasHomes()) {
                player.sendMessage("§cHome '$targetHomeName' has not been set.")
                player.sendMessage("§7Available homes: §f${allHomes.homeNames.joinToString(", ")}")
                player.sendMessage("§7Use §6/guild home <name> §7to teleport to a specific home.")
            } else {
                player.sendMessage("§cNo guild homes have been set.")
                player.sendMessage("§7Use §6/guild sethome §7to set your first home.")
            }
        }
    }

    @Subcommand("homes")
    @CommandPermission("lumaguilds.guild.home")
    fun onHomes(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        player.sendMessage("§6=== Guild Homes ===")
        if (allHomes.hasHomes()) {
            player.sendMessage("§7Your guild has §f${allHomes.size}§7/${availableSlots}§7 home slots:")
            allHomes.homes.forEach { entry ->
                val name = entry.key
                val home = entry.value
                val marker = if (name == "main") "§e[MAIN]" else ""
                val worldName = Bukkit.getWorld(home.worldId)?.name ?: "Unknown"
                player.sendMessage("§7• §f$name $marker §7- §f$worldName")
            }
            player.sendMessage("§7Use §6/guild home <name> §7to teleport to a home.")
        } else {
            player.sendMessage("§7No homes have been set yet.")
        }

        if (allHomes.size < availableSlots) {
            player.sendMessage("§7Available slots: §f${availableSlots - allHomes.size}")
            player.sendMessage("§7Use §6/guild sethome <name> §7to set additional homes.")
        }
        player.sendMessage("§6==================")
    }

    @Subcommand("removehome")
    @CommandPermission("lumaguilds.guild.sethome")
    @CommandCompletion("@guildhomes")
    fun onRemoveHome(player: Player, homeName: String) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()
        if (guildService.getHome(guild.id, homeName) == null) {
            player.sendMessage("§c❌ Home '$homeName' does not exist.")
            return
        }

        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(net.lumalyte.lg.interaction.menus.common.ConfirmationMenu(
            menuNavigator, player, "§cRemove home '$homeName'?"
        ) {
            val success = guildService.removeHome(guild.id, homeName, playerId)
            if (success) {
                player.sendMessage("§a✅ Home '$homeName' removed.")
            } else {
                player.sendMessage("§c❌ Failed to remove home '$homeName'.")
            }
        })
    }

    @Subcommand("setallyhome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onSetAllyHome(player: Player) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()
        val location = player.location

        val home = GuildHome(
            worldId = location.world.uid,
            position = net.lumalyte.lg.domain.values.Position3D(
                location.x.toInt(), location.y.toInt(), location.z.toInt()
            )
        )

        val success = guildService.setAllyHome(guild.id, home, playerId)
        if (success) {
            player.sendMessage("§a✅ Ally home set to your current location!")
            player.sendMessage("§7Allied guilds with the ally home perk can now teleport here.")
        } else {
            player.sendMessage("§c❌ Failed to set ally home. You may not have permission.")
        }
    }

    @Subcommand("removeallyhome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onRemoveAllyHome(player: Player) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()
        val success = guildService.removeAllyHome(guild.id, playerId)
        if (success) {
            player.sendMessage("§a✅ Ally home removed.")
        } else {
            player.sendMessage("§c❌ Failed to remove ally home. It may not be set or you lack permission.")
        }
    }

    @Subcommand("allyhome")
    @CommandPermission("lumaguilds.guild.allyhome")
    @CommandCompletion("@allyguilds")
    fun onAllyHome(player: Player, guildName: String, @Optional confirm: String?) {
        if (guildName.lowercase() == "confirm" || confirm?.lowercase() == "confirm") {
            val pendingLocation = GuildHomeSafety.consumePending(player)
            if (pendingLocation != null) {
                teleportationService.startTeleport(player, pendingLocation) {
                    lastHomeTeleport[player.uniqueId] = System.currentTimeMillis()
                }
                return
            } else {
                player.sendMessage("§cNo pending unsafe teleport to confirm, or confirmation expired.")
                return
            }
        }

        val playerId = player.uniqueId

        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage("§cNo guild named '$guildName' found.")
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage("§cUse §6/guild home §cfor your own guild's home.")
            return
        }

        val relationService: net.lumalyte.lg.application.services.RelationService by inject()
        val relation = relationService.getRelationType(guild.id, targetGuild.id)
        if (relation != net.lumalyte.lg.domain.entities.RelationType.ALLY) {
            player.sendMessage("§c${targetGuild.name} is not an ally of your guild.")
            return
        }

        val allyHome = guildService.getAllyHome(targetGuild.id)
        if (allyHome == null) {
            player.sendMessage("§c${targetGuild.name} has no ally home set.")
            return
        }

        if (!guildService.canUseAllyHome(playerId, guild.id, targetGuild.id)) {
            player.sendMessage("§c❌ You don't have permission to use ${targetGuild.name}'s ally home.")
            player.sendMessage("§7Your rank may lack USE_ALLY_HOMES, or that guild has not granted access.")
            return
        }

        if (teleportationService.hasActiveTeleport(playerId)) {
            player.sendMessage("§cYou already have a teleport in progress. Please wait for it to complete.")
            return
        }

        val config = configService.loadConfig()
        val baseCooldownSeconds = config.guild.homeTeleportCooldownSeconds
        val cooldownMultiplier = progressionService.getHomeCooldownMultiplier(guild.id)
        val cooldownSeconds = (baseCooldownSeconds * cooldownMultiplier).toLong()

        val lastTeleport = lastHomeTeleport[playerId]
        if (lastTeleport != null) {
            val elapsedSeconds = (System.currentTimeMillis() - lastTeleport) / 1000
            if (elapsedSeconds < cooldownSeconds) {
                val remainingSeconds = cooldownSeconds - elapsedSeconds
                player.sendMessage("§c◷ Please wait ${remainingSeconds}s before teleporting again.")
                return
            }
        }

        val world = player.server.getWorld(allyHome.worldId)
        if (world == null) {
            player.sendMessage("§cAlly guild home world is not available.")
            return
        }

        val targetLocation = Location(
            world,
            allyHome.position.x.toDouble() + 0.5,
            allyHome.position.y.toDouble(),
            allyHome.position.z.toDouble() + 0.5,
            player.location.yaw,
            player.location.pitch
        )

        if (config.guild.homeTeleportSafetyCheck) {
            if (!GuildHomeSafety.checkOrAskConfirm(player, targetLocation, "/guild allyhome ${targetGuild.name} confirm")) {
                return
            }
        }

        teleportationService.startTeleport(player, targetLocation) {
            lastHomeTeleport[playerId] = System.currentTimeMillis()
            player.sendMessage("§a✅ Teleported to §6${targetGuild.name}§a's ally home.")
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
    @CommandCompletion("@unlockedemojis")
    fun onEmoji(player: Player, @Optional emoji: String?) {
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

        // If no emoji parameter provided, open the menu
        if (emoji == null) {
            val menuNavigator = MenuNavigator(player)
            menuNavigator.openMenu(menuFactory.createGuildEmojiMenu(menuNavigator, player, guild))
            return
        }

        // Direct emoji setting via command parameter
        val nexoEmojiService: net.lumalyte.lg.infrastructure.services.NexoEmojiService by inject()

        // Validate emoji format
        if (!nexoEmojiService.isValidEmojiFormat(emoji)) {
            player.sendMessage("§c❌ Invalid emoji format!")
            player.sendMessage("§7Format must be: §f:emoji_name: §7(e.g., §f:cat:§7)")
            return
        }

        // Check if emoji exists in Nexo
        if (!nexoEmojiService.doesEmojiExist(emoji)) {
            player.sendMessage("§c❌ Emoji not found in Nexo registry!")
            player.sendMessage("§7Make sure the emoji is configured in Nexo.")
            return
        }

        // Check if player has permission for this specific emoji
        if (!nexoEmojiService.hasEmojiPermission(player, emoji)) {
            val permission = nexoEmojiService.getEmojiPermission(emoji) ?: "unknown"
            player.sendMessage("§c❌ You don't have permission to use this emoji!")
            player.sendMessage("§7Required permission: §f$permission")
            return
        }

        // Set the emoji
        val success = guildService.setEmoji(guild.id, emoji, playerId)
        if (success) {
            player.sendMessage("§a✅ Guild emoji updated successfully!")
            player.sendMessage("§7New emoji: $emoji")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        } else {
            player.sendMessage("§c❌ Failed to save emoji. Please try again.")
        }
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

        // Check if already in that mode
        if (guild.mode == guildMode) {
            player.sendMessage("§c❌ Guild is already in ${guildMode.name.lowercase().replaceFirstChar { it.uppercase() }} mode!")
            return
        }

        // Validate cooldown based on which mode we're switching to
        if (guildMode == GuildMode.PEACEFUL) {
            // Switching TO peaceful - check cooldown
            val canSwitch = canSwitchToPeaceful(guild, mainConfig.guild.modeSwitchCooldownDays)
            val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }

            if (hasActiveWar) {
                player.sendMessage("§c❌ Cannot switch to peaceful mode during active war!")
                return
            }

            if (!canSwitch) {
                val cooldownMsg = getCooldownMessage(guild, mainConfig.guild.modeSwitchCooldownDays)
                player.sendMessage("§c❌ $cooldownMsg")
                return
            }
        } else if (guildMode == GuildMode.HOSTILE) {
            // Switching TO hostile - check minimum peaceful days
            val canSwitch = canSwitchToHostile(guild, mainConfig.guild.hostileModeMinimumDays)

            if (!canSwitch) {
                val lockMsg = getHostileLockMessage(guild, mainConfig.guild.hostileModeMinimumDays)
                player.sendMessage("§c❌ $lockMsg")
                return
            }
        }

        val success = guildService.setMode(guild.id, guildMode, playerId)

        if (success) {
            player.sendMessage("§a✅ Guild mode changed to ${guildMode.name.lowercase().replaceFirstChar { it.uppercase() }}!")
        } else {
            player.sendMessage("§c❌ Failed to change guild mode. You may not have permission.")
        }
    }

    private fun canSwitchToPeaceful(guild: Guild, cooldownDays: Int): Boolean {
        val modeChanged = guild.modeChangedAt ?: return true

        val cooldownEnd = modeChanged.plus(java.time.Duration.ofDays(cooldownDays.toLong()))
        return java.time.Instant.now().isAfter(cooldownEnd)
    }

    private fun canSwitchToHostile(guild: Guild, minimumDays: Int): Boolean {
        if (guild.mode != GuildMode.PEACEFUL) return true

        val modeChanged = guild.modeChangedAt ?: return true

        val lockEnd = modeChanged.plus(java.time.Duration.ofDays(minimumDays.toLong()))
        return java.time.Instant.now().isAfter(lockEnd)
    }

    private fun getCooldownMessage(guild: Guild, cooldownDays: Int): String {
        val modeChanged = guild.modeChangedAt ?: return "No previous changes"

        val cooldownEnd = modeChanged.plus(java.time.Duration.ofDays(cooldownDays.toLong()))
        val remaining = java.time.Duration.between(java.time.Instant.now(), cooldownEnd)

        if (remaining.isNegative) return "Cooldown expired"

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return "${days}d ${hours}h until you can switch to Peaceful"
    }

    private fun getHostileLockMessage(guild: Guild, minimumDays: Int): String {
        val modeChanged = guild.modeChangedAt ?: return "No previous changes"

        val lockEnd = modeChanged.plus(java.time.Duration.ofDays(minimumDays.toLong()))
        val remaining = java.time.Duration.between(java.time.Instant.now(), lockEnd)

        if (remaining.isNegative) return "Lock expired"

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return "${days}d ${hours}h until you can switch to Hostile"
    }
    
    @Subcommand("history")
    @CommandPermission("lumaguilds.guild.history")
    @CommandCompletion("@players")
    fun onHistory(player: Player, targetPlayerName: String) {
        val onlineTarget = Bukkit.getPlayerExact(targetPlayerName)
        val targetId: java.util.UUID
        val displayName: String

        if (onlineTarget != null) {
            targetId = onlineTarget.uniqueId
            displayName = onlineTarget.name
        } else {
            @Suppress("DEPRECATION")
            val offlineTarget = Bukkit.getOfflinePlayer(targetPlayerName)
            if (!offlineTarget.hasPlayedBefore()) {
                player.sendMessage("§cPlayer '§6$targetPlayerName§c' has never played on this server.")
                return
            }
            targetId = offlineTarget.uniqueId
            displayName = offlineTarget.name ?: targetPlayerName
        }

        val history = historyRepository.getByPlayer(targetId)

        if (history.isEmpty()) {
            player.sendMessage("§7$displayName has no guild history.")
            return
        }

        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(java.time.ZoneId.systemDefault())

        player.sendMessage("§6§l╔══ Guild History: $displayName ══╗")
        player.sendMessage("§7Total guilds joined: §e${history.size}")
        player.sendMessage("")

        history.forEachIndexed { index, entry ->
            val guildName = guildService.getGuild(entry.guildId)?.name
            val guildDisplay = if (guildName != null) "§a$guildName" else "§8[UNKNOWN]"
            val joinDate = formatter.format(entry.joinedAt)

            val suffix = when {
                entry.isOpen -> "§a(current)"
                entry.departureReason == DepartureReason.LEFT -> "§7Left"
                entry.departureReason == DepartureReason.KICKED -> "§cKicked"
                entry.departureReason == DepartureReason.DISBANDED -> "§8Guild Disbanded"
                else -> ""
            }

            player.sendMessage("§f${index + 1}. $guildDisplay §7• Joined §e$joinDate §7• $suffix")
        }

        player.sendMessage("§6§l╚${"═".repeat(20 + displayName.length)}╝")
    }

    @Subcommand("chat")
    @CommandPermission("lumaguilds.guild.chat")
    fun onGuildChat(player: Player) {
        val playerId = player.uniqueId

        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }

        val nowEnabled = guildChatListener.toggleGuildChat(player)
        if (nowEnabled) {
            player.sendMessage("§a✅ §2Guild chat §aenabled§a! Your messages go only to guild members.")
            player.sendMessage("§7Run §f/g chat §7again to return to normal chat.")
        } else {
            player.sendMessage("§7Guild chat §cdisabled§7. Your messages go to main chat.")
        }
    }

    @Subcommand("allychat")
    @CommandPermission("lumaguilds.guild.chat")
    fun onAllyChat(player: Player) {
        val playerId = player.uniqueId

        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }

        val nowEnabled = guildChatListener.toggleAllyChat(player)
        if (nowEnabled) {
            player.sendMessage("§d✅ §5Ally chat §denabled§d! Your messages go to guild + allied guild members.")
            player.sendMessage("§7Run §f/g allychat §7again to return to normal chat.")
        } else {
            player.sendMessage("§7Ally chat §cdisabled§7. Your messages go to main chat.")
        }
    }

    @Subcommand("info")
    @CommandCompletion("@guildsorplayers")
    fun onInfo(player: Player, @Optional targetGuild: String?) {
        val menuNavigator = MenuNavigator(player)

        if (targetGuild != null) {
            // Resolve by guild name (exact / normalized) or by player name
            val targetGuildObj = net.lumalyte.lg.utils.GuildResolver.resolve(targetGuild, guildService)

            if (targetGuildObj == null) {
                player.sendMessage("§cNo guild or player named '$targetGuild' found.")
                return
            }

            // Open the target guild's info menu (no permission restrictions)
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, targetGuildObj))
        } else {
            // Show player's own guild info
            val guilds = guildService.getPlayerGuilds(player.uniqueId)
            if (guilds.isEmpty()) {
                player.sendMessage("§cYou are not in a guild.")
                return
            }

            val guild = guilds.first()
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
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

        if (!adminOverrideService.hasOverride(playerId) && playerRank?.id != highestRank?.id) {
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
        menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
    }

    @Subcommand("invite")
    @CommandPermission("lumaguilds.guild.invite")
    @CommandCompletion("@allplayers")
    fun onInvite(player: Player, targetPlayerName: String) {
        val playerId = player.uniqueId
        player.server.logger.info("Player : ${player} tried to invite ${targetPlayerName}")


        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }
        player.server.logger.info("bugrock guild : ${guilds}")

        val guild = guilds.first()

        // Check if player has member management permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            player.sendMessage("§cYou don't have permission to invite players.")
            return
        }

        // Find target player - handle Floodgate prefix
        val targetPlayer = findPlayerByName(targetPlayerName)
        player.server.logger.info("target player : ${targetPlayer}")
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
        menuNavigator.openMenu(menuFactory.createGuildInviteConfirmationMenu(menuNavigator, player, guild, targetPlayer))
    }

    @Subcommand("join|accept")
    @CommandPermission("lumaguilds.guild.join")
    @CommandCompletion("@guilds")
    fun onJoin(player: Player, guildName: String) {
        val playerId = player.uniqueId
        player.server.logger.info("Guild '${guildName}' Person who tried joining: ${player.name}")

        // Check if player is already in a guild
        val currentGuilds = guildService.getPlayerGuilds(playerId)
        if (currentGuilds.isNotEmpty()) {
            player.sendMessage("§cYou are already in a guild!")
            player.sendMessage("§7Use §e/guild leave§7 to leave your current guild first.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Resolve guild by name (exact / normalized). Player-name resolution is
        // intentionally NOT used here — joining via a player's name is ambiguous.
        val guild = net.lumalyte.lg.utils.GuildResolver.resolveGuildByName(guildName, guildService)
        if (guild == null) {
            player.sendMessage("§cGuild §6$guildName§c doesn't exist!")
            player.sendMessage("§7Check §e/guild list§7 to see available guilds.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Admin override bypasses invitation requirements
        if (adminOverrideService.hasOverride(playerId)) {
            player.sendMessage("§7[Override] Bypassing invitation check.")
            joinGuildDirectly(player, guild, isOpenGuild = guild.isOpen)
            return
        }

        // Check if guild is open - if so, allow direct joining without invitation
        if (guild.isOpen) {
            // Open guild - no invitation required
            joinGuildDirectly(player, guild, isOpenGuild = true)
            return
        }

        // Closed guild - check for pending invite (use canonical guild name, not raw user input)
        val invite = net.lumalyte.lg.infrastructure.services.GuildInvitationManager.getInviteByGuildName(playerId, guild.name)
        if (invite == null) {
            player.sendMessage("§cYou don't have an invitation to join §6$guildName§c!")
            player.sendMessage("§7This guild is invite-only. Ask a member to invite you.")
            player.sendMessage("§7Check §e/guild invites§7 to see your pending invitations.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val (guildId, actualGuildName) = invite

        // Verify the guild still exists
        if (guild.id != guildId) {
            player.sendMessage("§cThat guild no longer exists!")
            net.lumalyte.lg.infrastructure.services.GuildInvitationManager.removeInvite(playerId, guildId)
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Join through invitation
        joinGuildDirectly(player, guild, isOpenGuild = false)
    }

    /**
     * Helper function to join a guild (either via invitation or open guild)
     */
    private fun joinGuildDirectly(player: Player, guild: Guild, isOpenGuild: Boolean) {
        val playerId = player.uniqueId
        val guildId = guild.id

        // Add player to guild with lowest rank
        val ranks = rankService.listRanks(guildId).sortedByDescending { it.priority }
        val lowestRank = ranks.firstOrNull()

        if (lowestRank == null) {
            player.sendMessage("§cGuild has no ranks configured. Please contact the guild owner.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Add the member
        val newMember = memberService.addMember(playerId, guildId, lowestRank.id)

        if (newMember != null) {
            // Remove the invitation (if they had one)
            net.lumalyte.lg.infrastructure.services.GuildInvitationManager.removeInvite(playerId, guildId)

            player.sendMessage("")
            player.sendMessage("§a§l✅ JOINED GUILD!")
            player.sendMessage("§7You are now a member of §6${guild.name}§7!")
            if (isOpenGuild) {
                player.sendMessage("§7Guild Type: §aOPEN §7(public)")
            }
            player.sendMessage("§7Rank: §f${lowestRank.name}")
            player.sendMessage("§7Use §e/guild menu§7 to get started.")
            player.sendMessage("")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)

            // Notify guild members
            val guildMembers = memberService.getGuildMembers(guildId)
            guildMembers.forEach { member ->
                if (member.playerId != playerId) {
                    val memberPlayer = player.server.getPlayer(member.playerId)
                    if (memberPlayer != null && memberPlayer.isOnline) {
                        memberPlayer.sendMessage("§a${player.name}§7 has joined the guild!")
                        memberPlayer.playSound(memberPlayer.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
                    }
                }
            }
        } else {
            player.sendMessage("§cFailed to join guild. Please contact an administrator.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    @Subcommand("list")
    @CommandPermission("lumaguilds.guild.list")
    fun onList(player: Player) {
        val allGuilds = guildRepository.getAll()
        val openGuilds = allGuilds.filter { it.isOpen }

        if (openGuilds.isEmpty()) {
            player.sendMessage("")
            player.sendMessage("§6§l🏛 PUBLIC GUILDS")
            player.sendMessage("")
            player.sendMessage("§7No open guilds available at the moment.")
            player.sendMessage("§7Open guilds allow anyone to join without an invitation!")
            player.sendMessage("")
            player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
            return
        }

        player.sendMessage("")
        player.sendMessage("§6§l🏛 PUBLIC GUILDS (${openGuilds.size})")
        player.sendMessage("§7Anyone can join these guilds!")
        player.sendMessage("")

        openGuilds.sortedByDescending { memberService.getMemberCount(it.id) }.take(10).forEach { guild ->
            val memberCount = memberService.getMemberCount(guild.id)
            val emoji = guild.emoji ?: ""
            val tag = guild.tag ?: guild.name

            player.sendMessage("§a▸ §6$emoji $tag §7[${memberCount} members]")
            player.sendMessage("  §7Join: §e/guild join ${guild.name}")
            player.sendMessage("")
        }

        if (openGuilds.size > 10) {
            player.sendMessage("§7... and ${openGuilds.size - 10} more open guilds")
            player.sendMessage("")
        }

        player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
    }

    @Subcommand("lfg")
    @CommandPermission("lumaguilds.guild.lfg")
    fun onLfg(player: Player) {
        val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
        val menuNavigator = net.lumalyte.lg.interaction.menus.MenuNavigator(player)

        menuNavigator.openMenu(menuFactory.createLfgBrowserMenu(menuNavigator, player))
        player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
    }

    @Subcommand("decline")
    @CommandPermission("lumaguilds.guild.decline")
    @CommandCompletion("@pendinginvites")
    fun onDecline(player: Player, guildName: String) {
        val playerId = player.uniqueId

        // Resolve invite using exact name first, then normalized name match across
        // the player's pending invites so colored/lowercased input also works.
        val invites = net.lumalyte.lg.infrastructure.services.GuildInvitationManager.getInvites(playerId)
        val needle = net.lumalyte.lg.utils.GuildResolver.normalize(guildName)
        val invite = invites.firstOrNull { it.second.equals(guildName, ignoreCase = true) }
            ?: invites.firstOrNull { net.lumalyte.lg.utils.GuildResolver.normalize(it.second) == needle }
        if (invite == null) {
            player.sendMessage("§cYou don't have an invitation to join §6$guildName§c!")
            player.sendMessage("§7Check §e/guild invites§7 to see your pending invitations.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val (guildId, actualGuildName) = invite

        // Remove the invitation
        net.lumalyte.lg.infrastructure.services.GuildInvitationManager.removeInvite(playerId, guildId)

        player.sendMessage("§7You declined the invitation to join §6$actualGuildName§7.")
        player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f)
    }

    @Subcommand("invites")
    @CommandPermission("lumaguilds.guild.invites")
    fun onInvites(player: Player) {
        val playerId = player.uniqueId
        val invites = net.lumalyte.lg.infrastructure.services.GuildInvitationManager.getInvites(playerId)

        if (invites.isEmpty()) {
            player.sendMessage("§7You have no pending guild invitations.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        player.sendMessage("")
        player.sendMessage("§6§l📨 PENDING GUILD INVITATIONS (${invites.size})")
        player.sendMessage("")
        invites.forEach { (_, guildName) ->
            player.sendMessage("§7• §6$guildName")
            player.sendMessage("  §7Accept: §a/guild join $guildName")
            player.sendMessage("  §7Decline: §c/guild decline $guildName")
            player.sendMessage("")
        }
        player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
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

        // Find target player - try online first, then offline
        val targetPlayer = findPlayerByName(targetPlayerName)

        if (targetPlayer != null) {
            if (targetPlayer == player) {
                player.sendMessage("§cYou cannot kick yourself.")
                return
            }

            val targetMember = memberService.getMember(targetPlayer.uniqueId, guild.id)
            if (targetMember == null) {
                player.sendMessage("§c${targetPlayer.name} is not in your guild!")
                return
            }

            val menuNavigator = MenuNavigator(player)
            menuNavigator.openMenu(menuFactory.createGuildKickConfirmationMenu(menuNavigator, player, guild, targetMember))
        } else {
            // Player is offline — resolve from guild member list
            val targetMember = findGuildMemberByName(guild.id, targetPlayerName)
            if (targetMember == null) {
                player.sendMessage("§cNo guild member named '$targetPlayerName' found.")
                return
            }

            if (targetMember.playerId == playerId) {
                player.sendMessage("§cYou cannot kick yourself.")
                return
            }

            val kickerRank = rankService.getPlayerRank(playerId, guild.id)
            val targetRank = rankService.getPlayerRank(targetMember.playerId, guild.id)
            if (kickerRank == null || targetRank == null || targetRank.priority <= kickerRank.priority) {
                player.sendMessage("§c❌ You cannot kick a member of equal or higher rank.")
                return
            }

            val success = memberService.removeMember(targetMember.playerId, guild.id, playerId)
            if (success) {
                val resolvedName = Bukkit.getOfflinePlayer(targetMember.playerId).name ?: targetPlayerName
                player.sendMessage("§a✅ $resolvedName has been kicked from the guild.")
            } else {
                player.sendMessage("§c❌ Failed to kick '$targetPlayerName'.")
            }
        }
    }

    @Subcommand("leave")
    @CommandPermission("lumaguilds.guild.leave")
    fun onLeave(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val guild = guilds.first()

        // Check if player is the owner (priority 0 rank) - handle automatic succession
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        if (playerRank?.priority == 0) {
            // Owner is leaving - check if there are other members
            val allMembers = memberService.getGuildMembers(guild.id)
            val otherMembers = allMembers.filter { it.playerId != playerId }

            if (otherMembers.isEmpty()) {
                // No other members - owner must disband
                player.sendMessage("§cYou are the only member of this guild.")
                player.sendMessage("§7Use §e/guild disband§7 to delete the guild.")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                return
            }

            // Find the next highest rank member (lowest priority number after 0)
            val nextOwner = otherMembers.mapNotNull { member ->
                val rank = rankService.getPlayerRank(member.playerId, guild.id)
                rank?.let { member to it }
            }.minByOrNull { (_, rank) -> rank.priority }

            if (nextOwner == null) {
                player.sendMessage("§cFailed to find a successor. Please contact an administrator.")
                return
            }

            val (successorMember, successorRank) = nextOwner

            // Transfer ownership automatically
            val transferSuccess = memberService.transferOwnership(guild.id, playerId, successorMember.playerId)
            if (!transferSuccess) {
                player.sendMessage("§cFailed to transfer ownership automatically. Use §e/guild transfer <player>§c instead.")
                return
            }

            // Notify about succession
            val successorPlayer = player.server.getPlayer(successorMember.playerId)
            if (successorPlayer != null) {
                successorPlayer.sendMessage("§6§l✦ OWNERSHIP TRANSFERRED ✦")
                successorPlayer.sendMessage("§a${player.name} has left the guild and you are now the owner!")
                successorPlayer.sendMessage("§7Use §e/guild menu§7 to manage your guild.")
            }

            player.sendMessage("§7Ownership automatically transferred to §e${successorPlayer?.name ?: "the next highest rank"}§7.")
        }

        // Remove player from guild
        val success = memberService.removeMember(playerId, guild.id, playerId)

        if (success) {
            player.sendMessage("§aYou have left §6${guild.name}§a.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f)

            // Notify guild members
            val guildMembers = memberService.getGuildMembers(guild.id)
            guildMembers.forEach { member ->
                val memberPlayer = player.server.getPlayer(member.playerId)
                if (memberPlayer != null && memberPlayer.isOnline) {
                    memberPlayer.sendMessage("§e${player.name}§7 has left the guild.")
                    memberPlayer.playSound(memberPlayer.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f)
                }
            }
        } else {
            player.sendMessage("§cFailed to leave guild. Please contact an administrator.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    @Subcommand("transfer")
    @CommandPermission("lumaguilds.guild.transfer")
    @CommandCompletion("@guildmembers")
    fun onTransfer(player: Player, targetPlayerName: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player is the owner (priority 0 rank)
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        if (playerRank?.priority != 0) {
            player.sendMessage("§cOnly the guild owner can transfer ownership.")
            return
        }

        // Find target player - handle Floodgate prefix
        val targetPlayer = findPlayerByName(targetPlayerName)
        if (targetPlayer == null) {
            player.sendMessage("§cPlayer '$targetPlayerName' not found or is not online.")
            return
        }

        if (targetPlayer == player) {
            player.sendMessage("§cYou cannot transfer ownership to yourself.")
            return
        }

        // Check if target is in the guild
        val targetMember = memberService.getMember(targetPlayer.uniqueId, guild.id)
        if (targetMember == null) {
            player.sendMessage("§c${targetPlayer.name} is not in your guild!")
            return
        }

        // Perform ownership transfer
        val success = memberService.transferOwnership(guild.id, playerId, targetPlayer.uniqueId)

        if (success) {
            player.sendMessage("§aOwnership of §6${guild.name}§a has been transferred to §e${targetPlayer.name}§a.")
            player.sendMessage("§7You are now a §eCo-Owner§7.")

            targetPlayer.sendMessage("§6§l✦ PROMOTION ✦")
            targetPlayer.sendMessage("§aYou are now the owner of §6${guild.name}§a!")
            targetPlayer.sendMessage("§7Use §e/guild menu§7 to manage your guild.")

            // Notify all other guild members
            val guildMembers = memberService.getGuildMembers(guild.id)
            guildMembers.forEach { member ->
                if (member.playerId != playerId && member.playerId != targetPlayer.uniqueId) {
                    val memberPlayer = player.server.getPlayer(member.playerId)
                    memberPlayer?.sendMessage("§e${player.name}§7 has transferred ownership of the guild to §e${targetPlayer.name}§7.")
                }
            }
        } else {
            player.sendMessage("§cFailed to transfer ownership. Please contact an administrator.")
        }
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
        menuNavigator.openMenu(menuFactory.createTagEditorMenu(menuNavigator, player, guild))
        return
        }

        // Validate tag — mirrors TagEditorMenu.validateTag for consistency
        if (tag.trim().isEmpty()) {
            player.sendMessage("§cGuild tag cannot be empty.")
            return
        }

        if (tag.contains("<<") || tag.contains(">>")) {
            player.sendMessage("§cInvalid tag syntax: double brackets.")
            return
        }

        val miniMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        val visibleChars = try {
            val component = miniMessage.deserialize(tag)
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component).length
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Invalid format"
            val msg = when {
                errorMsg.contains("unclosed", ignoreCase = true) -> "Unclosed tag (missing closing tag)"
                errorMsg.contains("unknown tag", ignoreCase = true) -> "Unknown tag format"
                errorMsg.contains("invalid", ignoreCase = true) -> "Invalid MiniMessage syntax"
                else -> "Format error: ${errorMsg.take(50)}"
            }
            player.sendMessage("§c❌ Invalid tag: $msg")
            return
        }

        if (visibleChars > 32) {
            player.sendMessage("§cGuild tag too long ($visibleChars/32 visible characters).")
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
            menuNavigator.openMenu(menuFactory.createDescriptionEditorMenu(menuNavigator, player, guild))
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
        menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        player.sendMessage("§6⚔ Opening war management menu...")
    }

    private fun setGuildHomeCommand(player: Player, guild: net.lumalyte.lg.domain.entities.Guild, location: org.bukkit.Location, homeName: String = "main") {
        val home = GuildHome(
            worldId = location.world.uid,
            position = location.toPosition3D()
        )

        val config = configService.loadConfig()

        // Check if location is safe (if safety check is enabled)
        if (config.guild.homeTeleportSafetyCheck) {
            val safetyResult = GuildHomeSafety.evaluateSafety(location)
            if (!safetyResult.safe) {
                player.sendMessage("§e[Warning] §7That home looks unsafe: §c${safetyResult.reason}")
                player.sendMessage("§7Use §6/guild sethome confirm §7within 10s to set anyway.")
                return
            }
        }

        val success = guildService.setHome(guild.id, homeName, home, player.uniqueId)

        if (success) {
            val homeLabel = if (homeName == "main") "main home" else "home '$homeName'"
            player.sendMessage("§a✅ Guild $homeLabel set successfully!")
            if (config.claimsEnabled) {
                player.sendMessage("§7This location is within your guild's claim area.")
            }
            player.sendMessage("§7Members can now use §6/guild home §7to teleport here.")
        } else {
            player.sendMessage("§c❌ Failed to set guild home. You may not have permission.")
        }
    }

    private fun isLocationSafe(location: org.bukkit.Location): Boolean {
        return GuildHomeSafety.evaluateSafety(location).safe
    }

    /**
     * Find a player by name, handling Floodgate prefixes for Bedrock players.
     * Tries normal lookup first, then with Floodgate prefix if available.
     */
    private fun findPlayerByName(playerName: String): Player? {
        // Try normal lookup first
        var targetPlayer = Bukkit.getServer().getPlayer(playerName)
        if (targetPlayer != null) {
            return targetPlayer
        }

        // Try with Floodgate prefix if available
        try {
            val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
            val prefix = floodgateApi.playerPrefix

            // Try lookup with prefix
            targetPlayer = Bukkit.getServer().getPlayer("$prefix$playerName")
            if (targetPlayer != null) {
                return targetPlayer
            }
        } catch (e: Exception) {
            // Command handler - catching all exceptions to prevent command crash
            // Floodgate not available or failed - that's okay
        }

        return null
    }

    private fun findGuildMemberByName(guildId: UUID, name: String): net.lumalyte.lg.domain.entities.Member? {
        val members = memberService.getGuildMembers(guildId)
        for (member in members) {
            val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: continue
            if (playerName.equals(name, ignoreCase = true)) {
                return member
            }
        }
        return null
    }

    @Subcommand("getvault")
    @CommandPermission("lumaguilds.guild.getvault")
    fun onVaultGet(player: Player) {
        val playerId = player.uniqueId

        // Check if physical vault is enabled in config
        val vaultConfig = configService.loadConfig().vault
        val bankMode = vaultConfig.bankMode.uppercase()
        if (bankMode != "PHYSICAL" && bankMode != "BOTH") {
            player.sendMessage("§cPhysical vault system is not enabled on this server.")
            player.sendMessage("§7Contact a server administrator if you think this is incorrect.")
            return
        }

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if player has PLACE_VAULT permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.PLACE_VAULT)) {
            player.sendMessage("§c§lPERMISSION DENIED§r")
            player.sendMessage("§cYou don't have permission to get a guild vault chest.")
            player.sendMessage("§7You need the PLACE_VAULT permission.")
            return
        }

        // Check if vault already exists
        if (guild.vaultStatus == net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE) {
            val vaultLocation = vaultService.getVaultLocation(guild)
            if (vaultLocation != null) {
                player.sendMessage("§e§lVAULT EXISTS§r")
                player.sendMessage("§eYour guild already has a vault chest placed!")
                player.sendMessage("§7Location: §f${vaultLocation.world?.name} (${vaultLocation.blockX}, ${vaultLocation.blockY}, ${vaultLocation.blockZ})")
                player.sendMessage("§7Break the existing vault first if you want to move it.")
                return
            }
        }

        // Check if player has space in inventory
        if (player.inventory.firstEmpty() == -1) {
            player.sendMessage("§c§lINVENTORY FULL§r")
            player.sendMessage("§cYour inventory is full! Make space to receive the Guild Vault.")
            return
        }

        // Create the special Guild Vault chest item
        val vaultChest = org.bukkit.inventory.ItemStack.of(org.bukkit.Material.CHEST)
        val meta = vaultChest.itemMeta

        // Use guild's colored tag if set, otherwise use green name
        val guildDisplay = if (!guild.tag.isNullOrBlank()) {
            // Guild has a custom tag - parse it with MiniMessage
            val miniMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
            try {
                miniMessage.deserialize(guild.tag)
            } catch (e: Exception) {
            // Command handler - catching all exceptions to prevent command crash
                // If tag parsing fails, fall back to plain tag
                net.kyori.adventure.text.Component.text(guild.tag)
            }
        } else {
            // No tag set - use green guild name
            net.kyori.adventure.text.Component.text(guild.name, net.kyori.adventure.text.format.NamedTextColor.GREEN)
        }

        // Build the full display name: "⚑ GUILD VAULT (GuildTag)"
        val displayName = net.kyori.adventure.text.Component.text("§6§l⚑ GUILD VAULT §r§7(")
            .append(guildDisplay)
            .append(net.kyori.adventure.text.Component.text("§7)"))
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)

        meta.displayName(displayName)

        meta.lore(listOf(
            net.kyori.adventure.text.Component.text("§7Place this chest to create your guild's"),
            net.kyori.adventure.text.Component.text("§7physical vault storage."),
            net.kyori.adventure.text.Component.text(""),
            net.kyori.adventure.text.Component.text("§eCapacity: §f${vaultService.getCapacityForLevel(guild.level)} slots §7(Level ${guild.level})"),
            net.kyori.adventure.text.Component.text("§eGuild: §f${guild.name}"),
            net.kyori.adventure.text.Component.text(""),
            net.kyori.adventure.text.Component.text("§6⚠ §7Only one vault can exist per guild!"),
            net.kyori.adventure.text.Component.text("§6⚠ §7Protected - Only guild members can break it")
        ).map { it.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false) })

        // Add persistent data to identify this as a guild vault chest
        meta.persistentDataContainer.set(net.lumalyte.lg.common.PluginKeys.GUILD_VAULT_ID, org.bukkit.persistence.PersistentDataType.STRING, guild.id.toString())

        vaultChest.itemMeta = meta

        // Give the item to the player
        player.inventory.addItem(vaultChest)

        player.sendMessage("§a§l✓ VAULT CHEST RECEIVED§r")
        player.sendMessage("§aYou've received a Guild Vault chest!")
        player.sendMessage("§7")
        player.sendMessage("§6How to use:")
        player.sendMessage("§7 1. §fFind a safe location in your guild territory")
        player.sendMessage("§7 2. §fPlace the chest on the ground")
        player.sendMessage("§7 3. §fAccess it through §6/guild menu §7→ Bank")
        player.sendMessage("§7")
        player.sendMessage("§eCapacity: §f${vaultService.getCapacityForLevel(guild.level)} slots")
        player.sendMessage("§eUpgrades as your guild levels up!")

        // Play success sound
        player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f)
        player.playSound(player.location, org.bukkit.Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f)
    }

    @Subcommand("vault")
    @CommandPermission("lumaguilds.guild.vault")
    fun onVault(player: Player) {
        val playerId = player.uniqueId

        // Check if physical vault is enabled in config
        val vaultConfig = configService.loadConfig().vault
        val bankMode = vaultConfig.bankMode.uppercase()
        if (bankMode != "PHYSICAL" && bankMode != "BOTH") {
            player.sendMessage("§cPhysical vault system is not enabled on this server.")
            player.sendMessage("§7Contact a server administrator if you think this is incorrect.")
            return
        }

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check if vault is available
        if (guild.vaultStatus != net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE) {
            player.sendMessage("§c§lVAULT UNAVAILABLE§r")
            when (guild.vaultStatus) {
                net.lumalyte.lg.domain.entities.VaultStatus.NEVER_PLACED -> {
                    player.sendMessage("§cYour guild hasn't placed a vault yet!")
                    player.sendMessage("§7Use §6/guild getvault §7to get a vault chest.")
                }
                net.lumalyte.lg.domain.entities.VaultStatus.UNAVAILABLE -> {
                    player.sendMessage("§cYour guild's vault chest has been destroyed!")
                    player.sendMessage("§7Use §6/guild getvault §7to get a new vault chest.")
                }
                else -> {
                    player.sendMessage("§cVault is not available.")
                }
            }
            return
        }

        // Check if player has ACCESS_VAULT permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.ACCESS_VAULT)) {
            player.sendMessage("§c§lPERMISSION DENIED§r")
            player.sendMessage("§cYou don't have permission to access the guild vault.")
            player.sendMessage("§7You need the ACCESS_VAULT permission.")
            return
        }

        // Open vault inventory
        val result = vaultService.openVaultInventory(player, guild)
        when (result) {
            is net.lumalyte.lg.application.services.VaultResult.Success -> {
                player.sendMessage("§a§lVAULT OPENED§r")
                player.sendMessage("§aAccessing §6${guild.name}§a's vault...")
                player.playSound(player.location, org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f)
            }
            is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                player.sendMessage("§c§lFAILED§r")
                player.sendMessage("§cCouldn't open vault: ${result.message}")
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
            }
        }
    }

    @Subcommand("setshop")
    @CommandPermission("lumaguilds.guild.setshop")
    fun onSetShop(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§c❌ You are not in a guild!")
            return
        }

        val guild = guilds.first()

        // Check permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)) {
            player.sendMessage("§c❌ You don't have permission to convert shops to guild shops!")
            player.sendMessage("§7You need the MANAGE_GUILD_SETTINGS permission.")
            return
        }

        // Check if ARM-Guilds-Bridge is available
        val armBridgePlugin = Bukkit.getPluginManager().getPlugin("ARM-Guilds-Bridge")
        if (armBridgePlugin == null || !armBridgePlugin.isEnabled) {
            player.sendMessage("§c❌ Guild shops feature is not available!")
            player.sendMessage("§7Contact a server administrator - ARM-Guilds-Bridge is required.")
            return
        }

        // Get ARM-Guilds-Bridge services
        val armBridge = armBridgePlugin as net.lumalyte.armbridge.ARMGuildsBridge
        val guildShopService = armBridge.getGuildShopService()
        val itemShopGuildService = armBridge.getItemShopGuildService()

        // Check if standing in a guild-owned ARM region
        val playerLoc = player.location
        val armPlugin = Bukkit.getPluginManager().getPlugin("AdvancedRegionMarket")

        if (armPlugin == null || !armPlugin.isEnabled) {
            player.sendMessage("§c❌ ARM plugin not found!")
            return
        }

        val arm = armPlugin as net.alex9849.arm.AdvancedRegionMarket
        val region = arm.adapterHandler.getRegion(playerLoc)

        if (region == null) {
            player.sendMessage("§c❌ You must be standing in an ARM region to convert this shop!")
            player.sendMessage("§7Guild shops can only be created in guild-owned market regions.")
            return
        }

        // Check if region is owned by the player's guild
        val regionOwner = guildShopService.getGuildForShopRegion(region.id, playerLoc.world?.name ?: "")
        if (regionOwner == null || regionOwner != guild.id) {
            player.sendMessage("§c❌ This ARM region is not owned by your guild!")
            player.sendMessage("§7Your guild must own this market region to create guild shops here.")
            if (regionOwner != null) {
                player.sendMessage("§7This region is owned by a different guild.")
            }
            return
        }

        // Check if ItemShops plugin is available
        val itemShopsPlugin = Bukkit.getPluginManager().getPlugin("ItemShops")
        if (itemShopsPlugin == null || !itemShopsPlugin.isEnabled) {
            player.sendMessage("§c❌ ItemShops plugin not found!")
            return
        }

        // Check if there's a shop at this location (we'll validate in ItemShops later)
        // For now, just register it
        val shopLocation = playerLoc.block.location

        // Register the shop
        if (itemShopGuildService.registerGuildItemShop(shopLocation, guild.id, playerId)) {
            player.sendMessage("§a✅ Shop converted to guild shop!")
            player.sendMessage("§7All income from this shop will now go to §6${guild.name}§7's vault.")
            player.sendMessage("§7Guild permissions now apply to this shop.")

            // Notify guild members
            notifyGuildMembers(guild.id, "§6★ §e${player.name} §7converted a shop to a guild shop!")
        } else {
            player.sendMessage("§c❌ Failed to convert shop!")
            player.sendMessage("§7Contact a server administrator if this problem persists.")
        }
    }

    @Subcommand("help")
    @CommandPermission("lumaguilds.guild.help")
    @CommandCompletion("create|tag|home|relations|chat|vault")
    fun onHelp(player: Player, @Optional topic: String?) {
        when (topic?.lowercase()) {
            "create", "name" -> {
                player.sendMessage("§6§l=== Guild Name & Tag Guide ===")
                player.sendMessage("§7")
                player.sendMessage("§e📝 Guild Name (Plain Text)")
                player.sendMessage("§7 • Command: §f/guild create <name>")
                player.sendMessage("§7 • Max 32 characters")
                player.sendMessage("§7 • Letters, numbers, spaces, and: ' & -")
                player.sendMessage("§7 • No formatting tags allowed")
                player.sendMessage("§7 • Example: §fWhite Lotus §7or §fFire & Ice")
                player.sendMessage("§7")
                player.sendMessage("§e🎨 Guild Tag (Fancy Formatting)")
                player.sendMessage("§7 • Command: §f/guild tag <formatted_text>")
                player.sendMessage("§7 • Use MiniMessage formatting")
                player.sendMessage("§7 • Supports colors, gradients, effects")
                player.sendMessage("§7 • Examples:")
                player.sendMessage("§7   §f/guild tag <red>Fire</red><gold>Guild</gold>")
                player.sendMessage("§7   §f/guild tag <gradient:#FF0000:#00FF00>Rainbow</gradient>")
                player.sendMessage("§7   §f/guild tag <bold><blue>ELITE</blue></bold>")
                player.sendMessage("§7")
                player.sendMessage("§6💡 Remember: Name = Plain, Tag = Fancy!")
            }
            "tag" -> {
                player.sendMessage("§6§l=== Guild Tag Help ===")
                player.sendMessage("§7")
                player.sendMessage("§eGuild tags let you add fancy formatting!")
                player.sendMessage("§7")
                player.sendMessage("§7Commands:")
                player.sendMessage("§7 • Set tag: §f/guild tag <formatted_text>")
                player.sendMessage("§7 • Open menu: §f/guild tag")
                player.sendMessage("§7")
                player.sendMessage("§7Examples:")
                player.sendMessage("§7 • Single color: §f<red>MyGuild</red>")
                player.sendMessage("§7 • Two colors: §f<red>Fire</red><gold>Guild</gold>")
                player.sendMessage("§7 • Gradient: §f<gradient:#FF0000:#00FF00>Rainbow</gradient>")
                player.sendMessage("§7 • Bold: §f<bold><blue>ELITE</blue></bold>")
                player.sendMessage("§7")
                player.sendMessage("§6💡 TIP: Visit minimessage.net for more formatting!")
            }
            else -> {
                player.sendMessage("§6§l=== Guild Commands ===")
                player.sendMessage("§7")
                player.sendMessage("§eBasic:")
                player.sendMessage("§7 • §f/g create <name> §7- Create a guild")
                player.sendMessage("§7 • §f/g menu §7- Open guild menu")
                player.sendMessage("§7 • §f/g info [guild|player] §7- View guild info")
                player.sendMessage("§7 • §f/g list §7- List all guilds")
                player.sendMessage("§7 • §f/g lfg §7- Browse guilds looking for members")
                player.sendMessage("§7 • §f/g disband §7- Disband your guild")
                player.sendMessage("§7")
                player.sendMessage("§eMembership:")
                player.sendMessage("§7 • §f/g invite <player> §7- Invite a player")
                player.sendMessage("§7 • §f/g join|accept <guild> §7- Join/accept invite")
                player.sendMessage("§7 • §f/g decline <guild> §7- Decline an invite")
                player.sendMessage("§7 • §f/g invites §7- List pending invites")
                player.sendMessage("§7 • §f/g kick <player> §7- Kick a member")
                player.sendMessage("§7 • §f/g leave §7- Leave your guild")
                player.sendMessage("§7 • §f/g transfer <player> §7- Transfer ownership")
                player.sendMessage("§7 • §f/g history <player> §7- View member history")
                player.sendMessage("§7 • §f/g ranks §7- List guild ranks")
                player.sendMessage("§7")
                player.sendMessage("§eHomes:")
                player.sendMessage("§7 • §f/g home [name] §7- Teleport to guild home")
                player.sendMessage("§7 • §f/g homes §7- List guild homes")
                player.sendMessage("§7 • §f/g sethome [name] §7- Set a guild home")
                player.sendMessage("§7 • §f/g removehome <name> §7- Remove a guild home")
                player.sendMessage("§7 • §f/g setallyhome §7- Set the ally home")
                player.sendMessage("§7 • §f/g removeallyhome §7- Remove the ally home")
                player.sendMessage("§7 • §f/g allyhome <guild> §7- Teleport to an ally's home")
                player.sendMessage("§7")
                player.sendMessage("§eRelations:")
                player.sendMessage("§7 • §f/g ally <guild> §7- Request alliance")
                player.sendMessage("§7 • §f/g enemy <guild> §7- Declare war")
                player.sendMessage("§7 • §f/g truce <guild> [days] §7- Request truce")
                player.sendMessage("§7 • §f/g neutral <guild> §7- Request peace")
                player.sendMessage("§7 • §f/g war §7- Open war menu")
                player.sendMessage("§7")
                player.sendMessage("§eCustomization:")
                player.sendMessage("§7 • §f/g tag §7- Set fancy formatted tag")
                player.sendMessage("§7 • §f/g rename <name> §7- Rename guild")
                player.sendMessage("§7 • §f/g desc <text> §7- Set description")
                player.sendMessage("§7 • §f/g emoji [:name:] §7- Set guild emoji")
                player.sendMessage("§7 • §f/g mode <peaceful|hostile> §7- Switch mode")
                player.sendMessage("§7")
                player.sendMessage("§eChat:")
                player.sendMessage("§7 • §f/g chat §7- Toggle guild chat")
                player.sendMessage("§7 • §f/g allychat §7- Toggle ally chat")
                player.sendMessage("§7")
                player.sendMessage("§eVault & Shop:")
                player.sendMessage("§7 • §f/g vault §7- Open guild vault")
                player.sendMessage("§7 • §f/g getvault §7- Get a vault item")
                player.sendMessage("§7 • §f/g setshop §7- Convert shop to guild shop")
                player.sendMessage("§7")
                player.sendMessage("§eFor detailed help:")
                player.sendMessage("§7 • §f/g help create §7- Guild name & tag guide")
                player.sendMessage("§7 • §f/g help tag §7- Tag formatting examples")
            }
        }
    }

    @Subcommand("ally")
    @CommandPermission("lumaguilds.guild.ally")
    @CommandCompletion("@guilds")
    fun onAlly(player: Player, guildName: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check MANAGE_RELATIONS permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage("§cYou don't have permission to manage guild relations.")
            player.sendMessage("§7You need the MANAGE_RELATIONS permission.")
            return
        }

        // Resolve target guild (by name or by player name)
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage("§cNo guild or player named '$guildName' found.")
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage("§cYou cannot ally with your own guild.")
            return
        }

        // Get relation service
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()

        // Check current relation
        val currentRelation = relationService.getRelationType(guild.id, targetGuild.id)
        if (currentRelation == net.lumalyte.lg.domain.entities.RelationType.ALLY) {
            player.sendMessage("§cYou are already allied with ${targetGuild.name}!")
            return
        }

        if (currentRelation == net.lumalyte.lg.domain.entities.RelationType.ENEMY) {
            player.sendMessage("§cYou are at war with ${targetGuild.name}!")
            player.sendMessage("§7Request a truce first: §6/guild truce ${targetGuild.name}")
            return
        }

        // Check for pending requests
        val pendingRequests = relationService.getPendingRequests(guild.id)
        if (pendingRequests.any { it.getOtherGuild(guild.id) == targetGuild.id }) {
            player.sendMessage("§cYou already have a pending request with ${targetGuild.name}.")
            return
        }

        // Request alliance
        val relation = relationService.requestAlliance(guild.id, targetGuild.id, playerId)
        if (relation != null) {
            player.sendMessage("§a✓ Alliance request sent to ${targetGuild.name}!")
            player.sendMessage("§7They must accept your request for the alliance to become active.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, "§6${guild.name} §7has requested an alliance with your guild! Use §6/guild menu §7→ Relations to respond.")
        } else {
            player.sendMessage("§c✗ Failed to send alliance request.")
            player.sendMessage("§7There may already be a pending request.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    @Subcommand("enemy")
    @CommandPermission("lumaguilds.guild.enemy")
    @CommandCompletion("@guilds")
    fun onEnemy(player: Player, guildName: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check DECLARE_WAR permission (specific permission for war)
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage("§cYou don't have permission to declare war.")
            player.sendMessage("§7You need the DECLARE_WAR permission.")
            return
        }

        // Resolve target guild (by name or by player name)
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage("§cNo guild or player named '$guildName' found.")
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage("§cYou cannot declare war on your own guild.")
            return
        }

        // Get relation service
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()

        // Check current relation
        val currentRelation = relationService.getRelationType(guild.id, targetGuild.id)
        if (currentRelation == net.lumalyte.lg.domain.entities.RelationType.ENEMY) {
            player.sendMessage("§cYou are already at war with ${targetGuild.name}!")
            return
        }

        if (currentRelation == net.lumalyte.lg.domain.entities.RelationType.ALLY) {
            player.sendMessage("§cYou are allied with ${targetGuild.name}!")
            player.sendMessage("§7You must break the alliance first through the relations menu.")
            return
        }

        // Declare war (immediate effect)
        val relation = relationService.declareWar(guild.id, targetGuild.id, playerId)
        if (relation != null) {
            player.sendMessage("§c⚔ War declared against ${targetGuild.name}!")
            player.sendMessage("§7Your guilds are now enemies.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, "§c⚔ ${guild.name} §chas declared war on your guild!")

            // Broadcast to all online players
            net.lumalyte.lg.utils.ChatUtils.broadcastMessage("§c⚔ §6${guild.name} §chas declared war on §6${targetGuild.name}§c!", player)
        } else {
            player.sendMessage("§c✗ Failed to declare war.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    @Subcommand("truce")
    @CommandPermission("lumaguilds.guild.truce")
    @CommandCompletion("@guilds")
    fun onTruce(player: Player, guildName: String, @Optional durationDays: Int?) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check MANAGE_RELATIONS permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage("§cYou don't have permission to manage guild relations.")
            player.sendMessage("§7You need the MANAGE_RELATIONS permission.")
            return
        }

        // Resolve target guild (by name or by player name)
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage("§cNo guild or player named '$guildName' found.")
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage("§cYou cannot request a truce with your own guild.")
            return
        }

        // Get relation service
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()

        // Check current relation
        val currentRelation = relationService.getRelationType(guild.id, targetGuild.id)
        if (currentRelation != net.lumalyte.lg.domain.entities.RelationType.ENEMY) {
            player.sendMessage("§cYou can only request a truce with enemy guilds!")
            player.sendMessage("§7Current relation with ${targetGuild.name}: ${currentRelation.name.lowercase()}")
            return
        }

        // Validate duration (1-90 days, default 14)
        val duration = durationDays ?: 14
        if (duration < 1 || duration > 90) {
            player.sendMessage("§cTruce duration must be between 1 and 90 days.")
            return
        }

        // Request truce
        val relation = relationService.requestTruce(guild.id, targetGuild.id, playerId, java.time.Duration.ofDays(duration.toLong()))
        if (relation != null) {
            player.sendMessage("§e✓ Truce request sent to ${targetGuild.name} for $duration days!")
            player.sendMessage("§7They must accept your request for the truce to become active.")
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, "§e${guild.name} §7has requested a §e$duration-day truce§7 with your guild! Use §6/guild menu §7→ Relations to respond.")
        } else {
            player.sendMessage("§c✗ Failed to send truce request.")
            player.sendMessage("§7There may already be a pending request.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    @Subcommand("neutral")
    @CommandPermission("lumaguilds.guild.neutral")
    @CommandCompletion("@guilds")
    fun onNeutral(player: Player, guildName: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage("§cYou are not in a guild.")
            return
        }

        val guild = guilds.first()

        // Check MANAGE_RELATIONS permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage("§cYou don't have permission to manage guild relations.")
            player.sendMessage("§7You need the MANAGE_RELATIONS permission.")
            return
        }

        // Resolve target guild (by name or by player name)
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage("§cNo guild or player named '$guildName' found.")
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage("§cYou cannot request peace with your own guild.")
            return
        }

        // Get relation service
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()

        // Check current relation
        val currentRelation = relationService.getRelationType(guild.id, targetGuild.id)
        if (currentRelation != net.lumalyte.lg.domain.entities.RelationType.ENEMY) {
            player.sendMessage("§cYou can only request peace with enemy guilds!")
            player.sendMessage("§7Current relation with ${targetGuild.name}: ${currentRelation.name.lowercase()}")
            return
        }

        // Request unenemy (peace)
        val relation = relationService.requestUnenemy(guild.id, targetGuild.id, playerId)
        if (relation != null) {
            player.sendMessage("§f✓ Peace request sent to ${targetGuild.name}!")
            player.sendMessage("§7If accepted, hostilities will end permanently.")
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, "§f${guild.name} §7has requested to end hostilities with your guild! Use §6/guild menu §7→ Relations to respond.")
        } else {
            player.sendMessage("§c✗ Failed to send peace request.")
            player.sendMessage("§7There may already be a pending request.")
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

}