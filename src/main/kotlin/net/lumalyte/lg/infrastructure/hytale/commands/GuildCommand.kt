package net.lumalyte.lg.infrastructure.hytale.commands

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.InvitationService
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.infrastructure.hytale.sounds.GuildSounds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Main /guild command for LumaGuilds.
 *
 * This command handles all guild-related operations:
 * - /guild create <name> - Create a new guild
 * - /guild info - View guild information
 * - /guild list - List all guilds
 */
class GuildCommand : CommandBase("guild", "Manage your guild"), KoinComponent {

    private val guildService: GuildService by inject()

    init {
        // Add all subcommands
        addSubCommand(GuildCreateCommand())
        addSubCommand(GuildInfoCommand())
        addSubCommand(GuildListCommand())
        addSubCommand(GuildMenuCommand())  // NEW: Guild Control Panel
        addSubCommand(GuildInviteCommand())
        addSubCommand(GuildAcceptCommand())
        addSubCommand(GuildDeclineCommand())
        addSubCommand(GuildInvitesCommand())

        // Base permission
        requirePermission("lumaguilds.guild")
    }

    override fun executeSync(context: CommandContext) {
        // Show help/usage when no subcommand is provided
        context.sendMessage(
            Message.raw("=== ")
                .insert(Message.raw("LumaGuilds").color("gold").bold(true))
                .insert(" ===\n")
                .insert("Available commands:\n")
                .insert(Message.raw("/guild menu").color("yellow"))
                .insert(" - Open guild control panel ").insert(Message.raw("⚔").color("gold")).insert("\n")
                .insert(Message.raw("/guild create <name>").color("yellow"))
                .insert(" - Create a new guild\n")
                .insert(Message.raw("/guild info").color("yellow"))
                .insert(" - View guild information\n")
                .insert(Message.raw("/guild list").color("yellow"))
                .insert(" - List all guilds")
        )
    }
}

/**
 * /guild create <name>
 * Creates a new guild with the specified name.
 */
class GuildCreateCommand : CommandBase("create", "Create a new guild"), KoinComponent {

    private val guildService: GuildService by inject()
    private val guildNameArg = withRequiredArg("name", "The guild name", ArgTypes.STRING)

    init {
        requirePermission("lumaguilds.guild.create")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can create guilds!").color("red"))
            return
        }

        val playerId = context.sender().uuid
        val guildName = context.get(guildNameArg)

        // Get PlayerRef for sound playback
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(playerId)

        // Validate guild name length
        if (guildName.length < 3 || guildName.length > 32) {
            context.sendMessage(
                Message.raw("Guild name must be between 3 and 32 characters!")
                    .color("red")
            )
            return
        }

        // Validate guild name characters (alphanumeric + spaces only)
        if (!guildName.matches(Regex("^[a-zA-Z0-9 ]+$"))) {
            context.sendMessage(
                Message.raw("Guild name can only contain letters, numbers, and spaces!")
                    .color("red")
            )
            return
        }

