package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.PartyCreationMenu
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.floodgate.api.FloodgateApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition guild selection menu using Cumulus SimpleForm
 * Allows selecting multiple guilds for party invitations with pagination support
 */
class BedrockGuildSelectionMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val currentGuild: Guild,
    private val selectedGuilds: MutableSet<java.util.UUID>,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    private val itemsPerPage = 8 // Limit for SimpleForm buttons
    private var currentPage = 0

    override fun getForm(): Form {
        val allGuilds = guildService.getAllGuilds()
            .filter { it.id != currentGuild.id } // Exclude current guild
            .sortedBy { it.name }

        val totalPages = (allGuilds.size + itemsPerPage - 1) / itemsPerPage
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, allGuilds.size)
        val pageGuilds = allGuilds.subList(startIndex, endIndex)

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.party.guild_selection.title", "page" to currentPage + 1, "total_pages" to maxOf(1, totalPages)))
            .content(lang.bedrock(
                "bedrock.party.guild_selection.content",
                "selected" to selectedGuilds.size,
                "start" to startIndex + 1,
                "end" to endIndex,
                "total" to allGuilds.size,
            ))
            .apply {
                // Add guild selection buttons
                for (guild in pageGuilds) {
                    val isSelected = selectedGuilds.contains(guild.id)
                    val memberCount = memberService.getGuildMembers(guild.id).size
                    val statusText = if (isSelected) {
                        lang.bedrock("bedrock.party.guild_selection.status.selected")
                    } else {
                        lang.bedrock("bedrock.party.guild_selection.status.available")
                    }
                    val actionText = if (isSelected) {
                        lang.bedrock("bedrock.party.guild_selection.action.remove")
                    } else {
                        lang.bedrock("bedrock.party.guild_selection.action.add")
                    }

                    button(lang.bedrock(
                        "bedrock.party.guild_selection.guild_button",
                        "guild" to guild.name,
                        "members" to memberCount,
                        "status" to statusText,
                        "action" to actionText,
                    ))
                }

                // Add navigation buttons (only if needed)
                if (currentPage > 0) {
                    button(lang.bedrock("bedrock.party.guild_selection.button.previous"))
                }
                if (currentPage < totalPages - 1) {
                    button(lang.bedrock("bedrock.party.guild_selection.button.next"))
                }

                // Add summary and action buttons
                button(lang.bedrock("bedrock.party.guild_selection.button.view_selected", "count" to selectedGuilds.size))
                button(lang.bedrock("bedrock.party.guild_selection.button.done"))
                button(lang.bedrock("bedrock.party.guild_selection.button.cancel"))
            }
            .validResultHandler { response ->
                val clickedIndex = response.clickedButtonId()

                // Calculate button positions dynamically
                var currentButtonIndex = 0

                // Guild selection buttons
                if (clickedIndex < pageGuilds.size) {
                    val selectedGuild = pageGuilds[clickedIndex]
                    toggleGuildSelection(selectedGuild)
                    return@validResultHandler
                }
                currentButtonIndex += pageGuilds.size

                // Navigation buttons
                if (currentPage > 0) {
                    if (clickedIndex == currentButtonIndex) {
                        currentPage--
                        open() // Reopen with new page
                        return@validResultHandler
                    }
                    currentButtonIndex++
                }

                if (currentPage < totalPages - 1) {
                    if (clickedIndex == currentButtonIndex) {
                        currentPage++
                        open() // Reopen with new page
                        return@validResultHandler
                    }
                    currentButtonIndex++
                }

                // Summary and action buttons
                when (clickedIndex - currentButtonIndex) {
                    0 -> showSelectedSummary()
                    1 -> createParty()
                    2 -> bedrockNavigator.createBackHandler {
                        player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.cancelled"))
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.cancelled"))
            })
            .build()
    }

    private fun toggleGuildSelection(guild: Guild) {
        if (selectedGuilds.contains(guild.id)) {
            // Remove from selection
            selectedGuilds.remove(guild.id)
            player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.removed", "guild" to guild.name))
        } else {
            // Add to selection
            selectedGuilds.add(guild.id)
            player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.added", "guild" to guild.name))
        }
        // Stay on the same page to continue selecting
        open()
    }

    private fun showSelectedSummary() {
        if (selectedGuilds.isEmpty()) {
            player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.none_selected"))
            player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.selection_hint"))
            open()
            return
        }

        val config = getBedrockConfig()
        val guildRows = selectedGuilds.joinToString("\n") { guildId ->
            val guild = guildService.getGuild(guildId)
            lang.bedrock(
                "bedrock.party.guild_selection.summary.guild_row",
                "guild" to (guild?.name ?: lang.bedrock("bedrock.party.guild_selection.unknown_guild")),
            )
        }
        val summaryForm = SimpleForm.builder()
            .title(lang.bedrock("bedrock.party.guild_selection.summary.title"))
            .content(lang.bedrock(
                "bedrock.party.guild_selection.summary.content",
                "guilds" to guildRows,
                "total" to selectedGuilds.size,
            ))
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.guild_selection.summary.continue"),
                config.confirmIconUrl,
                config.confirmIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.guild_selection.summary.create"),
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.party.guild_selection.summary.clear"),
                config.cancelIconUrl,
                config.cancelIconPath
            )
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> bedrockNavigator.createRefreshHandler(this@BedrockGuildSelectionMenu).run() // Continue selecting
                    1 -> createParty() // Create party
                    2 -> {
                        selectedGuilds.clear()
                        player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.cleared"))
                        bedrockNavigator.createRefreshHandler(this@BedrockGuildSelectionMenu).run()
                    }
                }
            }
            .build()

        // Send the summary form directly
        val floodgateApi = FloodgateApi.getInstance()
        floodgateApi.sendForm(player.uniqueId, summaryForm)
    }

    private fun createParty() {
        if (selectedGuilds.isEmpty()) {
            player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.no_guilds"))
            player.sendMessage(lang.msg("bedrock.party.guild_selection.feedback.select_one"))
            open()
            return
        }

        // Return to party creation menu with selected guilds
        menuNavigator.openMenu(menuFactory.createPartyCreationMenu(menuNavigator, player, currentGuild).apply {
            passData(mapOf(
                "selectedGuilds" to selectedGuilds.toSet(),
                "partyName" to "", // Will be handled by the creation menu
                "restrictedRoles" to setOf<java.util.UUID>() // Will be handled by the creation menu
            ))
        })
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Response handling is done in the form builder's validResultHandler
        // This method is kept for interface compatibility
    }
}
