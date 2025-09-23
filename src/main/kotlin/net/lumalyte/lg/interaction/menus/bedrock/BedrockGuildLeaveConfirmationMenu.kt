package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildControlPanelMenu
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild leave confirmation menu using Cumulus SimpleForm
 */
class BedrockGuildLeaveConfirmationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    override fun getForm(): Form {
        return SimpleForm.builder()
            .title(bedrockLocalization.getBedrockString(player, "guild.leave.confirm.title"))
            .content(bedrockLocalization.getBedrockString(player, "guild.leave.confirm.message", guild.name, player.name))
            .button(bedrockLocalization.getBedrockString(player, "guild.leave.confirm.button.leave"))
            .button(bedrockLocalization.getBedrockString(player, "guild.leave.confirm.button.stay"))
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> leaveGuild()
                    1 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.leave.confirm.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.leave.confirm.cancelled"))
            })
            .build()
    }

    private fun leaveGuild() {
        // Check if player is the owner
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val ownerRank = rankService.getHighestRank(guild.id)
        val isOwner = playerRank?.id == ownerRank?.id

        if (isOwner) {
            // If owner is leaving, we need special handling
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.leave.owner.warning"))
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.leave.owner.message"))
            bedrockNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
            return
        }

        // Attempt to leave the guild
        val success = memberService.removeMember(player.uniqueId, guild.id, player.uniqueId)

        if (success) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.leave.success", guild.name))
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.leave.success.details"))

            // Clear menu stack since player is no longer in guild
            clearMenuStack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.leave.failed"))
            bedrockNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
        onFormResponseReceived()
    }

    override fun createFallbackJavaMenu(): net.lumalyte.lg.interaction.menus.Menu? {
        return try {
            // Import the Java guild leave confirmation menu
            val javaMenuClass = Class.forName("net.lumalyte.lg.interaction.menus.guild.GuildLeaveConfirmationMenu")
            val constructor = javaMenuClass.getConstructor(
                net.lumalyte.lg.interaction.menus.MenuNavigator::class.java,
                org.bukkit.entity.Player::class.java
            )
            constructor.newInstance(menuNavigator, player) as net.lumalyte.lg.interaction.menus.Menu
        } catch (e: Exception) {
            logger.warning("Failed to create Java fallback menu for guild leave confirmation: ${e.message}")
            null
        }
    }
}


