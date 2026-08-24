package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
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
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val infoIcon = BedrockFormUtils.createFormImage(config, config.guildSettingsIconUrl, config.guildSettingsIconPath)

        return CustomForm.builder()
            .title(lang.bedrock("bedrock.info.title", "guild" to guild.name))
            .apply { infoIcon?.let { icon(it) } }
            .label(lang.bedrock("bedrock.info.description"))
            .label(createSectionHeader(lang.bedrock("bedrock.info.header.overview")))
            .label(createOverviewSection())
            .label(createSectionHeader(lang.bedrock("bedrock.info.header.members")))
            .label(createMembersSection())
            .label(createSectionHeader(lang.bedrock("bedrock.info.header.relations")))
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
        return lang.bedrock("bedrock.info.header.format", "title" to title)
    }

    private fun createOverviewSection(): String {
        val formatter = DateTimeFormatter.ofPattern(lang.raw("bedrock.info.date_format"))
        val foundedDate = formatter.format(guild.createdAt.atZone(java.time.ZoneId.systemDefault()))

        val description = guild.description ?: lang.bedrock("bedrock.info.value.no_description")
        val tag = guildService.getTag(guild.id) ?: lang.bedrock("bedrock.info.value.no_tag")
        val emoji = guildService.getEmoji(guild.id) ?: lang.bedrock("bedrock.info.value.no_emoji")

        return when (guild.mode) {
            GuildMode.PEACEFUL -> lang.bedrock(
                "bedrock.info.overview.peaceful",
                "guild" to guild.name,
                "level" to guild.level,
                "emoji" to emoji,
                "description" to description,
                "tag" to tag,
                "founded" to foundedDate
            )
            GuildMode.HOSTILE -> lang.bedrock(
                "bedrock.info.overview.hostile",
                "guild" to guild.name,
                "level" to guild.level,
                "emoji" to emoji,
                "description" to description,
                "tag" to tag,
                "founded" to foundedDate
            )
        }
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

        return lang.bedrock("bedrock.info.members", "total" to totalMembers, "online" to onlineMembers)
    }

    private fun createRelationsSection(): String {
        // Placeholder for relations - would need RelationService integration
        val none = lang.bedrock("bedrock.info.value.no_relations")
        return lang.bedrock("bedrock.info.relations", "allies" to none, "enemies" to none)
    }

    override fun handleResponse(player: Player, response: Any?) {
        // This is a read-only information menu, just close it
        onFormResponseReceived()
    }
}
