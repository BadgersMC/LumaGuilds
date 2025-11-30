package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Bedrock Edition guild information menu using Cumulus CustomForm
 * Displays comprehensive guild details and information
 */
class BedrockGuildInfoMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val infoIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title("${bedrockLocalization.getBedrockString(player, "guild.info.title")} - ${guild.name}")
            .apply { infoIcon?.let { icon(it) } }
            .label(bedrockLocalization.getBedrockString(player, "guild.info.description"))
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.info.overview.header")))
            .label(createOverviewSection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.info.members.header")))
            .label(createMembersSection())
            .label(createSectionHeader(bedrockLocalization.getBedrockString(player, "guild.info.relations.header")))
            .label(createRelationsSection())
            .validResultHandler { response ->
                // Read-only menu, just close
                bedrockNavigator.goBack()
            }
            .closedOrInvalidResultHandler { _, _ ->
                bedrockNavigator.goBack()
            }
            .build()
    }

    private fun createSectionHeader(title: String): String {
        return "§e§l$title"
    }

    private fun createOverviewSection(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val foundedDate = formatter.format(guild.createdAt)

        val description = guild.description ?: bedrockLocalization.getBedrockString(player, "guild.info.no.description")
        val tag = guildService.getTag(guild.id) ?: bedrockLocalization.getBedrockString(player, "guild.info.no.tag")
        val emoji = guildService.getEmoji(guild.id) ?: bedrockLocalization.getBedrockString(player, "guild.info.no.emoji")

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.info.name")}: ${guild.name}
            |${bedrockLocalization.getBedrockString(player, "guild.info.level")}: 1
            |${bedrockLocalization.getBedrockString(player, "guild.info.mode")}: ${guild.mode.name.lowercase().replaceFirstChar { it.uppercase() }}
            |${bedrockLocalization.getBedrockString(player, "guild.info.emoji")}: $emoji
            |${bedrockLocalization.getBedrockString(player, "guild.info.description")}: $description
            |${bedrockLocalization.getBedrockString(player, "guild.info.tag")}: $tag
            |${bedrockLocalization.getBedrockString(player, "guild.info.founded")}: $foundedDate
        """.trimMargin()
    }

    private fun createMembersSection(): String {
        val members = memberService.getGuildMembers(guild.id)
        val totalMembers = members.size
        val onlineMembers = members.count { member ->
            try {
                player.server.getPlayer(member.playerId)?.isOnline == true
            } catch (e: Exception) {
                // Menu operation - catching all exceptions to prevent UI failure
            // Menu operation - catching all exceptions to prevent UI failure
                false
            }
        }

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.info.members.total")}: $totalMembers
            |${bedrockLocalization.getBedrockString(player, "guild.info.members.online")}: $onlineMembers
        """.trimMargin()
    }

    private fun createRelationsSection(): String {
        // Placeholder for relations - would need RelationService integration
        val allies = bedrockLocalization.getBedrockString(player, "guild.info.relations.none")
        val enemies = bedrockLocalization.getBedrockString(player, "guild.info.relations.none")

        return """
            |${bedrockLocalization.getBedrockString(player, "guild.info.relations.allies")}: $allies
            |${bedrockLocalization.getBedrockString(player, "guild.info.relations.enemies")}: $enemies
        """.trimMargin()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // This is a read-only information menu, just close it
        onFormResponseReceived()
    }
}
