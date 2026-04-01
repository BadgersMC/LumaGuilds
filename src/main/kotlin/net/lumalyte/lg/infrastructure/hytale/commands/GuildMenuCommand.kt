package net.lumalyte.lg.infrastructure.hytale.commands

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.infrastructure.hytale.ui.GuildControlPanel
import org.koin.java.KoinJavaComponent
import javax.annotation.Nonnull

/**
 * Command to open the Guild Control Panel GUI
 *
 * Usage: /guild menu
 *
 * Opens Hytale's native UI control panel for guild management
 */
class GuildMenuCommand : AbstractPlayerCommand("menu", "Open the guild control panel") {

    override fun canGeneratePermission(): Boolean {
        return true
    }

    override fun execute(
        @Nonnull context: CommandContext,
        @Nonnull store: Store<EntityStore>,
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull playerRef: PlayerRef,
        @Nonnull world: World
    ) {
        // Check if player is in a guild
        val guildService = KoinJavaComponent.get<GuildService>(GuildService::class.java)
        val guilds = guildService.getPlayerGuilds(playerRef.uuid)

        if (guilds.isEmpty()) {
            context.sendMessage(Message.raw("You must be in a guild to use the guild menu!").color("red"))
            context.sendMessage(Message.raw("Use /guild create <name> to create a guild or /guild join <guild> to join one.").color("yellow"))
            return
        }

        // Get Player component to access PageManager
        val player = store.getComponent(ref, Player.getComponentType())
        if (player == null) {
            context.sendMessage(Message.raw("Error: Could not get player component").color("red"))
            return
        }

        // Create and open the guild control panel
        val controlPanel = GuildControlPanel(playerRef)
        player.pageManager.openCustomPage(ref, store, controlPanel)
    }
}
