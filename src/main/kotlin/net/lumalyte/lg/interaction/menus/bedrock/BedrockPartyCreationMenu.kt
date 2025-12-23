package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.util.UUID
import java.util.logging.Logger

/**
 * Bedrock Edition party creation menu using Cumulus CustomForm
 * Allows creating parties with name and type (private/public)
 */
class BedrockPartyCreationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val partyService: PartyService by inject()
    private val configService: ConfigService by inject()
    private val guildRepository: GuildRepository by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val partyIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        // Check if parties are enabled
        val mainConfig = configService.loadConfig()
        if (!mainConfig.partiesEnabled) {
            return CustomForm.builder()
                .title(bedrockLocalization.getBedrockString(player, "guild.party.create.title"))
                .label(bedrockLocalization.getBedrockString(player, "guild.party.disabled"))
                .validResultHandler { _ ->
                    bedrockNavigator.goBack()
                }
                .build()
        }

        // Get list of guilds for multi-guild parties
        val allGuilds = guildRepository.getAll().filter { it.id != guild.id }
        val guildNames = allGuilds.map { it.name }

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.party.create.title")} - ${guild.name}")
            .apply { partyIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.party.create.description"))
            .input(
                bedrockLocalization.getBedrockString(player, "guild.party.name"),
                bedrockLocalization.getBedrockString(player, "guild.party.name.placeholder"),
                ""
            )
            .toggle(
                bedrockLocalization.getBedrockString(player, "guild.party.private"),
                mainConfig.party.allowPrivateParties
            )
            .apply {
                if (guildNames.isNotEmpty()) {
                    label(bedrockLocalization.getBedrockString(player, "guild.party.invite.other"))
                    dropdown(
                        bedrockLocalization.getBedrockString(player, "guild.party.select.guild"),
                        listOf(bedrockLocalization.getBedrockString(player, "guild.party.none")) + guildNames
                    )
                }
            }
            .validResultHandler { response ->
                val partyName = response.asInput(1)?.trim() ?: ""
                val isPrivate = response.asToggle(2)

                // Get selected guild if any
                val selectedGuildIndex = if (guildNames.isNotEmpty()) response.asDropdown(4) else 0
                val selectedGuild = if (selectedGuildIndex > 0 && guildNames.isNotEmpty()) {
                    allGuilds.getOrNull(selectedGuildIndex - 1)
                } else null

                handlePartyCreation(partyName, isPrivate, selectedGuild)
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handlePartyCreation(partyName: String, isPrivate: Boolean, invitedGuild: Guild?) {
        // Validate party name
        if (partyName.length > 32) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.name.too.long"))
            bedrockNavigator.goBack()
            return
        }

        // Build guild set
        val guildIds = mutableSetOf(guild.id)
        if (!isPrivate && invitedGuild != null) {
            guildIds.add(invitedGuild.id)
        }

        // Validate party name (no spaces allowed)
        if (partyName.contains(" ")) {
            player.sendMessage("§c❌ Party names cannot contain spaces!")
            player.sendMessage("§7Use underscores instead (e.g., My_Party)")
            bedrockNavigator.goBack()
            return
        }

        // Check if we have enough guilds for public party
        if (!isPrivate && guildIds.size < 2) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.need.guilds"))
            bedrockNavigator.goBack()
            return
        }

        // Create the party
        val partyConfig = configService.loadConfig().party
        val expiresAt = java.time.Instant.now().plus(Duration.ofHours(partyConfig.defaultPartyDurationHours.toLong()))

        val party = Party(
            id = UUID.randomUUID(),
            name = partyName.ifBlank { null },
            guildIds = guildIds,
            leaderId = player.uniqueId,
            status = PartyStatus.ACTIVE,
            createdAt = java.time.Instant.now(),
            expiresAt = expiresAt,
            restrictedRoles = null
        )

        val createdParty = partyService.createParty(party)

        if (createdParty != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.created.success"))
            if (partyName.isNotBlank()) {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.name.set", partyName))
            }
            if (isPrivate) {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.private.created"))
            } else if (invitedGuild != null) {
                player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.invited", invitedGuild.name))
            }
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.party.created.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
