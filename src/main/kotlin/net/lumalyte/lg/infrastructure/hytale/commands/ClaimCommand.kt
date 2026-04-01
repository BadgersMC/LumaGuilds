package net.lumalyte.lg.infrastructure.hytale.commands

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import net.lumalyte.lg.application.actions.claim.CreateClaim
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.actions.claim.ListPlayerClaims
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.persistence.PartitionRepository
import net.lumalyte.lg.application.results.claim.CreateClaimResult
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.domain.entities.Partition
import net.lumalyte.lg.domain.values.Position
import net.lumalyte.lg.domain.values.Position3D
import net.lumalyte.lg.infrastructure.hytale.sounds.ClaimSounds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Main /claim command for LumaGuilds land claiming.
 *
 * Subcommands:
 * - /claim create <name> - Create a new claim at your current location
 * - /claim delete <name> - Delete one of your claims
 * - /claim list - List all of your claims
 * - /claim info - View information about the claim at your current location
 */
class ClaimCommand : CommandBase("claim", "Manage your land claims"), KoinComponent {

    init {
        // Add all subcommands
        addSubCommand(ClaimCreateCommand())
        addSubCommand(ClaimDeleteCommand())
        addSubCommand(ClaimListCommand())
        addSubCommand(ClaimInfoCommand())
        addSubCommand(ClaimTrustCommand())
        addSubCommand(ClaimUntrustCommand())

        // Base permission
        requirePermission("lumaguilds.claim")
    }

    override fun executeSync(context: CommandContext) {
        // Show help/usage when no subcommand is provided
        context.sendMessage(
            Message.raw("=== ")
                .insert(Message.raw("LumaGuilds Claims").color("gold").bold(true))
                .insert(" ===\n")
                .insert("Available commands:\n")
                .insert(Message.raw("/claim create <name>").color("yellow"))
                .insert(" - Create a new claim\n")
                .insert(Message.raw("/claim delete <name>").color("yellow"))
                .insert(" - Delete a claim\n")
                .insert(Message.raw("/claim list").color("yellow"))
                .insert(" - List your claims\n")
                .insert(Message.raw("/claim info").color("yellow"))
                .insert(" - View claim info at your location\n")
                .insert(Message.raw("/claim trust <player>").color("yellow"))
                .insert(" - Trust a player in your claim\n")
                .insert(Message.raw("/claim untrust <player>").color("yellow"))
                .insert(" - Untrust a player in your claim")
        )
    }
}

/**
 * /claim create <name>
 * Creates a new claim at the player's current location.
 */
class ClaimCreateCommand : CommandBase("create", "Create a new claim"), KoinComponent {

    private val createClaim: CreateClaim by inject()
    private val claimNameArg = withRequiredArg("name", "The claim name", ArgTypes.STRING)

    init {
        requirePermission("lumaguilds.claim.create")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can create claims!").color("red"))
            return
        }

        val player = context.sender()
        val playerId = player.uuid
        val claimName = context.get(claimNameArg)

        // Get player's current position using Universe
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(playerId)
        if (playerRef == null) {
            context.sendMessage(Message.raw("Failed to get your position!").color("red"))
            return
        }

        val transform = playerRef.getTransform()
        val position = Position3D(
            transform.getPosition().x.toInt(),
            transform.getPosition().y.toInt(),
            transform.getPosition().z.toInt()
        )

        val worldId = playerRef.getWorldUuid()
        if (worldId == null) {
            context.sendMessage(Message.raw("Failed to determine your world!").color("red"))
            return
        }