        try {
            // Create the guild using GuildService
            val guild = guildService.createGuild(
                name = guildName,
                ownerId = playerId
            )

            if (guild == null) {
                context.sendMessage(
                    Message.raw("Failed to create guild. You may already be in a guild or the name is taken.")
                        .color("red")
                )
                return
            }

            // Play guild created sound
            if (playerRef != null) GuildSounds.playGuildCreated(playerRef)

            // Success message
            context.sendMessage(
                Message.raw("✓ Guild ")
                    .color("green")
                    .insert(Message.raw(guild.name).color("gold").bold(true))
                    .insert(Message.raw(" created successfully!").color("green"))
            )

            context.sendMessage(
                Message.raw("You are now the owner of ").color("gray")
                    .insert(Message.raw(guild.name).color("gold"))
                    .insert("!")
            )

        } catch (e: Exception) {
            // Unexpected error
            context.sendMessage(
                Message.raw("Failed to create guild: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /guild info
 * Displays information about your guild.
 */
class GuildInfoCommand : CommandBase("info", "View guild information"), KoinComponent {

    private val guildService: GuildService by inject()

    init {
        requirePermission("lumaguilds.guild.info")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can view guild info!").color("red"))
            return
        }

        val playerId = context.sender().uuid

        try {
            val guilds = guildService.getPlayerGuilds(playerId)
            if (guilds.isEmpty()) {
                context.sendMessage(
                    Message.raw("You are not in a guild!")
                        .color("red")
                )
                return
            }

            // Get first guild (players can be in multiple guilds in this system)
            val guild = guilds.first()

            // Build guild info message
            val message = Message.raw("=== ")
                .insert(Message.raw("Guild Info").color("gold").bold(true))
                .insert(" ===\n")
                .insert(Message.raw("Name: ").color("gray"))
                .insert(Message.raw(guild.name).color("yellow").bold(true))
                .insert("\n")
                .insert(Message.raw("Level: ").color("gray"))
                .insert(Message.raw(guild.level.toString()).color("green"))
                .insert("\n")
                .insert(Message.raw("Mode: ").color("gray"))
                .insert(Message.raw(guild.mode.name).color(
                    if (guild.mode == GuildMode.PEACEFUL) "green" else "red"
                ))
                .insert("\n")
                .insert(Message.raw("Bank: ").color("gray"))
                .insert(Message.raw("${guild.bankBalance} coins").color("yellow"))

            if (!guild.description.isNullOrBlank()) {
                message.insert("\n")
                    .insert(Message.raw("Description: ").color("gray"))
                    .insert(guild.description!!)
            }

            context.sendMessage(message)

        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to retrieve guild info: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /guild list
 * Lists all guilds in the system.
 */
class GuildListCommand : CommandBase("list", "List all guilds"), KoinComponent {

    private val guildService: GuildService by inject()

    init {
        requirePermission("lumaguilds.guild.list")
    }

    override fun executeSync(context: CommandContext) {
        try {
            val allGuilds = guildService.getAllGuilds()

            if (allGuilds.isEmpty()) {
                context.sendMessage(
                    Message.raw("There are no guilds yet!")
                        .color("yellow")
                )
                return
            }

            // Build guilds list message
            val message = Message.raw("=== ")
                .insert(Message.raw("All Guilds").color("gold").bold(true))
                .insert(" (${allGuilds.size}) ===\n")

            allGuilds.forEachIndexed { index, guild ->
                message.insert(Message.raw("${index + 1}. ").color("gray"))
                    .insert(Message.raw(guild.name).color("yellow").bold(true))
                    .insert(" - Level ")
                    .insert(Message.raw(guild.level.toString()).color("green"))

                if (index < allGuilds.size - 1) {
                    message.insert("\n")
                }
            }

            context.sendMessage(message)

        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to list guilds: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /guild invite <player>
 * Invites a player to join your guild.
 */
class GuildInviteCommand : CommandBase("invite", "Invite a player to your guild"), KoinComponent {

    private val guildService: GuildService by inject()
    private val invitationService: InvitationService by inject()
    private val playerNameArg = withRequiredArg("player", "The player to invite", ArgTypes.STRING)

    init {
        requirePermission("lumaguilds.guild.invite")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can invite to guilds!").color("red"))
            return
        }

        val inviterId = context.sender().uuid
        val targetPlayerName = context.get(playerNameArg)

        // Get PlayerRef for sound playback
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(inviterId)

        try {
            // Get inviter's guild
            val guilds = guildService.getPlayerGuilds(inviterId)
            if (guilds.isEmpty()) {
                context.sendMessage(
                    Message.raw("You are not in a guild!")
                        .color("red")
                )
                return
            }

            val guild = guilds.first()

            // TODO: Check if inviter has permission to invite (requires rank system)
            // For now, any guild member can invite

            // Look up target player by username
            val targetPlayerRef = universe.getPlayerByUsername(
                targetPlayerName,
                com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE
            )

            if (targetPlayerRef == null) {
                context.sendMessage(
                    Message.raw("Player '$targetPlayerName' not found or is not online!")
                        .color("red")
                )
                return
            }

            val targetPlayerId = targetPlayerRef.getUuid()!!  // Safe: PlayerRef always has UUID

            // Send invitation
            val success = invitationService.sendInvitation(
                guildId = guild.id,
                invitedPlayerId = targetPlayerId,
                inviterPlayerId = inviterId
            )

            if (success) {
                // Play invitation sent sound
                if (playerRef != null) GuildSounds.playInvitationSent(playerRef)

                context.sendMessage(
                    Message.raw("✓ Invitation sent to player!")
                        .color("green")
                )
            } else {
                context.sendMessage(
                    Message.raw("Failed to send invitation. Player may already be in the guild or have a pending invite.")
                        .color("red")
                )
            }

        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to send invitation: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /guild accept <guild>
 * Accepts a pending guild invitation.
 */
class GuildAcceptCommand : CommandBase("accept", "Accept a guild invitation"), KoinComponent {

    private val invitationService: InvitationService by inject()
    private val guildNameArg = withRequiredArg("guild", "The guild name to accept", ArgTypes.STRING)

    init {
        requirePermission("lumaguilds.guild.accept")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can accept guild invitations!").color("red"))
            return
        }

        val playerId = context.sender().uuid
        val guildName = context.get(guildNameArg)

        // Get PlayerRef for sound playback
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(playerId)

        try {
            // Get player's pending invitations
            val invitations = invitationService.getPlayerInvitations(playerId)

            // Find invitation matching the guild name
            val invitation = invitations.firstOrNull { it.guildName.equals(guildName, ignoreCase = true) }

            if (invitation == null) {
                context.sendMessage(
                    Message.raw("You don't have a pending invitation from guild '$guildName'!")
                        .color("red")
                )
                return
            }

            // Accept the invitation
            val success = invitationService.acceptInvitation(
                playerId = playerId,
                guildId = invitation.guildId
            )

            if (success) {
                // Play invitation accepted sound
                if (playerRef != null) GuildSounds.playInvitationAccepted(playerRef)

                context.sendMessage(
                    Message.raw("✓ You have joined ")
                        .color("green")
                        .insert(Message.raw(invitation.guildName).color("gold").bold(true))
                        .insert(Message.raw("!").color("green"))
                )
            } else {
                context.sendMessage(
                    Message.raw("Failed to accept invitation. You may already be in a guild.")
                        .color("red")
                )
            }

        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to accept invitation: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /guild decline <guild>
 * Declines a pending guild invitation.
 */
class GuildDeclineCommand : CommandBase("decline", "Decline a guild invitation"), KoinComponent {

    private val invitationService: InvitationService by inject()
    private val guildNameArg = withRequiredArg("guild", "The guild name to decline", ArgTypes.STRING)

    init {
        requirePermission("lumaguilds.guild.decline")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can decline guild invitations!").color("red"))
            return
        }

        val playerId = context.sender().uuid
        val guildName = context.get(guildNameArg)

        // Get PlayerRef for sound playback
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(playerId)

        try {
            // Get player's pending invitations
            val invitations = invitationService.getPlayerInvitations(playerId)

            // Find invitation matching the guild name
            val invitation = invitations.firstOrNull { it.guildName.equals(guildName, ignoreCase = true) }

            if (invitation == null) {
                context.sendMessage(
                    Message.raw("You don't have a pending invitation from guild '$guildName'!")
                        .color("red")
                )
                return
            }

            // Decline the invitation
            val success = invitationService.declineInvitation(
                playerId = playerId,
                guildId = invitation.guildId
            )

            if (success) {
                // Play invitation declined sound
                if (playerRef != null) GuildSounds.playInvitationDeclined(playerRef)

                context.sendMessage(
                    Message.raw("✓ Declined invitation from ")
                        .color("yellow")
                        .insert(Message.raw(invitation.guildName).color("gold").bold(true))
                )
            } else {
                context.sendMessage(
                    Message.raw("Failed to decline invitation.")
                        .color("red")
                )
            }

        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to decline invitation: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /guild invites
 * Lists all pending guild invitations for the player.
 */
class GuildInvitesCommand : CommandBase("invites", "View your pending guild invitations"), KoinComponent {

    private val invitationService: InvitationService by inject()

    init {
        requirePermission("lumaguilds.guild.invites")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can view guild invitations!").color("red"))
            return
        }

        val playerId = context.sender().uuid

        try {
            val invitations = invitationService.getPlayerInvitations(playerId)

            if (invitations.isEmpty()) {
                context.sendMessage(
                    Message.raw("You have no pending guild invitations.")
                        .color("yellow")
                )
                return
            }

            // Build invitations list message
            val message = Message.raw("=== ")
                .insert(Message.raw("Pending Invitations").color("gold").bold(true))
                .insert(" (${invitations.size}) ===\n")

            invitations.forEachIndexed { index, invitation ->
                message.insert(Message.raw("${index + 1}. ").color("gray"))
                    .insert(Message.raw(invitation.guildName).color("yellow").bold(true))
                    .insert(" - invited by ")
                    .insert(Message.raw(invitation.inviterName).color("aqua"))

                if (index < invitations.size - 1) {
                    message.insert("\n")
                }
            }

            message.insert("\n")
                .insert(Message.raw("Use ").color("gray"))
                .insert(Message.raw("/guild accept <guild>").color("green"))
                .insert(Message.raw(" or ").color("gray"))
                .insert(Message.raw("/guild decline <guild>").color("red"))

            context.sendMessage(message)

        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to retrieve invitations: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}
