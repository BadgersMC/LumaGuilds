package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.logging.Logger

/**
 * Bedrock Edition rank edit menu using Cumulus CustomForm
 * Allows editing an existing rank's name and permissions
 */
class BedrockRankEditMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val rank: Rank,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val rankService: RankService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val rankIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.rank.edit.title")} - ${rank.name}")
            .apply { rankIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.rank.edit.description"))
            .input(
                bedrockLocalization.getBedrockString(player, "guild.rank.name"),
                bedrockLocalization.getBedrockString(player, "guild.rank.name.placeholder"),
                rank.name
            )
            .label(bedrockLocalization.getBedrockString(player, "guild.rank.permissions.basic"))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.invite"), rank.permissions.contains(RankPermission.MANAGE_MEMBERS))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.kick"), rank.permissions.contains(RankPermission.MANAGE_MEMBERS))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.promote"), rank.permissions.contains(RankPermission.MANAGE_MEMBERS))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.manage.ranks"), rank.permissions.contains(RankPermission.MANAGE_RANKS))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.manage.settings"), rank.permissions.contains(RankPermission.MANAGE_GUILD_SETTINGS))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.declare.war"), rank.permissions.contains(RankPermission.DECLARE_WAR))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.manage.bank"), rank.permissions.contains(RankPermission.MANAGE_BANK_SETTINGS))
            .toggle(bedrockLocalization.getBedrockString(player, "guild.rank.perm.access.vault"), rank.permissions.contains(RankPermission.ACCESS_VAULT))
            .validResultHandler { response ->
                val newName = response.asInput(1)?.trim() ?: rank.name
                val canInvite = response.asToggle(3)
                val canKick = response.asToggle(4)
                val canPromote = response.asToggle(5)
                val canManageRanks = response.asToggle(6)
                val canManageSettings = response.asToggle(7)
                val canDeclareWar = response.asToggle(8)
                val canManageBank = response.asToggle(9)
                val canAccessVault = response.asToggle(10)

                handleRankUpdate(
                    newName,
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

    private fun handleRankUpdate(
        newName: String,
        canInvite: Boolean,
        canKick: Boolean,
        canPromote: Boolean,
        canManageRanks: Boolean,
        canManageSettings: Boolean,
        canDeclareWar: Boolean,
        canManageBank: Boolean,
        canAccessVault: Boolean
    ) {
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

        // Update rank permissions
        val permSuccess = rankService.setRankPermissions(rank.id, permissions, player.uniqueId)

        // Update name if changed
        var nameSuccess = true
        if (newName != rank.name && newName.isNotEmpty()) {
            nameSuccess = rankService.renameRank(rank.id, newName, player.uniqueId)
        }

        if (permSuccess && nameSuccess) {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.rank.updated.success"))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(bedrockLocalization.getBedrockString(player, "guild.rank.updated.failed"))
            bedrockNavigator.goBack()
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // Handled in the form result handler
        onFormResponseReceived()
    }
}
