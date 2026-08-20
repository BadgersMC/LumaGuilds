package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import net.badgersmc.nexus.i18n.LangService
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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.interaction.help.HelpTopics
import net.lumalyte.lg.interaction.help.HelpTopicsRenderer
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.*
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.GuildHomeSafety
import net.lumalyte.lg.utils.GuildNameFilter
import net.lumalyte.lg.utils.GuildResolver
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

    private val lang: LangService by inject()
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
    private val bannermanListeners: net.lumalyte.lg.infrastructure.bukkit.bannerman.BannermanListeners by inject()
    private val strikeService: net.lumalyte.lg.application.services.StrikeService by inject()
    private val bankService: net.lumalyte.lg.application.services.BankService by inject()

    private val lastHomeTeleport = mutableMapOf<java.util.UUID, Long>()

    private fun notifyGuildMembers(guildId: java.util.UUID, message: Component) {
        val members = memberService.getGuildMembers(guildId)
        members.forEach { member ->
            val onlinePlayer = Bukkit.getPlayer(member.playerId)
            if (onlinePlayer != null && onlinePlayer.isOnline) {
                onlinePlayer.sendMessage(message)
            }
        }
    }

    internal fun checkHomeSafety(player: Player, target: Location, confirmCommand: String): Boolean {
        val result = GuildHomeSafety.checkAndRemember(player, target)
        if (result.safe) return true

        val reason = when (requireNotNull(result.issue)) {
            GuildHomeSafety.Issue.INVALID_WORLD -> lang.raw("command.guild.home.safety.reason.invalid_world")
            GuildHomeSafety.Issue.HEIGHT_OUT_OF_RANGE -> lang.raw("command.guild.home.safety.reason.height_out_of_range")
            GuildHomeSafety.Issue.DAMAGING_BLOCK_AT_FEET -> lang.raw("command.guild.home.safety.reason.damaging_block_at_feet")
            GuildHomeSafety.Issue.DAMAGING_BLOCK_BELOW -> lang.raw("command.guild.home.safety.reason.damaging_block_below")
        }
        player.sendMessage(
            lang.msg(
                "command.guild.home.safety.warning",
                "reason" to reason,
            ),
        )
        player.sendMessage(
            lang.msg(
                "command.guild.home.safety.confirm",
                "confirm_command" to confirmCommand,
            ),
        )
        return false
    }

    @Subcommand("create")
    @CommandPermission("lumaguilds.guild.create")
    fun onCreate(player: Player, name: String, @Optional banner: String?) {
        val playerId = player.uniqueId

        // Check if player is already in a guild
        val existingGuilds = guildService.getPlayerGuilds(playerId)
        if (existingGuilds.isNotEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.create.you_are_already_in_a_guild", "first" to existingGuilds.first().name))
            return
        }

        // Pre-validate guild name with helpful error messages

        // Check for MiniMessage/HTML-like formatting tags
        if (name.contains("<") && name.contains(">")) {
            player.sendMessage(lang.msg("command.migrated.guild.create.invalid_guild_name"))
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_names_cannot_contain_formatting_tags_like"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.create.tip_use_guild_tag_to_set_a"))
            player.sendMessage(lang.msg("command.migrated.guild.create.example_guild_tag_gradient_ff0000_00ff00_myguild"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_name_plain_text_only"))
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_tag_fancy_formatting_with_colors"))
            return
        }

        // Check for blank name
        if (name.isBlank()) {
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_name_cannot_be_blank"))
            return
        }

        // Check for length
        if (name.length > 32) {
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_name_is_too_long"))
            player.sendMessage(lang.msg("command.migrated.guild.create.maximum_length_32_characters"))
            player.sendMessage(lang.msg("command.migrated.guild.create.your_name_characters", "length" to name.length))
            return
        }

        // Check for invalid characters (only allow letters, numbers, spaces, and basic punctuation)
        if (!name.matches(Regex("^[a-zA-Z0-9 '&-]+$"))) {
            player.sendMessage(lang.msg("command.migrated.guild.create.invalid_guild_name"))
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_names_can_only_contain"))
            player.sendMessage(lang.msg("command.migrated.guild.create.letters_a_z_a_z"))
            player.sendMessage(lang.msg("command.migrated.guild.create.numbers_0_9"))
            player.sendMessage(lang.msg("command.migrated.guild.create.spaces"))
            player.sendMessage(lang.msg("command.migrated.guild.create.basic_punctuation"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.create.tip_use_guild_tag_to_add_colors"))
            return
        }

        // Check name filter (profanity/inappropriate content)
        val nameFilterConfig = configService.loadConfig().guild.nameFilter
        GuildNameFilter.checkName(name, nameFilterConfig)?.let { reason ->
            player.sendMessage(lang.msg("command.migrated.guild.create.blank_line", "reason" to reason))
            return
        }

        val guild = guildService.createGuild(name, playerId, banner)
        if (guild != null) {
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_created_successfully", "name" to name))
            player.sendMessage(lang.msg("command.migrated.guild.create.you_are_now_the_owner_of_the"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.create.customize_your_guild"))
            player.sendMessage(lang.msg("command.migrated.guild.create.set_fancy_tag_guild_tag"))
            player.sendMessage(lang.msg("command.migrated.guild.create.open_menu_guild_menu"))

            // Broadcast guild creation to all online players
            val creationMessage = lang.legacy("command.migrated.guild.create.a_new_guild_has_been_founded_by", "name" to name, "player" to player.name)
            net.lumalyte.lg.utils.ChatUtils.broadcastMessage(creationMessage, player)

            // Log the guild creation
            player.server.logger.info("Guild '${name}' created by ${player.name} (${player.uniqueId})")
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.create.failed_to_create_guild"))
            player.sendMessage(lang.msg("command.migrated.guild.create.the_name_is_already_taken_by_another", "name" to name))
            player.sendMessage(lang.msg("command.migrated.guild.create.please_choose_a_different_name"))
        }
    }
    
    @Subcommand("rename")
    @CommandPermission("lumaguilds.guild.rename")
    fun onRename(player: Player, newName: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if player has permission to rename guild
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_don_t_have_permission_to_rename"))
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_need_the_manage_guild_settings_permission"))
            return
        }

        // Pre-validate guild name with helpful error messages

        // Check for MiniMessage/HTML-like formatting tags
        if (newName.contains("<") && newName.contains(">")) {
            player.sendMessage(lang.msg("command.migrated.guild.create.invalid_guild_name"))
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_names_cannot_contain_formatting_tags_like"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.create.tip_use_guild_tag_to_set_a"))
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_name_plain_text_only"))
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_tag_fancy_formatting_with_colors"))
            return
        }

        // Check for blank name
        if (newName.isBlank()) {
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_name_cannot_be_blank"))
            return
        }

        // Check for length
        if (newName.length > 32) {
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_name_is_too_long"))
            player.sendMessage(lang.msg("command.migrated.guild.create.maximum_length_32_characters"))
            player.sendMessage(lang.msg("command.migrated.guild.create.your_name_characters", "length" to newName.length))
            return
        }

        // Check for invalid characters
        if (!newName.matches(Regex("^[a-zA-Z0-9 '&-]+$"))) {
            player.sendMessage(lang.msg("command.migrated.guild.create.invalid_guild_name"))
            player.sendMessage(lang.msg("command.migrated.guild.create.guild_names_can_only_contain"))
            player.sendMessage(lang.msg("command.migrated.guild.create.letters_a_z_a_z"))
            player.sendMessage(lang.msg("command.migrated.guild.create.numbers_0_9"))
            player.sendMessage(lang.msg("command.migrated.guild.create.spaces"))
            player.sendMessage(lang.msg("command.migrated.guild.create.basic_punctuation"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.create.tip_use_guild_tag_to_add_colors"))
            return
        }

        // Check name filter (profanity/inappropriate content)
        val nameFilterConfig = configService.loadConfig().guild.nameFilter
        GuildNameFilter.checkName(newName, nameFilterConfig)?.let { reason ->
            player.sendMessage(lang.msg("command.migrated.guild.create.blank_line", "reason" to reason))
            return
        }

        val success = guildService.renameGuild(guild.id, newName, playerId)

        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.guild_renamed_to_successfully", "new_name" to newName))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.rename.failed_to_rename_guild"))
            player.sendMessage(lang.msg("command.migrated.guild.rename.the_name_may_already_be_taken_by", "new_name" to newName))
            player.sendMessage(lang.msg("command.migrated.guild.create.please_choose_a_different_name"))
        }
    }
    
    @Subcommand("sethome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onSetHome(player: Player, @Optional homeName: String?, @Optional confirm: String?) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()
        val location = player.location

        // A one-argument confirmation targets the main home; two arguments preserve a named home.
        val firstArgument = homeName?.lowercase()
        val isMainHomeConfirmation = firstArgument == "confirm" || firstArgument == "unsafe"
        val adjustedHomeName = if (isMainHomeConfirmation) null else homeName
        val adjustedConfirm = if (isMainHomeConfirmation) firstArgument else confirm

        val targetHomeName = adjustedHomeName ?: "main"

        // Check if this is a confirmation for an unsafe location
        if (adjustedConfirm?.lowercase() == "unsafe") {
            val pendingLocation = GuildHomeSafety.consumePending(player)
            if (pendingLocation != null) {
                setGuildHomeCommand(player, guild, pendingLocation, targetHomeName)
                return
            } else {
                player.sendMessage(lang.msg("command.migrated.guild.sethome.no_pending_unsafe_location_to_confirm_or"))
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
                        player.sendMessage(lang.msg("command.migrated.guild.sethome.you_can_only_set_guild_home_in"))
                        player.sendMessage(lang.msg("command.migrated.guild.sethome.use_the_bell_menu_to_convert_this"))
                        return
                    }

                    // Check if the claim belongs to the player's guild
                    if (claim.teamId != guild.id) {
                        player.sendMessage(lang.msg("command.migrated.guild.sethome.you_can_only_set_guild_home_in_2"))
                        player.sendMessage(lang.msg("command.migrated.guild.sethome.this_claim_belongs_to_a_different_guild"))
                        return
                    }
                }
                is net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult.NoClaimFound -> {
                    player.sendMessage(lang.msg("command.migrated.guild.sethome.you_must_be_standing_in_a_guild"))
                    player.sendMessage(lang.msg("command.migrated.guild.sethome.place_a_bell_and_convert_it_to"))
                    return
                }
                is net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult.StorageError -> {
                    player.sendMessage(lang.msg("command.migrated.guild.sethome.an_error_occurred_while_checking_your_location"))
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
            player.sendMessage(lang.msg("command.migrated.guild.sethome.your_guild_already_has_a_set", "home_label" to homeLabel))
            player.sendMessage(lang.msg("command.migrated.guild.sethome.use_to_replace_it", "confirm_command" to confirmCommand))
            player.sendMessage(lang.msg("command.migrated.guild.sethome.or_use_the_guild_menu_for_a"))
            return
        }

        // Check safety and handle confirmation system
        if (config.guild.homeTeleportSafetyCheck) {
            val unsafeConfirmCommand = if (adjustedHomeName == null) {
                "/guild sethome unsafe"
            } else {
                "/guild sethome $adjustedHomeName unsafe"
            }
            if (!checkHomeSafety(player, location, unsafeConfirmCommand)) {
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
                player.sendMessage(lang.msg("command.migrated.guild.home.no_pending_unsafe_teleport_to_confirm_or"))
                return
            }
        }

        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()
        val targetHomeName = homeName ?: "main"
        val home = guildService.getHome(guild.id, targetHomeName)

        if (home != null) {
            if (!guildService.canUseHome(playerId, guild.id, targetHomeName)) {
                player.sendMessage(lang.msg("command.migrated.guild.home.you_don_t_have_permission_to_use", "target_home_name" to targetHomeName))
                player.sendMessage(lang.msg("command.migrated.guild.home.ask_a_guild_manager_to_grant_your"))
                return
            }
            // Check if player already has an active teleport
            if (teleportationService.hasActiveTeleport(playerId)) {
                player.sendMessage(lang.msg("command.migrated.guild.home.you_already_have_a_teleport_in_progress"))
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
                    player.sendMessage(lang.msg("command.migrated.guild.home.please_wait_s_before_teleporting_again", "remaining_seconds" to remainingSeconds))
                    return
                }
            }

            // Get target location
            val world = player.server.getWorld(home.worldId)
            if (world == null) {
                player.sendMessage(lang.msg("command.migrated.guild.home.guild_home_world_is_not_available"))
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
                if (!checkHomeSafety(player, targetLocation, "/guild home confirm")) {
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
                player.sendMessage(lang.msg("command.migrated.guild.home.home_has_not_been_set", "target_home_name" to targetHomeName))
                player.sendMessage(lang.msg("command.migrated.guild.home.available_homes", "join_to_string" to allHomes.homeNames.joinToString(", ")))
                player.sendMessage(lang.msg("command.migrated.guild.home.use_guild_home_name_to_teleport_to"))
            } else {
                player.sendMessage(lang.msg("command.migrated.guild.home.no_guild_homes_have_been_set"))
                player.sendMessage(lang.msg("command.migrated.guild.home.use_guild_sethome_to_set_your_first"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        player.sendMessage(lang.msg("command.migrated.guild.homes.guild_homes"))
        if (allHomes.hasHomes()) {
            player.sendMessage(lang.msg("command.migrated.guild.homes.your_guild_has_home_slots", "size" to allHomes.size, "available_slots" to availableSlots))
            allHomes.homes.forEach { entry ->
                val name = entry.key
                val home = entry.value
                val marker = if (name == "main") lang.legacy("command.migrated.guild.homes.main") else ""
                val worldName = Bukkit.getWorld(home.worldId)?.name ?: "Unknown"
                player.sendMessage(lang.msg("command.migrated.guild.homes.blank_line", "name" to name, "marker" to marker, "world_name" to worldName))
            }
            player.sendMessage(lang.msg("command.migrated.guild.homes.use_guild_home_name_to_teleport_to"))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.homes.no_homes_have_been_set_yet"))
        }

        if (allHomes.size < availableSlots) {
            player.sendMessage(lang.msg("command.migrated.guild.homes.available_slots", "size" to availableSlots - allHomes.size))
            player.sendMessage(lang.msg("command.migrated.guild.homes.use_guild_sethome_name_to_set_additional"))
        }
        player.sendMessage(lang.msg("command.migrated.guild.homes.blank_line_2"))
    }

    @Subcommand("removehome")
    @CommandPermission("lumaguilds.guild.sethome")
    @CommandCompletion("@guildhomes")
    fun onRemoveHome(player: Player, homeName: String) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()
        if (guildService.getHome(guild.id, homeName) == null) {
            player.sendMessage(lang.msg("command.migrated.guild.removehome.home_does_not_exist", "home_name" to homeName))
            return
        }

        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(net.lumalyte.lg.interaction.menus.common.ConfirmationMenu(
            menuNavigator, player, lang.legacy("command.migrated.guild.removehome.remove_home", "home_name" to homeName)
        ) {
            val success = guildService.removeHome(guild.id, homeName, playerId)
            if (success) {
                player.sendMessage(lang.msg("command.migrated.guild.removehome.home_removed", "home_name" to homeName))
            } else {
                player.sendMessage(lang.msg("command.migrated.guild.removehome.failed_to_remove_home", "home_name" to homeName))
            }
        })
    }

    @Subcommand("setallyhome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onSetAllyHome(player: Player) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()
        val location = player.location

        val home = GuildHome(
            worldId = location.world.uid,
            position = location.toPosition3D()
        )

        val success = guildService.setAllyHome(guild.id, home, playerId)
        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.setallyhome.ally_home_set_to_your_current_location"))
            player.sendMessage(lang.msg("command.migrated.guild.setallyhome.allied_guilds_with_the_ally_home_perk"))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.setallyhome.failed_to_set_ally_home_you_may"))
        }
    }

    @Subcommand("removeallyhome")
    @CommandPermission("lumaguilds.guild.sethome")
    fun onRemoveAllyHome(player: Player) {
        val playerId = player.uniqueId
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()
        val success = guildService.removeAllyHome(guild.id, playerId)
        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.removeallyhome.ally_home_removed"))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.removeallyhome.failed_to_remove_ally_home_it_may"))
        }
    }

    @Subcommand("allyhome")
    @CommandPermission("lumaguilds.guild.allyhome")
    @CommandCompletion("@allyguilds")
    @Syntax("<guildName> [confirm]")
    fun onAllyHome(player: Player, guildName: String, @Optional confirm: String?) {
        if (guildName.lowercase() == "confirm" || confirm?.lowercase() == "confirm") {
            handleAllyHomeConfirm(player)
            return
        }

        val ownGuild = guildService.getPlayerGuilds(player.uniqueId).firstOrNull()
        if (ownGuild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val targetGuild = resolveAllyTarget(player, guildName, ownGuild) ?: return
        val targetLocation = resolveAllyHomeLocation(player, ownGuild, targetGuild) ?: return
        if (!checkAllyHomeCooldown(player, ownGuild.id)) return

        val config = configService.loadConfig()
        if (config.guild.homeTeleportSafetyCheck &&
            !checkHomeSafety(player, targetLocation, "/guild allyhome ${targetGuild.id} confirm")) {
            return
        }

        teleportationService.startTeleport(player, targetLocation) {
            lastHomeTeleport[player.uniqueId] = System.currentTimeMillis()
            player.sendMessage(lang.msg("command.migrated.guild.allyhome.teleported_to_s_ally_home", "guild" to targetGuild.name))
        }
    }

    private fun handleAllyHomeConfirm(player: Player) {
        val pendingLocation = GuildHomeSafety.consumePending(player)
        if (pendingLocation == null) {
            player.sendMessage(lang.msg("command.migrated.guild.home.no_pending_unsafe_teleport_to_confirm_or"))
            return
        }
        teleportationService.startTeleport(player, pendingLocation) {
            lastHomeTeleport[player.uniqueId] = System.currentTimeMillis()
        }
    }

    private fun resolveAllyTarget(
        player: Player,
        guildName: String,
        ownGuild: net.lumalyte.lg.domain.entities.Guild
    ): net.lumalyte.lg.domain.entities.Guild? {
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.resolveallytarget.no_guild_named_found", "guild" to guildName))
            return null
        }
        // Own guild's ally-home is a valid target: members with USE_ALLY_HOMES (or the owner)
        // may teleport to it. The ally-relation check below only applies to other guilds.
        if (targetGuild.id == ownGuild.id) {
            return targetGuild
        }
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()
        if (relationService.getRelationType(ownGuild.id, targetGuild.id)
            != net.lumalyte.lg.domain.entities.RelationType.ALLY) {
            player.sendMessage(lang.msg("command.migrated.guild.resolveallytarget.is_not_an_ally_of_your_guild", "guild" to targetGuild.name))
            return null
        }
        return targetGuild
    }

    private fun resolveAllyHomeLocation(
        player: Player,
        ownGuild: net.lumalyte.lg.domain.entities.Guild,
        targetGuild: net.lumalyte.lg.domain.entities.Guild
    ): Location? {
        val allyHome = guildService.getAllyHome(targetGuild.id)
        if (allyHome == null) {
            player.sendMessage(lang.msg("command.migrated.guild.resolveallyhomelocation.has_no_ally_home_set", "guild" to targetGuild.name))
            return null
        }
        val isOwnGuild = targetGuild.id == ownGuild.id
        val allowed = if (isOwnGuild) {
            guildService.canUseOwnAllyHome(player.uniqueId, ownGuild.id)
        } else {
            guildService.canUseAllyHome(player.uniqueId, ownGuild.id, targetGuild.id)
        }
        if (!allowed) {
            if (isOwnGuild) {
                player.sendMessage(lang.msg("command.migrated.guild.resolveallyhomelocation.you_don_t_have_permission_to_use"))
                player.sendMessage(lang.msg("command.migrated.guild.resolveallyhomelocation.your_rank_needs_the_use_ally_homes"))
            } else {
                player.sendMessage(lang.msg("command.migrated.guild.resolveallyhomelocation.you_don_t_have_permission_to_use_2", "guild" to targetGuild.name))
                player.sendMessage(lang.msg("command.migrated.guild.resolveallyhomelocation.your_rank_may_lack_use_ally_homes"))
            }
            return null
        }
        if (teleportationService.hasActiveTeleport(player.uniqueId)) {
            player.sendMessage(lang.msg("command.migrated.guild.home.you_already_have_a_teleport_in_progress"))
            return null
        }
        val world = player.server.getWorld(allyHome.worldId)
        if (world == null) {
            player.sendMessage(lang.msg("command.migrated.guild.resolveallyhomelocation.ally_guild_home_world_is_not_available"))
            return null
        }
        return Location(
            world,
            allyHome.position.x.toDouble() + 0.5,
            allyHome.position.y.toDouble(),
            allyHome.position.z.toDouble() + 0.5,
            player.location.yaw,
            player.location.pitch
        )
    }

    private fun checkAllyHomeCooldown(player: Player, ownGuildId: java.util.UUID): Boolean {
        val config = configService.loadConfig()
        val baseCooldownSeconds = config.guild.homeTeleportCooldownSeconds
        val cooldownMultiplier = progressionService.getHomeCooldownMultiplier(ownGuildId)
        val cooldownSeconds = (baseCooldownSeconds * cooldownMultiplier).toLong()
        val lastTeleport = lastHomeTeleport[player.uniqueId] ?: return true
        val elapsedSeconds = (System.currentTimeMillis() - lastTeleport) / 1000
        if (elapsedSeconds < cooldownSeconds) {
            player.sendMessage(lang.msg("command.migrated.guild.checkallyhomecooldown.please_wait_s_before_teleporting_again", "elapsed_seconds" to cooldownSeconds - elapsedSeconds))
            return false
        }
        return true
    }

    @Subcommand("ranks")
    @CommandPermission("lumaguilds.guild.ranks")
    fun onRanks(player: Player) {
        val playerId = player.uniqueId
        
        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }
        
        val guild = guilds.first()
        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        
        player.sendMessage(lang.msg("command.migrated.guild.ranks.guild_ranks"))
        player.sendMessage(lang.msg("command.migrated.guild.ranks.guild", "guild" to guild.name))
        player.sendMessage(lang.msg("command.common.blank_line"))
        
        for (rank in ranks) {
            val memberCount = memberService.getMembersByRank(guild.id, rank.id).size
            val permissions = if (rank.permissions.isNotEmpty()) {
                rank.permissions.joinToString(", ") { it.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() } }
            } else "None"
            
            player.sendMessage(lang.msg("command.migrated.guild.ranks.priority", "rank" to rank.name, "priority" to rank.priority))
            player.sendMessage(lang.msg("command.migrated.guild.ranks.members", "member_count" to memberCount))
            player.sendMessage(lang.msg("command.migrated.guild.ranks.permissions", "permissions" to permissions))
            player.sendMessage(lang.msg("command.common.blank_line"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if player has permission to manage emoji
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        val hasEmojiPermission = playerRank?.permissions?.any { permission ->
            permission in setOf(
                RankPermission.MANAGE_EMOJI,
                RankPermission.MANAGE_BANNER,
                RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_CLAIMS
            )
        } ?: false

        val highestRank = rankService.getHighestRank(guild.id)
        val isOwner = playerRank?.id == highestRank?.id

        if (!hasEmojiPermission && !isOwner) {
            player.sendMessage(lang.msg("command.migrated.guild.emoji.you_don_t_have_permission_to_change"))
            return
        }

        // If no emoji parameter provided, open the menu
        if (emoji == null) {
            val menuNavigator = MenuNavigator(player)
            menuNavigator.openMenu(menuFactory.createGuildEmojiMenu(menuNavigator, player, guild))
            return
        }

        // Clear the emoji if the player passes "clear", "none", or "remove"
        val clearKeywords = setOf("clear", "none", "remove")
        if (emoji.lowercase() in clearKeywords) {
            val success = guildService.setEmoji(guild.id, null, playerId)
            if (success) {
                player.sendMessage(lang.msg("command.migrated.guild.emoji.guild_emoji_cleared_successfully"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
            } else {
                player.sendMessage(lang.msg("command.migrated.guild.emoji.failed_to_clear_emoji_please_try_again"))
            }
            return
        }

        // Direct emoji setting via command parameter
        val nexoEmojiService: net.lumalyte.lg.infrastructure.services.NexoEmojiService by inject()

        // Validate emoji format
        if (!nexoEmojiService.isValidEmojiFormat(emoji)) {
            player.sendMessage(lang.msg("command.migrated.guild.emoji.invalid_emoji_format"))
            player.sendMessage(lang.msg("command.migrated.guild.emoji.format_must_be_emoji_name_e_g"))
            return
        }

        // Check if emoji exists in Nexo
        if (!nexoEmojiService.doesEmojiExist(emoji)) {
            player.sendMessage(lang.msg("command.migrated.guild.emoji.emoji_not_found_in_nexo_registry"))
            player.sendMessage(lang.msg("command.migrated.guild.emoji.make_sure_the_emoji_is_configured_in"))
            return
        }

        // Check if player has permission for this specific emoji
        if (!nexoEmojiService.hasEmojiPermission(player, emoji)) {
            val permission = nexoEmojiService.getEmojiPermission(emoji) ?: "unknown"
            player.sendMessage(lang.msg("command.migrated.guild.emoji.you_don_t_have_permission_to_use"))
            player.sendMessage(lang.msg("command.migrated.guild.emoji.required_permission", "permission" to permission))
            return
        }

        // Set the emoji
        val success = guildService.setEmoji(guild.id, emoji, playerId)
        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.emoji.guild_emoji_updated_successfully"))
            player.sendMessage(lang.msg("command.migrated.guild.emoji.new_emoji", "emoji" to emoji))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.emoji.failed_to_save_emoji_please_try_again"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if mode switching is enabled in config
        val mainConfig = configService.loadConfig()
        if (!mainConfig.guild.modeSwitchingEnabled) {
            player.sendMessage(lang.msg("command.migrated.guild.mode.guild_mode_switching_is_disabled_by_server"))
            player.sendMessage(lang.msg("command.migrated.guild.mode.guilds_cannot_change_between_peaceful_and_hostile"))
            return
        }

        val guildMode = try {
            GuildMode.valueOf(mode.uppercase())
        } catch (e: IllegalArgumentException) {
            player.sendMessage(lang.msg("command.migrated.guild.mode.invalid_mode_use_peaceful_or_hostile"))
            return
        }

        // Check if already in that mode
        if (guild.mode == guildMode) {
            player.sendMessage(lang.msg("command.migrated.guild.mode.guild_is_already_in_mode", "uppercase" to guildMode.name.lowercase().replaceFirstChar { it.uppercase() }))
            return
        }

        // Validate cooldown based on which mode we're switching to
        if (guildMode == GuildMode.PEACEFUL) {
            // Switching TO peaceful - check cooldown
            val canSwitch = canSwitchToPeaceful(guild, mainConfig.guild.modeSwitchCooldownDays)
            val hasActiveWar = warService.getWarsForGuild(guild.id).any { it.isActive }

            if (hasActiveWar) {
                player.sendMessage(lang.msg("command.migrated.guild.mode.cannot_switch_to_peaceful_mode_during_active"))
                return
            }

            if (!canSwitch) {
                val cooldownMsg = getCooldownMessage(guild, mainConfig.guild.modeSwitchCooldownDays)
                player.sendMessage(lang.msg("command.migrated.guild.mode.blank_line", "cooldown_msg" to cooldownMsg))
                return
            }
        } else if (guildMode == GuildMode.HOSTILE) {
            // Switching TO hostile - check minimum peaceful days
            val canSwitch = canSwitchToHostile(guild, mainConfig.guild.hostileModeMinimumDays)

            if (!canSwitch) {
                val lockMsg = getHostileLockMessage(guild, mainConfig.guild.hostileModeMinimumDays)
                player.sendMessage(lang.msg("command.migrated.guild.mode.blank_line_2", "lock_msg" to lockMsg))
                return
            }
        }

        val success = guildService.setMode(guild.id, guildMode, playerId)

        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.mode.guild_mode_changed_to", "uppercase" to guildMode.name.lowercase().replaceFirstChar { it.uppercase() }))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.mode.failed_to_change_guild_mode_you_may"))
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
        val modeChanged = guild.modeChangedAt ?: return lang.legacy("command.migrated.guild.mode.no_previous_changes")

        val cooldownEnd = modeChanged.plus(java.time.Duration.ofDays(cooldownDays.toLong()))
        val remaining = java.time.Duration.between(java.time.Instant.now(), cooldownEnd)

        if (remaining.isNegative) return lang.legacy("command.migrated.guild.mode.cooldown_expired")

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return lang.legacy("command.migrated.guild.mode.switch_to_peaceful", "days" to days, "hours" to hours)
    }

    private fun getHostileLockMessage(guild: Guild, minimumDays: Int): String {
        val modeChanged = guild.modeChangedAt ?: return lang.legacy("command.migrated.guild.mode.no_previous_changes")

        val lockEnd = modeChanged.plus(java.time.Duration.ofDays(minimumDays.toLong()))
        val remaining = java.time.Duration.between(java.time.Instant.now(), lockEnd)

        if (remaining.isNegative) return lang.legacy("command.migrated.guild.mode.lock_expired")

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24

        return lang.legacy("command.migrated.guild.mode.switch_to_hostile", "days" to days, "hours" to hours)
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
                player.sendMessage(lang.msg("command.migrated.guild.history.player_has_never_played_on_this_server", "target_player_name" to targetPlayerName))
                return
            }
            targetId = offlineTarget.uniqueId
            displayName = offlineTarget.name ?: targetPlayerName
        }

        val history = historyRepository.getByPlayer(targetId)

        if (history.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.history.has_no_guild_history", "display_name" to displayName))
            return
        }

        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(java.time.ZoneId.systemDefault())

        player.sendMessage(lang.msg("command.migrated.guild.history.guild_history", "display_name" to displayName))
        player.sendMessage(lang.msg("command.migrated.guild.history.total_guilds_joined", "size" to history.size))
        player.sendMessage(lang.msg("command.common.blank_line"))

        history.forEachIndexed { index, entry ->
            val guildName = guildService.getGuild(entry.guildId)?.name
            val guildDisplay = if (guildName != null) lang.legacy("command.migrated.guild.history.blank_line", "guild" to guildName) else lang.legacy("command.migrated.guild.history.unknown")
            val joinDate = formatter.format(entry.joinedAt)

            val suffix = when {
                entry.isOpen -> lang.legacy("command.migrated.guild.history.current")
                entry.departureReason == DepartureReason.LEFT -> lang.legacy("command.migrated.guild.history.left")
                entry.departureReason == DepartureReason.KICKED -> lang.legacy("command.migrated.guild.history.kicked")
                entry.departureReason == DepartureReason.DISBANDED -> lang.legacy("command.migrated.guild.history.guild_disbanded")
                else -> ""
            }

            player.sendMessage(lang.msg("command.migrated.guild.history.joined", "index" to index + 1, "guild_display" to guildDisplay, "join_date" to joinDate, "suffix" to suffix))
        }

        player.sendMessage(lang.msg("command.migrated.guild.history.blank_line_2", "length" to "═".repeat(20 + displayName.length)))
    }

    @Subcommand("chat")
    @CommandPermission("lumaguilds.guild.chat")
    fun onGuildChat(player: Player) {
        val playerId = player.uniqueId

        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.guildchat.you_are_not_in_a_guild"))
            return
        }

        val nowEnabled = guildChatListener.toggleGuildChat(player)
        if (nowEnabled) {
            player.sendMessage(lang.msg("command.migrated.guild.guildchat.guild_chat_enabled_your_messages_go_only"))
            player.sendMessage(lang.msg("command.migrated.guild.guildchat.run_g_chat_again_to_return_to"))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.guildchat.guild_chat_disabled_your_messages_go_to"))
        }
    }

    @Subcommand("allychat")
    @CommandPermission("lumaguilds.guild.chat")
    fun onAllyChat(player: Player) {
        val playerId = player.uniqueId

        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.guildchat.you_are_not_in_a_guild"))
            return
        }

        val nowEnabled = guildChatListener.toggleAllyChat(player)
        if (nowEnabled) {
            player.sendMessage(lang.msg("command.migrated.guild.allychat.ally_chat_enabled_your_messages_go_to"))
            player.sendMessage(lang.msg("command.migrated.guild.allychat.run_g_allychat_again_to_return_to"))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.allychat.ally_chat_disabled_your_messages_go_to"))
        }
    }

    @Subcommand("modchat")
    @CommandPermission("lumaguilds.guild.chat")
    fun onModChat(player: Player) {
        val result = guildChatListener.toggleModChat(player)
        when (result) {
            null -> {} // error already sent by resolveModChatChannel
            true -> player.sendMessage(
                lang.msg("command.migrated.guild.modchat.mod_chat_enabled_run_g_modchat_again"),
            )
            false -> player.sendMessage(
                lang.msg("command.migrated.guild.modchat.mod_chat_disabled_your_messages_go_to"),
            )
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
                player.sendMessage(lang.msg("command.migrated.guild.info.no_guild_or_player_named_found", "target_guild" to targetGuild))
                return
            }

            // Open the target guild's info menu (no permission restrictions)
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, targetGuildObj))
        } else {
            // Show player's own guild info
            val guilds = guildService.getPlayerGuilds(player.uniqueId)
            if (guilds.isEmpty()) {
                player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
                return
            }

            val guild = guilds.first()
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }
    }

    /**
     * Public Guild Strikes view — lists every guild's strikes and, with a guild
     * argument, the individual punishments behind them.
     *
     * No permission required: the strike ledger is public by design.
     */
    @Subcommand("strikes")
    @CommandCompletion("@guilds")
    fun onStrikes(player: Player, @Optional target: String?) {
        val strikesConfig = configService.loadConfig().strikes
        if (!strikesConfig.enabled) {
            player.sendMessage(lang.msg("command.migrated.guild.strikes.guild_strikes_are_currently_disabled"))
            return
        }

        val threshold = strikesConfig.threshold
        val guildServiceRef = guildService

        if (target != null) {
            val guild = GuildResolver.resolve(target, guildServiceRef)
            if (guild == null) {
                player.sendMessage(lang.msg("command.migrated.guild.strikes.no_guild_or_player_named_found", "target" to target))
                return
            }
            showGuildStrikeDetails(player, guild, threshold)
            return
        }

        // Global public view: every guild that has at least one strike.
        val counts = strikeService.getAllCounts()
        if (counts.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.strikes.lumaguilds_no_guild_has_any_strikes_yet"))
            return
        }

        val total = strikeService.countAll()
        player.sendMessage(lang.msg("command.migrated.guild.strikes.lumaguilds_guild_strikes_total", "total" to total))
        if (threshold > 0) {
            player.sendMessage(lang.msg("command.migrated.guild.strikes.guilds_at_active_strikes_are_up_for", "threshold" to threshold))
        }

        // Penalty eligibility is based on ACTIVE strikes only (lifted punishments
        // don't count toward the threshold) — same rule as /g strikes <guild>.
        val activeCounts = strikeService.getAllActiveCounts()
        val nameById = guildServiceRef.getAllGuilds().associateBy { it.id }
        var index = 0
        for ((guildId, count) in counts.entries.sortedByDescending { it.value }) {
            val guild = nameById[guildId]
            val label = guild?.let { GuildResolver.displayName(it) } ?: guildId.toString().take(8)
            val flag = if (threshold > 0 && (activeCounts[guildId] ?: 0) >= threshold) lang.legacy("command.migrated.guild.strikes.up_for_penalty") else ""
            player.sendMessage(lang.msg("command.migrated.guild.strikes.strike_s", "index" to ++index, "label" to label, "count" to count, "flag" to flag))
        }
        player.sendMessage(lang.msg("command.migrated.guild.strikes.run_g_strikes_guild_for_the_punishments"))
    }

    /**
     * Admin penalty GUI — opens the penalty menu for a guild. Requires
     * `lumaguilds.admin.strikes`.
     */
    @Subcommand("strikes punish")
    @CommandPermission("lumaguilds.admin.strikes")
    @CommandCompletion("@guilds")
    fun onStrikesPunish(player: Player, target: String) {
        val strikesConfig = configService.loadConfig().strikes
        if (!strikesConfig.enabled) {
            player.sendMessage(lang.msg("command.migrated.guild.strikes.guild_strikes_are_currently_disabled"))
            return
        }

        val guild = GuildResolver.resolve(target, guildService)
        if (guild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.strikes.no_guild_or_player_named_found", "target" to target))
            return
        }

        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(
            net.lumalyte.lg.interaction.menus.guild.GuildStrikePenaltyMenu(
                menuNavigator, player, guild, strikesConfig
            )
        )
    }

    private fun showGuildStrikeDetails(player: Player, guild: net.lumalyte.lg.domain.entities.Guild, threshold: Int) {
        val strikes = strikeService.getByGuild(guild.id)
        val name = GuildResolver.displayName(guild)

        if (strikes.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.showguildstrikedetails.lumaguilds_has_no_strikes", "name" to name))
            return
        }

        val activeCount = strikes.count { it.active }
        val status = if (threshold > 0 && activeCount >= threshold) {
            lang.legacy("command.migrated.guild.strikes.up_for_penalty")
        } else {
            ""
        }
        player.sendMessage(lang.msg("command.migrated.guild.showguildstrikedetails.lumaguilds_strike_s", "name" to name, "size" to strikes.size, "status" to status))
        if (threshold > 0) {
            player.sendMessage(lang.msg("command.migrated.guild.showguildstrikedetails.penalty_threshold_active_strikes", "threshold" to threshold))
        }

        // Cap the per-guild detail list — a guild with hundreds of punishments
        // would otherwise flood the player's chat.
        val maxShown = 15
        strikes.take(maxShown).forEach { strike ->
            val typeColor = when (strike.punishmentType.uppercase()) {
                "BAN" -> lang.legacy("command.migrated.guild.showguildstrikedetails.blank_line")
                "MUTE" -> lang.legacy("command.migrated.guild.showguildstrikedetails.blank_line_2")
                "KICK" -> lang.legacy("command.migrated.guild.showguildstrikedetails.blank_line_3")
                else -> lang.legacy("command.common.blank_line")
            }
            val lifted = if (strike.active) "" else lang.legacy("command.migrated.guild.showguildstrikedetails.lifted")
            val reason = strike.reason?.takeIf { it.isNotBlank() }?.let { lang.legacy("command.migrated.guild.showguildstrikedetails.blank_line_4", "take" to it.take(80)) } ?: ""
            val by = strike.executorName?.let { lang.legacy("command.migrated.guild.showguildstrikedetails.by", "it" to it) } ?: ""
            player.sendMessage(
                lang.msg(
                    "command.migrated.guild.showguildstrikedetails.row",
                    "type_color" to typeColor,
                    "uppercase" to strike.punishmentType.uppercase(),
                    "player" to (strike.playerName ?: strike.playerUuid.toString().take(8)),
                    "reason" to reason,
                    "by" to by,
                    "issued_at" to formatStrikeDate(strike.issuedAt),
                    "lifted" to lifted,
                ),
            )
            if (!strike.active) {
                row = row.append(lang.msg("command.migrated.guild.showguildstrikedetails.lifted"))
            }
            player.sendMessage(row)
        }
        val hidden = strikes.size - maxShown
        if (hidden > 0) {
            player.sendMessage(lang.msg("command.migrated.guild.showguildstrikedetails.and_more_showing_newest", "hidden" to hidden, "max_shown" to maxShown))
        }
    }

    private fun formatStrikeDate(instant: java.time.Instant): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return formatter.format(instant.atZone(java.time.ZoneId.systemDefault()))
    }
    
    @Subcommand("disband")
    @CommandPermission("lumaguilds.guild.disband")
    fun onDisband(player: Player) {
        val playerId = player.uniqueId
        
        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }
        
        val guild = guilds.first()
        
        // Check if player is the owner (has highest rank)
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        val highestRank = rankService.getHighestRank(guild.id)
        
        if (playerRank?.id != highestRank?.id) {
            player.sendMessage(lang.msg("command.migrated.guild.disband.only_the_guild_owner_can_disband_the"))
            return
        }
        
        val success = guildService.disbandGuild(guild.id, playerId)
        
        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.disband.guild_has_been_disbanded", "guild" to guild.name))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.disband.failed_to_disband_guild"))
        }
    }

    @Subcommand("menu")
    @CommandPermission("lumaguilds.guild.menu")
    fun onMenu(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
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
                player.sendMessage(lang.msg("command.migrated.guild.menu.you_don_t_have_permission_to_access"))
                player.sendMessage(lang.msg("command.migrated.guild.menu.only_guild_owners_and_members_with_management"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }
        player.server.logger.info("bugrock guild : ${guilds}")

        val guild = guilds.first()

        // Check if player has member management permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            player.sendMessage(lang.msg("command.migrated.guild.invite.you_don_t_have_permission_to_invite"))
            return
        }

        // Find target player - handle Floodgate prefix
        val targetPlayer = findPlayerByName(targetPlayerName)
        player.server.logger.info("target player : ${targetPlayer}")
        if (targetPlayer == null) {
            player.sendMessage(lang.msg("command.migrated.guild.invite.player_is_not_online", "target_player_name" to targetPlayerName))
            return
        }

        if (targetPlayer == player) {
            player.sendMessage(lang.msg("command.migrated.guild.invite.you_cannot_invite_yourself"))
            return
        }

        // Check if target is already in a guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            player.sendMessage(lang.msg("command.migrated.guild.invite.is_already_in_your_guild", "player" to targetPlayer.name))
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
            player.sendMessage(lang.msg("command.migrated.guild.join.you_are_already_in_a_guild"))
            player.sendMessage(lang.msg("command.migrated.guild.join.use_guild_leave_to_leave_your_current"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Resolve guild by name (exact / normalized). Player-name resolution is
        // intentionally NOT used here — joining via a player's name is ambiguous.
        val guild = net.lumalyte.lg.utils.GuildResolver.resolveGuildByName(guildName, guildService)
        if (guild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.join.guild_doesn_t_exist", "guild" to guildName))
            player.sendMessage(lang.msg("command.migrated.guild.join.check_guild_list_to_see_available_guilds"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Admin override bypasses invitation requirements
        if (adminOverrideService.hasOverride(playerId)) {
            player.sendMessage(lang.msg("command.migrated.guild.join.override_bypassing_invitation_check"))
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
            player.sendMessage(lang.msg("command.migrated.guild.join.you_don_t_have_an_invitation_to", "guild" to guildName))
            player.sendMessage(lang.msg("command.migrated.guild.join.this_guild_is_invite_only_ask_a"))
            player.sendMessage(lang.msg("command.migrated.guild.join.check_guild_invites_to_see_your_pending"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val (guildId, actualGuildName) = invite

        // Verify the guild still exists
        if (guild.id != guildId) {
            player.sendMessage(lang.msg("command.migrated.guild.join.that_guild_no_longer_exists"))
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
            player.sendMessage(lang.msg("command.migrated.guild.joinguilddirectly.guild_has_no_ranks_configured_please_contact"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        // Add the member
        val newMember = memberService.addMember(playerId, guildId, lowestRank.id)

        if (newMember != null) {
            // Remove the invitation (if they had one)
            net.lumalyte.lg.infrastructure.services.GuildInvitationManager.removeInvite(playerId, guildId)

            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.joinguilddirectly.joined_guild"))
            player.sendMessage(lang.msg("command.migrated.guild.joinguilddirectly.you_are_now_a_member_of", "guild" to guild.name))
            if (isOpenGuild) {
                player.sendMessage(lang.msg("command.migrated.guild.joinguilddirectly.guild_type_open_public"))
            }
            player.sendMessage(lang.msg("command.migrated.guild.joinguilddirectly.rank", "lowest_rank" to lowestRank.name))
            player.sendMessage(lang.msg("command.migrated.guild.joinguilddirectly.use_guild_menu_to_get_started"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)

            // Notify guild members
            val guildMembers = memberService.getGuildMembers(guildId)
            guildMembers.forEach { member ->
                if (member.playerId != playerId) {
                    val memberPlayer = player.server.getPlayer(member.playerId)
                    if (memberPlayer != null && memberPlayer.isOnline) {
                        memberPlayer.sendMessage(lang.msg("command.migrated.guild.joinguilddirectly.has_joined_the_guild", "player" to player.name))
                        memberPlayer.playSound(memberPlayer.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f)
                    }
                }
            }
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.joinguilddirectly.failed_to_join_guild_please_contact_an"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    @Subcommand("list")
    @CommandPermission("lumaguilds.guild.list")
    fun onList(player: Player) {
        val allGuilds = guildRepository.getAll()
        val openGuilds = allGuilds.filter { it.isOpen }

        if (openGuilds.isEmpty()) {
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.list.public_guilds"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.sendMessage(lang.msg("command.migrated.guild.list.no_open_guilds_available_at_the_moment"))
            player.sendMessage(lang.msg("command.migrated.guild.list.open_guilds_allow_anyone_to_join_without"))
            player.sendMessage(lang.msg("command.common.blank_line"))
            player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
            return
        }

        player.sendMessage(lang.msg("command.common.blank_line"))
        player.sendMessage(lang.msg("command.migrated.guild.list.public_guilds_2", "size" to openGuilds.size))
        player.sendMessage(lang.msg("command.migrated.guild.list.anyone_can_join_these_guilds"))
        player.sendMessage(lang.msg("command.common.blank_line"))

        openGuilds.sortedByDescending { memberService.getMemberCount(it.id) }.take(10).forEach { guild ->
            val memberCount = memberService.getMemberCount(guild.id)
            val emoji = guild.emoji ?: ""
            val tag = guild.tag ?: guild.name

            player.sendMessage(lang.msg("command.migrated.guild.list.members", "emoji" to emoji, "tag" to tag, "member_count" to memberCount))
            player.sendMessage(lang.msg("command.migrated.guild.list.join_guild_join", "guild" to guild.name))
            player.sendMessage(lang.msg("command.common.blank_line"))
        }

        if (openGuilds.size > 10) {
            player.sendMessage(lang.msg("command.migrated.guild.list.and_more_open_guilds", "size" to openGuilds.size - 10))
            player.sendMessage(lang.msg("command.common.blank_line"))
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
            player.sendMessage(lang.msg("command.migrated.guild.join.you_don_t_have_an_invitation_to", "guild" to guildName))
            player.sendMessage(lang.msg("command.migrated.guild.join.check_guild_invites_to_see_your_pending"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        val (guildId, actualGuildName) = invite

        // Remove the invitation
        net.lumalyte.lg.infrastructure.services.GuildInvitationManager.removeInvite(playerId, guildId)

        player.sendMessage(lang.msg("command.migrated.guild.decline.you_declined_the_invitation_to_join", "actual_guild_name" to actualGuildName))
        player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f)
    }

    @Subcommand("invites")
    @CommandPermission("lumaguilds.guild.invites")
    fun onInvites(player: Player) {
        val playerId = player.uniqueId
        val invites = net.lumalyte.lg.infrastructure.services.GuildInvitationManager.getInvites(playerId)

        if (invites.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.invites.you_have_no_pending_guild_invitations"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            return
        }

        player.sendMessage(lang.msg("command.common.blank_line"))
        player.sendMessage(lang.msg("command.migrated.guild.invites.pending_guild_invitations", "size" to invites.size))
        player.sendMessage(lang.msg("command.common.blank_line"))
        invites.forEach { (_, guildName) ->
            player.sendMessage(lang.msg("command.migrated.guild.invites.blank_line", "guild" to guildName))
            player.sendMessage(lang.msg("command.migrated.guild.invites.accept_guild_join", "guild" to guildName))
            player.sendMessage(lang.msg("command.migrated.guild.invites.decline_guild_decline", "guild" to guildName))
            player.sendMessage(lang.msg("command.common.blank_line"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if player has member management permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            player.sendMessage(lang.msg("command.migrated.guild.kick.you_don_t_have_permission_to_kick"))
            return
        }

        // Find target player - try online first, then offline
        val targetPlayer = findPlayerByName(targetPlayerName)

        if (targetPlayer != null) {
            if (targetPlayer == player) {
                player.sendMessage(lang.msg("command.migrated.guild.kick.you_cannot_kick_yourself"))
                return
            }

            val targetMember = memberService.getMember(targetPlayer.uniqueId, guild.id)
            if (targetMember == null) {
                player.sendMessage(lang.msg("command.migrated.guild.kick.is_not_in_your_guild", "player" to targetPlayer.name))
                return
            }

            val menuNavigator = MenuNavigator(player)
            menuNavigator.openMenu(menuFactory.createGuildKickConfirmationMenu(menuNavigator, player, guild, targetMember))
        } else {
            // Player is offline — resolve from guild member list
            val targetMember = findGuildMemberByName(guild.id, targetPlayerName)
            if (targetMember == null) {
                player.sendMessage(lang.msg("command.migrated.guild.kick.no_guild_member_named_found", "target_player_name" to targetPlayerName))
                return
            }

            if (targetMember.playerId == playerId) {
                player.sendMessage(lang.msg("command.migrated.guild.kick.you_cannot_kick_yourself"))
                return
            }

            val kickerRank = rankService.getPlayerRank(playerId, guild.id)
            val targetRank = rankService.getPlayerRank(targetMember.playerId, guild.id)
            if (kickerRank == null || targetRank == null || targetRank.priority <= kickerRank.priority) {
                player.sendMessage(lang.msg("command.migrated.guild.kick.you_cannot_kick_a_member_of_equal"))
                return
            }

            val success = memberService.removeMember(targetMember.playerId, guild.id, playerId)
            if (success) {
                val resolvedName = Bukkit.getOfflinePlayer(targetMember.playerId).name ?: targetPlayerName
                player.sendMessage(lang.msg("command.migrated.guild.kick.has_been_kicked_from_the_guild", "resolved_name" to resolvedName))
            } else {
                player.sendMessage(lang.msg("command.migrated.guild.kick.failed_to_kick", "target_player_name" to targetPlayerName))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
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
                player.sendMessage(lang.msg("command.migrated.guild.leave.you_are_the_only_member_of_this"))
                player.sendMessage(lang.msg("command.migrated.guild.leave.use_guild_disband_to_delete_the_guild"))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
                return
            }

            // Find the next highest rank member (lowest priority number after 0)
            val nextOwner = otherMembers.mapNotNull { member ->
                val rank = rankService.getPlayerRank(member.playerId, guild.id)
                rank?.let { member to it }
            }.minByOrNull { (_, rank) -> rank.priority }

            if (nextOwner == null) {
                player.sendMessage(lang.msg("command.migrated.guild.leave.failed_to_find_a_successor_please_contact"))
                return
            }

            val (successorMember, successorRank) = nextOwner

            // Transfer ownership automatically
            val transferSuccess = memberService.transferOwnership(guild.id, playerId, successorMember.playerId)
            if (!transferSuccess) {
                player.sendMessage(lang.msg("command.migrated.guild.leave.failed_to_transfer_ownership_automatically_use_guild"))
                return
            }

            // Notify about succession
            val successorPlayer = player.server.getPlayer(successorMember.playerId)
            if (successorPlayer != null) {
                successorPlayer.sendMessage(lang.msg("command.migrated.guild.leave.ownership_transferred"))
                successorPlayer.sendMessage(lang.msg("command.migrated.guild.leave.has_left_the_guild_and_you_are", "player" to player.name))
                successorPlayer.sendMessage(lang.msg("command.migrated.guild.leave.use_guild_menu_to_manage_your_guild"))
            }

            player.sendMessage(lang.msg("command.migrated.guild.leave.ownership_automatically_transferred_to", "rank" to (successorPlayer?.name ?: lang.legacy("command.migrated.guild.leave.next_highest_rank"))))
        }

        // Remove player from guild
        val success = memberService.removeMember(playerId, guild.id, playerId)

        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.leave.you_have_left", "guild" to guild.name))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f)

            // Notify guild members
            val guildMembers = memberService.getGuildMembers(guild.id)
            guildMembers.forEach { member ->
                val memberPlayer = player.server.getPlayer(member.playerId)
                if (memberPlayer != null && memberPlayer.isOnline) {
                    memberPlayer.sendMessage(lang.msg("command.migrated.guild.leave.has_left_the_guild", "player" to player.name))
                    memberPlayer.playSound(memberPlayer.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f)
                }
            }
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.leave.failed_to_leave_guild_please_contact_an"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if player is the owner (priority 0 rank)
        val playerRank = rankService.getPlayerRank(playerId, guild.id)
        if (playerRank?.priority != 0) {
            player.sendMessage(lang.msg("command.migrated.guild.transfer.only_the_guild_owner_can_transfer_ownership"))
            return
        }

        // Find target among guild members (supports offline players via OfflinePlayer)
        val targetMember = findGuildMemberByName(guild.id, targetPlayerName)
        if (targetMember == null) {
            player.sendMessage(lang.msg("command.migrated.guild.transfer.player_is_not_in_your_guild", "target_player_name" to targetPlayerName))
            return
        }

        if (targetMember.playerId == playerId) {
            player.sendMessage(lang.msg("command.migrated.guild.transfer.you_cannot_transfer_ownership_to_yourself"))
            return
        }

        val targetOffline = player.server.getOfflinePlayer(targetMember.playerId)
        val targetName = targetOffline.name ?: targetPlayerName

        // Perform ownership transfer
        val success = memberService.transferOwnership(guild.id, playerId, targetMember.playerId)

        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.transfer.ownership_of_has_been_transferred_to", "guild" to guild.name, "target_name" to targetName))
            player.sendMessage(lang.msg("command.migrated.guild.transfer.you_are_now_a_co_owner"))

            // Only notify the new owner if they are online
            val targetOnline = targetOffline.player
            if (targetOnline != null) {
                targetOnline.sendMessage(lang.msg("command.migrated.guild.transfer.promotion"))
                targetOnline.sendMessage(lang.msg("command.migrated.guild.transfer.you_are_now_the_owner_of", "guild" to guild.name))
                targetOnline.sendMessage(lang.msg("command.migrated.guild.leave.use_guild_menu_to_manage_your_guild"))
            }

            // Notify all other guild members
            val guildMembers = memberService.getGuildMembers(guild.id)
            guildMembers.forEach { member ->
                if (member.playerId != playerId && member.playerId != targetMember.playerId) {
                    val memberPlayer = player.server.getPlayer(member.playerId)
                    memberPlayer?.sendMessage(lang.msg("command.migrated.guild.transfer.has_transferred_ownership_of_the_guild_to", "player" to player.name, "target_name" to targetName))
                }
            }
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.transfer.failed_to_transfer_ownership_please_contact_an"))
        }
    }

    @Subcommand("tag")
    @CommandPermission("lumaguilds.guild.tag")
    fun onTag(player: Player, @Optional tag: String?) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if player has permission to manage guild settings
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_BANNER)) {
            player.sendMessage(lang.msg("command.migrated.guild.tag.you_don_t_have_permission_to_manage"))
            player.sendMessage(lang.msg("command.migrated.guild.tag.you_need_the_manage_banner_permission_to"))
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
            player.sendMessage(lang.msg("command.migrated.guild.tag.guild_tag_cannot_be_empty"))
            return
        }

        if (tag.contains("<<") || tag.contains(">>")) {
            player.sendMessage(lang.msg("command.migrated.guild.tag.invalid_tag_syntax_double_brackets"))
            return
        }

        val nameFilterConfig2 = configService.loadConfig().guild.nameFilter
        net.lumalyte.lg.utils.GuildTagValidator.rejectionReason(tag, nameFilterConfig2)?.let { reason ->
            player.sendMessage(lang.msg("command.migrated.guild.create.blank_line", "reason" to reason))
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
            player.sendMessage(lang.msg("command.migrated.guild.tag.invalid_tag", "msg" to msg))
            return
        }

        if (visibleChars > 32) {
            player.sendMessage(lang.msg("command.migrated.guild.tag.guild_tag_too_long_32_visible_characters", "visible_chars" to visibleChars))
            return
        }

        // Set the tag
        val success = guildService.setTag(guild.id, tag, playerId)

        if (success) {
            val rendered = net.lumalyte.lg.utils.ColorCodeUtils.renderTagForDisplay(tag)
            player.sendMessage(lang.msg("command.migrated.guild.tag.guild_tag_set_to", "rendered" to rendered))
            player.sendMessage(lang.msg("command.migrated.guild.tag.this_will_be_displayed_next_to_guild"))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.tag.failed_to_set_guild_tag_the_tag"))
        }
    }

    @Subcommand("bannerman")
    @CommandPermission("lumaguilds.guild.bannerman")
    fun onBannerman(player: Player) {
        val playerId = player.uniqueId

        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }
        val guild = guilds.first()

        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_BANNER)) {
            player.sendMessage(lang.msg("command.migrated.guild.bannerman.you_don_t_have_permission_to_toggle"))
            player.sendMessage(lang.msg("command.migrated.guild.bannerman.you_need_the_manage_banner_permission"))
            return
        }

        val current = guildService.getBannermanEnabled(guild.id)
        val newState = !current
        val success = guildService.setBannermanEnabled(guild.id, newState, playerId)
        if (!success) {
            player.sendMessage(lang.msg("command.migrated.guild.bannerman.failed_to_toggle_bannerman"))
            return
        }

        refreshBannermanDisplay(guild.id, newState, player)
    }

    private fun refreshBannermanDisplay(guildId: UUID, newState: Boolean, player: Player) {
        try {
            if (newState) {
                bannermanListeners.onBannermanEnabled(guildId)
            } else {
                bannermanListeners.onBannermanDisabled(guildId)
            }
        } catch (e: Exception) {
            org.bukkit.Bukkit.getLogger().warning(
                "Bannerman render callback failed for guild $guildId (newState=$newState): ${e.message}"
            )
            player.sendMessage(lang.msg("command.migrated.guild.refreshbannermandisplay.bannerman_state_was_saved_but_live_refresh"))
            return
        }
        if (newState) {
            player.sendMessage(lang.msg("command.migrated.guild.refreshbannermandisplay.bannerman_enabled_guild_members_will_wear_the"))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.refreshbannermandisplay.bannerman_disabled"))
        }
    }

    @Subcommand("description|desc")
    @CommandPermission("lumaguilds.guild.description")
    fun onDescription(player: Player, @Optional description: String?) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if player has permission to manage guild settings
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_DESCRIPTION)) {
            player.sendMessage(lang.msg("command.migrated.guild.description.you_don_t_have_permission_to_manage"))
            player.sendMessage(lang.msg("command.migrated.guild.description.you_need_the_manage_description_permission_to"))
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
            player.sendMessage(lang.msg("command.migrated.guild.description.guild_description_must_be_100_characters_or"))
            player.sendMessage(lang.msg("command.migrated.guild.description.your_description_is_characters_long", "length" to description.length))
            return
        }

        // Set the description
        val success = guildService.setDescription(guild.id, description, playerId)

        if (success) {
            player.sendMessage(lang.msg("command.migrated.guild.description.guild_description_set"))
            player.sendMessage(lang.msg("command.migrated.guild.description.new_description", "description" to description))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.description.failed_to_set_guild_description"))
        }
    }

    @Subcommand("war")
    @CommandPermission("lumaguilds.guild.war")
    fun onWar(player: Player) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if player has permission to manage wars (DECLARE_WAR permission)
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage(lang.msg("command.guild.war.no_permission"))
            player.sendMessage(lang.msg("command.migrated.guild.war.you_need_the_declare_war_permission_to"))
            return
        }

        // Open the war management menu
        val menuNavigator = MenuNavigator(player)
        menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        player.sendMessage(lang.msg("command.migrated.guild.war.opening_war_management_menu"))
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
                player.sendMessage(lang.msg("command.migrated.guild.setguildhomecommand.warning_that_home_looks_unsafe", "reason" to safetyResult.reason))
                player.sendMessage(lang.msg("command.migrated.guild.setguildhomecommand.use_guild_sethome_confirm_within_10s_to"))
                return
            }
        }

        val success = guildService.setHome(guild.id, homeName, home, player.uniqueId)

        if (success) {
            val homeLabel = if (homeName == "main") "main home" else "home '$homeName'"
            player.sendMessage(lang.msg("command.migrated.guild.setguildhomecommand.guild_set_successfully", "home_label" to homeLabel))
            if (config.claimsEnabled) {
                player.sendMessage(lang.msg("command.migrated.guild.setguildhomecommand.this_location_is_within_your_guild_s"))
            }
            player.sendMessage(lang.msg("command.migrated.guild.setguildhomecommand.members_can_now_use_guild_home_to"))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.setguildhomecommand.failed_to_set_guild_home_you_may"))
        }
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
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.physical_vault_system_is_not_enabled_on"))
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.contact_a_server_administrator_if_you_think"))
            return
        }

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if player has PLACE_VAULT permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.PLACE_VAULT)) {
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.permission_denied"))
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.you_don_t_have_permission_to_get"))
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.you_need_the_place_vault_permission"))
            return
        }

        // Check if vault already exists
        if (guild.vaultStatus == net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE) {
            val vaultLocation = vaultService.getVaultLocation(guild)
            if (vaultLocation != null) {
                player.sendMessage(lang.msg("command.migrated.guild.vaultget.vault_exists"))
                player.sendMessage(lang.msg("command.migrated.guild.vaultget.your_guild_already_has_a_vault_chest"))
                player.sendMessage(lang.msg("command.migrated.guild.vaultget.location", "world" to vaultLocation.world?.name, "block_x" to vaultLocation.blockX, "block_y" to vaultLocation.blockY, "block_z" to vaultLocation.blockZ))
                player.sendMessage(lang.msg("command.migrated.guild.vaultget.break_the_existing_vault_first_if_you"))
                return
            }
        }

        // Check if player has space in inventory
        if (player.inventory.firstEmpty() == -1) {
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.inventory_full"))
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.your_inventory_is_full_make_space_to"))
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
        val displayName = lang.msg("command.migrated.guild.vaultget.guild_vault")
            .append(guildDisplay)
            .append(lang.msg("command.migrated.guild.vaultget.blank_line"))
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)

        meta.displayName(displayName)

        meta.lore(listOf(
            lang.msg("command.migrated.guild.vaultget.place_this_chest_to_create_your_guild"),
            lang.msg("command.migrated.guild.vaultget.physical_vault_storage"),
            net.kyori.adventure.text.Component.text(""),
            lang.msg("command.migrated.guild.vaultget.capacity_slots_level", "level" to vaultService.getCapacityForLevel(guild.level), "level_2" to guild.level),
            lang.msg("command.migrated.guild.vaultget.guild", "guild" to guild.name),
            net.kyori.adventure.text.Component.text(""),
            lang.msg("command.migrated.guild.vaultget.only_one_vault_can_exist_per_guild"),
            lang.msg("command.migrated.guild.vaultget.protected_only_guild_members_can_break_it")
        ).map { it.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false) })

        // Add persistent data to identify this as a guild vault chest
        meta.persistentDataContainer.set(net.lumalyte.lg.common.PluginKeys.GUILD_VAULT_ID, org.bukkit.persistence.PersistentDataType.STRING, guild.id.toString())

        vaultChest.itemMeta = meta

        // Give the item to the player
        player.inventory.addItem(vaultChest)

        player.sendMessage(lang.msg("command.migrated.guild.vaultget.vault_chest_received"))
        player.sendMessage(lang.msg("command.migrated.guild.vaultget.you_ve_received_a_guild_vault_chest"))
        player.sendMessage(lang.msg("command.common.blank_line"))
        player.sendMessage(lang.msg("command.migrated.guild.vaultget.how_to_use"))
        player.sendMessage(lang.msg("command.migrated.guild.vaultget.1_find_a_safe_location_in_your"))
        player.sendMessage(lang.msg("command.migrated.guild.vaultget.2_place_the_chest_on_the_ground"))
        player.sendMessage(lang.msg("command.migrated.guild.vaultget.3_access_it_through_guild_menu_bank"))
        player.sendMessage(lang.msg("command.common.blank_line"))
        player.sendMessage(lang.msg("command.migrated.guild.vaultget.capacity_slots", "level" to vaultService.getCapacityForLevel(guild.level)))
        player.sendMessage(lang.msg("command.migrated.guild.vaultget.upgrades_as_your_guild_levels_up"))

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
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.physical_vault_system_is_not_enabled_on"))
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.contact_a_server_administrator_if_you_think"))
            return
        }

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check if vault is available
        if (guild.vaultStatus != net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE) {
            player.sendMessage(lang.msg("command.migrated.guild.vault.vault_unavailable"))
            when (guild.vaultStatus) {
                net.lumalyte.lg.domain.entities.VaultStatus.NEVER_PLACED -> {
                    player.sendMessage(lang.msg("command.migrated.guild.vault.your_guild_hasn_t_placed_a_vault"))
                    player.sendMessage(lang.msg("command.migrated.guild.vault.use_guild_getvault_to_get_a_vault"))
                }
                net.lumalyte.lg.domain.entities.VaultStatus.UNAVAILABLE -> {
                    player.sendMessage(lang.msg("command.migrated.guild.vault.your_guild_s_vault_chest_has_been"))
                    player.sendMessage(lang.msg("command.migrated.guild.vault.use_guild_getvault_to_get_a_new"))
                }
                else -> {
                    player.sendMessage(lang.msg("command.migrated.guild.vault.vault_is_not_available"))
                }
            }
            return
        }

        // Check if player has ACCESS_VAULT permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.ACCESS_VAULT)) {
            player.sendMessage(lang.msg("command.migrated.guild.vaultget.permission_denied"))
            player.sendMessage(lang.msg("command.migrated.guild.vault.you_don_t_have_permission_to_access"))
            player.sendMessage(lang.msg("command.migrated.guild.vault.you_need_the_access_vault_permission"))
            return
        }

        // Open vault inventory
        val result = vaultService.openVaultInventory(player, guild)
        when (result) {
            is net.lumalyte.lg.application.services.VaultResult.Success -> {
                player.sendMessage(lang.msg("command.migrated.guild.vault.vault_opened"))
                player.sendMessage(lang.msg("command.migrated.guild.vault.accessing_s_vault", "guild" to guild.name))
                player.playSound(player.location, org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f)
            }
            is net.lumalyte.lg.application.services.VaultResult.Failure -> {
                player.sendMessage(lang.msg("command.migrated.guild.vault.failed"))
                player.sendMessage(lang.msg("command.migrated.guild.vault.couldn_t_open_vault", "reason" to result.message))
                player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
            }
        }
    }

    @Subcommand("help")
    @CommandPermission("lumaguilds.guild.help")
    fun onHelp(player: Player, @Optional topic: String?) {
        val renderer = HelpTopicsRenderer
        if (topic.isNullOrBlank()) {
            player.sendMessage(renderer.renderTopicMenu(lang))
            return
        }
        val found = HelpTopics.bySlug(topic)
        if (found == null) {
            val helpCommand = Component.text("/g help", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/g help"))
            player.sendMessage(lang.msg(
                "command.guild.help.unknown_topic",
                "topic" to topic,
                "help_command" to helpCommand,
            ))
            return
        }
        player.sendMessage(renderer.renderTopicPage(found, lang))
    }

    @Subcommand("ally")
    @CommandPermission("lumaguilds.guild.ally")
    @CommandCompletion("@guilds")
    fun onAlly(player: Player, guildName: String) {
        val playerId = player.uniqueId

        // Find player's guild
        val guilds = guildService.getPlayerGuilds(playerId)
        if (guilds.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check MANAGE_RELATIONS permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_don_t_have_permission_to_manage"))
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_need_the_manage_relations_permission"))
            return
        }

        // Resolve target guild (by name or by player name)
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.no_guild_or_player_named_found", "guild" to guildName))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_cannot_ally_with_your_own_guild"))
            return
        }

        // Get relation service
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()

        // Check current relation
        val currentRelation = relationService.getRelationType(guild.id, targetGuild.id)
        if (currentRelation == net.lumalyte.lg.domain.entities.RelationType.ALLY) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_are_already_allied_with", "guild" to targetGuild.name))
            return
        }

        if (currentRelation == net.lumalyte.lg.domain.entities.RelationType.ENEMY) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_are_at_war_with", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("command.migrated.guild.ally.request_a_truce_first_guild_truce", "guild" to targetGuild.name))
            return
        }

        // Check for pending requests
        val pendingRequests = relationService.getPendingRequests(guild.id)
        if (pendingRequests.any { it.getOtherGuild(guild.id) == targetGuild.id }) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_already_have_a_pending_request_with", "guild" to targetGuild.name))
            return
        }

        // Request alliance
        val relation = relationService.requestAlliance(guild.id, targetGuild.id, playerId)
        if (relation != null) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.alliance_request_sent_to", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("command.migrated.guild.ally.they_must_accept_your_request_for_the"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, lang.msg("command.migrated.guild.ally.has_requested_an_alliance_with_your_guild", "guild" to guild.name))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.ally.failed_to_send_alliance_request"))
            player.sendMessage(lang.msg("command.migrated.guild.ally.there_may_already_be_a_pending_request"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check DECLARE_WAR permission (specific permission for war)
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage(lang.msg("command.migrated.guild.enemy.you_don_t_have_permission_to_declare"))
            player.sendMessage(lang.msg("command.migrated.guild.enemy.you_need_the_declare_war_permission"))
            return
        }

        // Resolve target guild (by name or by player name)
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.no_guild_or_player_named_found", "guild" to guildName))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("command.migrated.guild.enemy.you_cannot_declare_war_on_your_own"))
            return
        }

        // Get relation service
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()

        // Check current relation
        val currentRelation = relationService.getRelationType(guild.id, targetGuild.id)
        if (currentRelation == net.lumalyte.lg.domain.entities.RelationType.ENEMY) {
            player.sendMessage(lang.msg("command.migrated.guild.enemy.you_are_already_at_war_with", "guild" to targetGuild.name))
            return
        }

        if (currentRelation == net.lumalyte.lg.domain.entities.RelationType.ALLY) {
            player.sendMessage(lang.msg("command.migrated.guild.enemy.you_are_allied_with", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("command.migrated.guild.enemy.you_must_break_the_alliance_first_through"))
            return
        }

        // Declare war (immediate effect)
        val relation = relationService.declareWar(guild.id, targetGuild.id, playerId)
        if (relation != null) {
            player.sendMessage(lang.msg("command.migrated.guild.enemy.war_declared_against", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("command.migrated.guild.enemy.your_guilds_are_now_enemies"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, lang.msg("command.migrated.guild.enemy.has_declared_war_on_your_guild", "guild" to guild.name))

            // Broadcast to all online players
            net.lumalyte.lg.utils.ChatUtils.broadcastMessage(lang.legacy("command.migrated.guild.enemy.has_declared_war_on", "guild" to guild.name, "guild_2" to targetGuild.name), player)
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.enemy.failed_to_declare_war"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check MANAGE_RELATIONS permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_don_t_have_permission_to_manage"))
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_need_the_manage_relations_permission"))
            return
        }

        // Resolve target guild (by name or by player name)
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.no_guild_or_player_named_found", "guild" to guildName))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("command.migrated.guild.truce.you_cannot_request_a_truce_with_your"))
            return
        }

        // Get relation service
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()

        // Check current relation
        val currentRelation = relationService.getRelationType(guild.id, targetGuild.id)
        if (currentRelation != net.lumalyte.lg.domain.entities.RelationType.ENEMY) {
            player.sendMessage(lang.msg("command.migrated.guild.truce.you_can_only_request_a_truce_with"))
            player.sendMessage(lang.msg("command.migrated.guild.truce.current_relation_with", "guild" to targetGuild.name, "name" to currentRelation.name.lowercase()))
            return
        }

        // Validate duration (1-90 days, default 14)
        val duration = durationDays ?: 14
        if (duration < 1 || duration > 90) {
            player.sendMessage(lang.msg("command.migrated.guild.truce.truce_duration_must_be_between_1_and"))
            return
        }

        // Request truce
        val relation = relationService.requestTruce(guild.id, targetGuild.id, playerId, java.time.Duration.ofDays(duration.toLong()))
        if (relation != null) {
            player.sendMessage(lang.msg("command.migrated.guild.truce.truce_request_sent_to_for_days", "guild" to targetGuild.name, "duration" to duration))
            player.sendMessage(lang.msg("command.migrated.guild.truce.they_must_accept_your_request_for_the"))
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, lang.msg("command.migrated.guild.truce.has_requested_a_day_truce_with_your", "guild" to guild.name, "duration" to duration))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.truce.failed_to_send_truce_request"))
            player.sendMessage(lang.msg("command.migrated.guild.ally.there_may_already_be_a_pending_request"))
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
            player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
            return
        }

        val guild = guilds.first()

        // Check MANAGE_RELATIONS permission
        if (!memberService.hasPermission(playerId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_don_t_have_permission_to_manage"))
            player.sendMessage(lang.msg("command.migrated.guild.ally.you_need_the_manage_relations_permission"))
            return
        }

        // Resolve target guild (by name or by player name)
        val targetGuild = net.lumalyte.lg.utils.GuildResolver.resolve(guildName, guildService)
        if (targetGuild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.ally.no_guild_or_player_named_found", "guild" to guildName))
            return
        }

        if (targetGuild.id == guild.id) {
            player.sendMessage(lang.msg("command.migrated.guild.neutral.you_cannot_request_peace_with_your_own"))
            return
        }

        // Get relation service
        val relationService: net.lumalyte.lg.application.services.RelationService by inject()

        // Check current relation
        val currentRelation = relationService.getRelationType(guild.id, targetGuild.id)
        if (currentRelation != net.lumalyte.lg.domain.entities.RelationType.ENEMY) {
            player.sendMessage(lang.msg("command.migrated.guild.neutral.you_can_only_request_peace_with_enemy"))
            player.sendMessage(lang.msg("command.migrated.guild.truce.current_relation_with", "guild" to targetGuild.name, "name" to currentRelation.name.lowercase()))
            return
        }

        // Request unenemy (peace)
        val relation = relationService.requestUnenemy(guild.id, targetGuild.id, playerId)
        if (relation != null) {
            player.sendMessage(lang.msg("command.migrated.guild.neutral.peace_request_sent_to", "guild" to targetGuild.name))
            player.sendMessage(lang.msg("command.migrated.guild.neutral.if_accepted_hostilities_will_end_permanently"))
            player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f)

            // Notify target guild members
            notifyGuildMembers(targetGuild.id, lang.msg("command.migrated.guild.neutral.has_requested_to_end_hostilities_with_your", "guild" to guild.name))
        } else {
            player.sendMessage(lang.msg("command.migrated.guild.neutral.failed_to_send_peace_request"))
            player.sendMessage(lang.msg("command.migrated.guild.ally.there_may_already_be_a_pending_request"))
            player.playSound(player.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        }
    }

    @Subcommand("balance|bal")
    @CommandPermission("lumaguilds.guild.bal")
    @CommandCompletion("@guilds")
    fun onBalance(player: Player, @Optional guildName: String?) {
        val playerId = player.uniqueId

        // Resolve which guild to show
        val guild = if (guildName != null) {
            guildService.getGuildByName(guildName)
        } else {
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                player.sendMessage(lang.msg("command.migrated.guild.rename.you_are_not_in_a_guild"))
                return
            }
            guilds.first()
        }

        if (guild == null) {
            player.sendMessage(lang.msg("command.migrated.guild.resolveallytarget.no_guild_named_found", "guild" to guildName))
            return
        }

        val balance = bankService.getBalance(guild.id)
        val formatted = java.text.NumberFormat.getIntegerInstance().format(balance.toLong())
        player.sendMessage(lang.msg("command.guild.balance.success", "guild" to guild.name, "balance" to formatted))
    }

    @Subcommand("baltop")
    @CommandPermission("lumaguilds.guild.baltop")
    fun onBaltop(player: Player) {
        val top = bankService.getTopBalances(15)
        if (top.isEmpty()) {
            player.sendMessage(lang.msg("command.migrated.guild.baltop.no_guild_balance_data_available"))
            return
        }

        val nameById = guildService.getAllGuilds().associateBy { it.id }
        player.sendMessage(lang.msg("command.migrated.guild.baltop.lumaguilds_top_guilds_by_balance"))
        player.sendMessage(lang.msg("command.migrated.guild.baltop.blank_line"))
        var index = 0
        for ((guildId, balance) in top) {
            val guild = nameById[guildId]
            val label = guild?.name ?: guildId.toString().take(8)
            val formatted = java.text.NumberFormat.getIntegerInstance().format(balance.toLong())
            player.sendMessage(lang.msg("command.migrated.guild.baltop.blank_line_2", "index" to ++index, "label" to label, "balance" to formatted))
        }
    }

}
