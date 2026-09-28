package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

/** Bedrock rank creation backed by the complete current RankPermission model. */
class BedrockRankCreationMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {
    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val authorization by lazy { BedrockGuildAuthorization(guildService) }
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()

    override fun getForm(): Form {
        if (!authorization.canManageRanks(player.uniqueId, guild.id)) {
            return noPermissionForm()
        }

        val permissions = availablePermissions()
        val config = getBedrockConfig()
        val rankIcon = BedrockFormUtils.createFormImage(
            config,
            config.guildSettingsIconUrl,
            config.guildSettingsIconPath
        )
        val builder = CustomForm.builder()
            .title(lang.bedrock("bedrock.rank_creation.title", "guild" to guild.name))
            .apply { rankIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.rank_creation.description"))
            .input(
                lang.bedrock("bedrock.rank_creation.name.label"),
                lang.bedrock("bedrock.rank_creation.name.placeholder"),
                ""
            )
            .label(lang.bedrock("bedrock.rank_creation.permissions.header"))

        RankPermission.entries
            .filter { it in permissions }
            .forEach { permission ->
                builder.toggle(
                    permissionLabel(permission),
                    permission == RankPermission.ACCESS_VAULT
                )
            }

        return builder
            .validResultHandler { response ->
                val rankName = (response.next() as? String).orEmpty().trim()
                val selected = mutableSetOf<RankPermission>()
                permissions.forEach { permission ->
                    if (response.next() as? Boolean == true) selected += permission
                }
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) handleRankCreation(rankName, selected)
                })
            }
            .closedOrInvalidResultHandler { _, _ ->
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (player.isOnline) bedrockNavigator.goBack()
                })
            }
            .build()
    }

    private fun handleRankCreation(rankName: String, permissions: Set<RankPermission>) {
        onFormResponseReceived()
        if (!authorization.canManageRanks(player.uniqueId, guild.id)) {
            player.sendMessage(lang.msg("bedrock.rank_management.error.no_permission"))
            bedrockNavigator.goBack()
            return
        }
        if (rankName.isEmpty()) {
            player.sendMessage(lang.msg("bedrock.rank_creation.feedback.empty_name"))
            open()
            return
        }
        if (rankName.length > 20) {
            player.sendMessage(lang.msg("bedrock.rank_creation.feedback.name_too_long"))
            open()
            return
        }

        val rank = rankService.addRank(guild.id, rankName, permissions, player.uniqueId)
        if (rank != null) {
            player.sendMessage(lang.msg("bedrock.rank_creation.feedback.created", "rank" to rankName))
            bedrockNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("bedrock.rank_creation.feedback.failed"))
            open()
        }
    }

    private fun availablePermissions(): List<RankPermission> {
        val claimsEnabled = configService.loadConfig().claimsEnabled
        return RankPermission.entries.filterNot { !claimsEnabled && it in CLAIM_PERMISSIONS }
    }

    private fun permissionLabel(permission: RankPermission): String =
        BedrockGuildRankManagementMenu.permissionDisplayName(lang, permission)

    private fun noPermissionForm(): Form = SimpleForm.builder()
        .title(lang.bedrock("bedrock.rank_creation.title", "guild" to guild.name))
        .content(lang.bedrock("bedrock.rank_management.error.no_permission"))
        .button(lang.bedrock("bedrock.rank_management.back"))
        .validResultHandler { bedrockNavigator.goBack() }
        .closedOrInvalidResultHandler { _, _ -> bedrockNavigator.goBack() }
        .build()

    override fun shouldCacheForm(): Boolean = false
    override fun handleResponse(player: Player, response: Any?) = Unit

    companion object {
        private val CLAIM_PERMISSIONS = setOf(
            RankPermission.MANAGE_CLAIMS,
            RankPermission.MANAGE_FLAGS,
            RankPermission.MANAGE_PERMISSIONS,
            RankPermission.CREATE_CLAIMS,
            RankPermission.DELETE_CLAIMS
        )
    }
}