        try {
            // Create the claim using CreateClaim action
            when (val result = createClaim.execute(playerId, claimName, position, worldId)) {
                is CreateClaimResult.Success -> {
                    val claim = result.claim
                    ClaimSounds.playClaimCreate(playerRef)
                    context.sendMessage(
                        Message.raw("✓ Claim ")
                            .color("green")
                            .insert(Message.raw(claim.name).color("gold").bold(true))
                            .insert(Message.raw(" created successfully!").color("green"))
                    )
                    context.sendMessage(
                        Message.raw("Your land is now protected at: ")
                            .color("gray")
                            .insert("${position.x}, ${position.y}, ${position.z}")
                    )
                }
                is CreateClaimResult.LimitExceeded -> {
                    ClaimSounds.playClaimViolationUI(playerRef)
                    context.sendMessage(
                        Message.raw("You have reached your claim limit!")
                            .color("red")
                    )
                }
                is CreateClaimResult.NameCannotBeBlank -> {
                    ClaimSounds.playClaimViolationUI(playerRef)
                    context.sendMessage(
                        Message.raw("Claim name cannot be blank!")
                            .color("red")
                    )
                }
                is CreateClaimResult.NameAlreadyExists -> {
                    ClaimSounds.playClaimViolationUI(playerRef)
                    context.sendMessage(
                        Message.raw("You already have a claim named \"$claimName\"!")
                            .color("red")
                    )
                }
                is CreateClaimResult.TooCloseToWorldBorder -> {
                    ClaimSounds.playClaimViolationUI(playerRef)
                    context.sendMessage(
                        Message.raw("Cannot create claim too close to world border!")
                            .color("red")
                    )
                }
            }
        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to create claim: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /claim delete <name>
 * Deletes one of the player's claims by name.
 */
class ClaimDeleteCommand : CommandBase("delete", "Delete a claim"), KoinComponent {

    private val claimRepository: ClaimRepository by inject()
    private val partitionRepository: PartitionRepository by inject()
    private val claimNameArg = withRequiredArg("name", "The claim name", ArgTypes.STRING)

    init {
        requirePermission("lumaguilds.claim.delete")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can delete claims!").color("red"))
            return
        }

        val playerId = context.sender().uuid
        val claimName = context.get(claimNameArg)

        // Get PlayerRef for sound playback
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(playerId)

        try {
            // Find the claim by name
            val claim = claimRepository.getByName(playerId, claimName)

            if (claim == null) {
                if (playerRef != null) ClaimSounds.playClaimViolationUI(playerRef)
                context.sendMessage(
                    Message.raw("You don't have a claim named \"$claimName\"!")
                        .color("red")
                )
                return
            }

            // Delete all partitions for this claim
            val partitions = partitionRepository.getByClaim(claim.id)
            partitions.forEach { partition: Partition ->
                partitionRepository.remove(partition.id)
            }

            // Delete the claim
            val success = claimRepository.remove(claim.id)

            if (success) {
                if (playerRef != null) ClaimSounds.playClaimDelete(playerRef)
                context.sendMessage(
                    Message.raw("✓ Claim ")
                        .color("green")
                        .insert(Message.raw(claimName).color("gold").bold(true))
                        .insert(Message.raw(" deleted successfully!").color("green"))
                )
            } else {
                if (playerRef != null) ClaimSounds.playClaimViolationUI(playerRef)
                context.sendMessage(
                    Message.raw("Failed to delete claim!")
                        .color("red")
                )
            }
        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to delete claim: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /claim list
 * Lists all claims owned by the player.
 */
class ClaimListCommand : CommandBase("list", "List your claims"), KoinComponent {

    private val listPlayerClaims: ListPlayerClaims by inject()

    init {
        requirePermission("lumaguilds.claim.list")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can list claims!").color("red"))
            return
        }

        val playerId = context.sender().uuid

        try {
            val claims = listPlayerClaims.execute(playerId)

            if (claims.isEmpty()) {
                context.sendMessage(
                    Message.raw("You don't have any claims yet!")
                        .color("yellow")
                )
                context.sendMessage(
                    Message.raw("Use ")
                        .color("gray")
                        .insert(Message.raw("/claim create <name>").color("yellow"))
                        .insert(Message.raw(" to create one!").color("gray"))
                )
                return
            }

            // Build claims list message
            val message = Message.raw("=== ")
                .insert(Message.raw("Your Claims").color("gold").bold(true))
                .insert(" (${claims.size}) ===\n")

            claims.forEachIndexed { index, claim ->
                message.insert(Message.raw("${index + 1}. ").color("gray"))
                    .insert(Message.raw(claim.name).color("yellow").bold(true))
                    .insert(" - ${claim.position.x}, ${claim.position.y}, ${claim.position.z}")

                if (index < claims.size - 1) {
                    message.insert("\n")
                }
            }

            context.sendMessage(message)

        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to list claims: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /claim info
 * Displays information about the claim at the player's current location.
 */
class ClaimInfoCommand : CommandBase("info", "View claim info"), KoinComponent {

    private val getClaimAtPosition: GetClaimAtPosition by inject()

    init {
        requirePermission("lumaguilds.claim.info")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can view claim info!").color("red"))
            return
        }

        val player = context.sender()
        val playerId = player.uuid

        // Get player's current position using Universe
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(playerId)
        if (playerRef == null) {
            context.sendMessage(Message.raw("Failed to get your position!").color("red"))
            return
        }

        val transform = playerRef.getTransform()
        val position = Position(
            transform.getPosition().x.toInt(),
            transform.getPosition().y.toInt(),
            transform.getPosition().z.toInt()
        )

        val worldId = playerRef.getWorldUuid()
        if (worldId == null) {
            context.sendMessage(Message.raw("Failed to determine your world!").color("red"))
            return
        }

        try {
            when (val result = getClaimAtPosition.execute(worldId, position)) {
                is GetClaimAtPositionResult.Success -> {
                    val claim = result.claim
                    ClaimSounds.playClaimInfo(playerRef)

                    // Build claim info message
                    val message = Message.raw("=== ")
                        .insert(Message.raw("Claim Info").color("gold").bold(true))
                        .insert(" ===\n")
                        .insert(Message.raw("Name: ").color("gray"))
                        .insert(Message.raw(claim.name).color("yellow").bold(true))
                        .insert("\n")
                        .insert(Message.raw("Position: ").color("gray"))
                        .insert("${claim.position.x}, ${claim.position.y}, ${claim.position.z}\n")
                        .insert(Message.raw("Owner: ").color("gray"))

                    // Get owner name from player service
                    // For now, just show UUID (TODO: get player name)
                    message.insert(claim.playerId.toString())

                    context.sendMessage(message)
                }
                is GetClaimAtPositionResult.NoClaimFound -> {
                    context.sendMessage(
                        Message.raw("There is no claim at this location!")
                            .color("yellow")
                    )
                }
                is GetClaimAtPositionResult.StorageError -> {
                    ClaimSounds.playClaimViolationUI(playerRef)
                    context.sendMessage(
                        Message.raw("Error accessing storage!")
                            .color("red")
                    )
                }
            }
        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to get claim info: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /claim trust <player>
 * Trusts a player to build in the claim at your current location.
 */
class ClaimTrustCommand : CommandBase("trust", "Trust a player in your claim"), KoinComponent {

    private val getClaimAtPosition: GetClaimAtPosition by inject()
    private val claimRepository: ClaimRepository by inject()
    private val playerNameArg = withRequiredArg("player", "The player to trust", ArgTypes.STRING)

    init {
        requirePermission("lumaguilds.claim.trust")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can trust others in claims!").color("red"))
            return
        }

        val player = context.sender()
        val playerId = player.uuid
        val playerName = context.get(playerNameArg)

        // Get player's current position using Universe
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(playerId)
        if (playerRef == null) {
            context.sendMessage(Message.raw("Failed to get your position!").color("red"))
            return
        }

        val transform = playerRef.getTransform()
        val position = Position(
            transform.getPosition().x.toInt(),
            transform.getPosition().y.toInt(),
            transform.getPosition().z.toInt()
        )

        val worldId = playerRef.getWorldUuid()
        if (worldId == null) {
            context.sendMessage(Message.raw("Failed to determine your world!").color("red"))
            return
        }

        try {
            when (val result = getClaimAtPosition.execute(worldId, position)) {
                is GetClaimAtPositionResult.Success -> {
                    val claim = result.claim

                    // Check if player is the owner
                    if (claim.playerId != playerId) {
                        ClaimSounds.playClaimViolationUI(playerRef)
                        context.sendMessage(
                            Message.raw("You don't own this claim!")
                                .color("red")
                        )
                        return
                    }

                    // Find the target player by username
                    val targetPlayerRef = universe.getPlayerByUsername(playerName, com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE)
                    if (targetPlayerRef == null) {
                        ClaimSounds.playClaimViolationUI(playerRef)
                        context.sendMessage(
                            Message.raw("Player '$playerName' not found!")
                                .color("red")
                        )
                        return
                    }

                    val targetPlayerId = targetPlayerRef.getUuid()

                    // Check if already trusted
                    if (claim.trustedPlayers.contains(targetPlayerId)) {
                        context.sendMessage(
                            Message.raw("$playerName is already trusted in this claim!")
                                .color("yellow")
                        )
                        return
                    }

                    // Trust the player
                    claim.trustPlayer(targetPlayerId)
                    claimRepository.update(claim)

                    ClaimSounds.playSuccess(playerRef)
                    context.sendMessage(
                        Message.raw("✓ ").color("green")
                            .insert(Message.raw(playerName).color("gold").bold(true))
                            .insert(Message.raw(" is now trusted in ").color("green"))
                            .insert(Message.raw(claim.name).color("gold").bold(true))
                    )
                }
                is GetClaimAtPositionResult.NoClaimFound -> {
                    context.sendMessage(
                        Message.raw("There is no claim at this location!")
                            .color("yellow")
                    )
                }
                is GetClaimAtPositionResult.StorageError -> {
                    ClaimSounds.playClaimViolationUI(playerRef)
                    context.sendMessage(
                        Message.raw("Error accessing storage!")
                            .color("red")
                    )
                }
            }
        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to trust player: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}

/**
 * /claim untrust <player>
 * Untrusts a player from building in the claim at your current location.
 */
class ClaimUntrustCommand : CommandBase("untrust", "Untrust a player in your claim"), KoinComponent {

    private val getClaimAtPosition: GetClaimAtPosition by inject()
    private val claimRepository: ClaimRepository by inject()
    private val playerNameArg = withRequiredArg("player", "The player to untrust", ArgTypes.STRING)

    init {
        requirePermission("lumaguilds.claim.untrust")
    }

    override fun executeSync(context: CommandContext) {
        if (!context.isPlayer) {
            context.sendMessage(Message.raw("Only players can untrust others in claims!").color("red"))
            return
        }

        val player = context.sender()
        val playerId = player.uuid
        val playerName = context.get(playerNameArg)

        // Get player's current position using Universe
        val universe = com.hypixel.hytale.server.core.universe.Universe.get()
        val playerRef = universe.getPlayer(playerId)
        if (playerRef == null) {
            context.sendMessage(Message.raw("Failed to get your position!").color("red"))
            return
        }

        val transform = playerRef.getTransform()
        val position = Position(
            transform.getPosition().x.toInt(),
            transform.getPosition().y.toInt(),
            transform.getPosition().z.toInt()
        )

        val worldId = playerRef.getWorldUuid()
        if (worldId == null) {
            context.sendMessage(Message.raw("Failed to determine your world!").color("red"))
            return
        }

        try {
            when (val result = getClaimAtPosition.execute(worldId, position)) {
                is GetClaimAtPositionResult.Success -> {
                    val claim = result.claim

                    // Check if player is the owner
                    if (claim.playerId != playerId) {
                        ClaimSounds.playClaimViolationUI(playerRef)
                        context.sendMessage(
                            Message.raw("You don't own this claim!")
                                .color("red")
                        )
                        return
                    }

                    // Find the target player by username
                    val targetPlayerRef = universe.getPlayerByUsername(playerName, com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE)
                    if (targetPlayerRef == null) {
                        ClaimSounds.playClaimViolationUI(playerRef)
                        context.sendMessage(
                            Message.raw("Player '$playerName' not found!")
                                .color("red")
                        )
                        return
                    }

                    val targetPlayerId = targetPlayerRef.getUuid()

                    // Check if actually trusted
                    if (!claim.trustedPlayers.contains(targetPlayerId)) {
                        context.sendMessage(
                            Message.raw("$playerName is not trusted in this claim!")
                                .color("yellow")
                        )
                        return
                    }

                    // Untrust the player
                    claim.untrustPlayer(targetPlayerId)
                    claimRepository.update(claim)

                    ClaimSounds.playSuccess(playerRef)
                    context.sendMessage(
                        Message.raw("✓ ").color("green")
                            .insert(Message.raw(playerName).color("gold").bold(true))
                            .insert(Message.raw(" is no longer trusted in ").color("green"))
                            .insert(Message.raw(claim.name).color("gold").bold(true))
                    )
                }
                is GetClaimAtPositionResult.NoClaimFound -> {
                    context.sendMessage(
                        Message.raw("There is no claim at this location!")
                            .color("yellow")
                    )
                }
                is GetClaimAtPositionResult.StorageError -> {
                    ClaimSounds.playClaimViolationUI(playerRef)
                    context.sendMessage(
                        Message.raw("Error accessing storage!")
                            .color("red")
                    )
                }
            }
        } catch (e: Exception) {
            context.sendMessage(
                Message.raw("Failed to untrust player: ${e.message}")
                    .color("red")
            )
            e.printStackTrace()
        }
    }
}
