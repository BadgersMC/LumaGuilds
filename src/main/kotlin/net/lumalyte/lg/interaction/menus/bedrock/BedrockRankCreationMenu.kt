package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition rank creation menu using Cumulus CustomForm
 * Allows creating a new rank with name and basic permissions
 */
class BedrockRankCreationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val rankService: RankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val rankIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.rank.create.title")} - ${guild.name}")
            .apply { rankIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.rank.create.description"))
            .input(
                bedrockLocalization.getBedrockString(player, "guild.rank.name"),
                bedrockLocalization.getBedrockString(player, "guild.rank.name.placeholder"),
                ""
            )
            .label(bedrockLocalization.getBedrockString(player, "guild.rank.permissions.basic"))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.invite"), false)
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.kick"), false)
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.promote"), false)
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.manage.ranks"), false)
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.manage.settings"), false)
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.declare.war"), false)
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.manage.bank"), false)
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.access.vault"), true) // Default true
            .validResultHandler { response ->
                val rankName = response.asInput(1)?.trim() ?: ""
                val canInvite = response.asToggle(3)
                val canKick = response.asToggle(4)
                val canPromote = response.asToggle(5)
                val canManageRanks = response.asToggle(6)
                val canManageSettings = response.asToggle(7)
                val canDeclareWar = response.asToggle(8)
                val canManageBank = response.asToggle(9)
                val canAccessVault = response.asToggle(10)

                handleRankCreation(
                    rankName,
                    canInvite,
                    canKick,
                    canPromote,
                    canManageRanks,
                    canManageSettings,
                    canDeclareWar,
                    canManageBank,
                    canAccessVault
                )
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun handleRankCreation(
        rankName: String,
        canInvite: Boolean,
        canKick: Boolean,
        canPromote: Boolean,
        canManageRanks: Boolean,
        canManageSettings: Boolean,
        canDeclareWar: Boolean,
        canManageBank: Boolean,
        canAccessVault: Boolean
    ) {
        // Validate name
        if (rankName.isEmpty()) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.rank.name.empty"))
            bedrockNavigator.goBack()
            return
        }

        if (rankName.length > 20) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.rank.name.too.long"))
            bedrockNavigator.goBack()
            return
        }

        // Build permissions set
        val permissions = mutableSetOf<RankPermission>()
        if (canInvite) permissions.add(RankPermission.MANAGE_MEMBERS)
        if (canKick) permissions.add(RankPermission.MANAGE_MEMBERS)
        if (canPromote) permissions.add(RankPermission.MANAGE_MEMBERS)
        if (canManageRanks) permissions.add(RankPermission.MANAGE_RANKS)
        if (canManageSettings) permissions.add(RankPermission.MANAGE_GUILD_SETTINGS)
        if (canDeclareWar) permissions.add(RankPermission.DECLARE_WAR)
        if (canManageBank) permissions.add(RankPermission.MANAGE_BANK_SETTINGS)
        if (canAccessVault) permissions.add(RankPermission.ACCESS_VAULT)

        // Create rank
        val rank = rankService.addRank(guild.id, rankName, permissions, player.uniqueId)

        if (rank != null) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.rank.created.success", rankName))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.rank.created.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
