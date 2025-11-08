package net.lumalyte.lg.interaction.menus.bedrock

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
            .title("🏰 Select Guilds - Page ${currentPage + 1}/${maxOf(1, totalPages)}")
            .content("""
                |Select guilds to invite to the party.
                |
                |📊 Selected: ${selectedGuilds.size} guilds
                |📄 Showing: ${startIndex + 1}-${endIndex} of ${allGuilds.size}
                |
                |✅ Selected guilds will be invited when the party is created.
            """.trimMargin())
            .apply {
                // Add guild selection buttons
                for (guild in pageGuilds) {
                    val isSelected = selectedGuilds.contains(guild.id)
                    val memberCount = memberService.getGuildMembers(guild.id).size
                    val statusText = if (isSelected) "✅ SELECTED" else "❌ AVAILABLE"
                    val actionText = if (isSelected) "Remove from selection" else "Add to selection"

                    button("""
                        |🏰 ${guild.name}
                        |👥 Members: $memberCount
                        |📊 Status: $statusText
                        |💡 $actionText
                    """.trimMargin())
                }

                // Add navigation buttons (only if needed)
                if (currentPage > 0) {
                    button("⬅ Previous Page")
                }
                if (currentPage < totalPages - 1) {
                    button("➡ Next Page")
                }

                // Add summary and action buttons
                button("📋 View Selected (${selectedGuilds.size})")
                button("✅ Done - Create Party")
                button("❌ Cancel")
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
                        player.sendMessage("§c❌ Guild selection cancelled.")
                    }.run()
                }
            }
            .closedOrInvalidResultHandler(bedrockNavigator.createBackHandler {
                player.sendMessage("§c❌ Guild selection cancelled.")
            })
            .build()
    }

    private fun toggleGuildSelection(guild: Guild) {
        if (selectedGuilds.contains(guild.id)) {
            // Remove from selection
            selectedGuilds.remove(guild.id)
            player.sendMessage("§c❌ Removed ${guild.name} from party invitation")
        } else {
            // Add to selection
            selectedGuilds.add(guild.id)
            player.sendMessage("§a✅ Added ${guild.name} to party invitation")
        }
        // Stay on the same page to continue selecting
        open()
    }

    private fun showSelectedSummary() {
        if (selectedGuilds.isEmpty()) {
            player.sendMessage("§e📋 No guilds selected yet")
            player.sendMessage("§7Use the buttons above to select guilds to invite")
            open()
            return
        }

        val config = getBedrockConfig()
        val summaryForm = SimpleForm.builder()
            .title("📋 Selected Guilds")
            .content("""
                |Selected guilds for party invitation:
                |
                |${selectedGuilds.joinToString("\n") { guildId ->
                    val guild = guildService.getGuild(guildId)
                    "🏰 ${guild?.name ?: "Unknown Guild"}"
                }}
                |
                |Total: ${selectedGuilds.size} guilds
            """.trimMargin())
            .addButtonWithImage(
                config,
                "✅ Continue Selecting",
                config.confirmIconUrl,
                config.confirmIconPath
            )
            .addButtonWithImage(
                config,
                "🚀 Create Party",
                config.editIconUrl,
                config.editIconPath
            )
            .addButtonWithImage(
                config,
                "🔄 Clear All",
                config.cancelIconUrl,
                config.cancelIconPath
            )
            .validResultHandler { response ->
                when (response.clickedButtonId()) {
                    0 -> bedrockNavigator.createRefreshHandler(this@BedrockGuildSelectionMenu).run() // Continue selecting
                    1 -> createParty() // Create party
                    2 -> {
                        selectedGuilds.clear()
                        player.sendMessage("§c🗑 Cleared all guild selections")
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
            player.sendMessage("§c❌ No guilds selected!")
            player.sendMessage("§7Please select at least one guild to invite")
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
