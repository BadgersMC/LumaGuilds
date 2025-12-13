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
        return "§6§l━━━ $title §r§6━━━"
    }

    private fun createOverviewSection(): String {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        val foundedDate = formatter.format(guild.createdAt.atZone(java.time.ZoneId.systemDefault()))

        val description = guild.description ?: bedrockLocalization.getBedrockString(player, "guild.info.no.description")
        val tag = guildService.getTag(guild.id) ?: bedrockLocalization.getBedrockString(player, "guild.info.no.tag")
        val emoji = guildService.getEmoji(guild.id) ?: bedrockLocalization.getBedrockString(player, "guild.info.no.emoji")

        val modeColor = when (guild.mode.name) {
            "PEACEFUL" -> "§a"
            "HOSTILE" -> "§c"
            else -> "§e"
        }

        return """
            |§b${bedrockLocalization.getBedrockString(player, "guild.info.name")}§7: §f${guild.name}
            |§d${bedrockLocalization.getBedrockString(player, "guild.info.level")}§7: §f1
            |§e${bedrockLocalization.getBedrockString(player, "guild.info.mode")}§7: $modeColor${guild.mode.name.lowercase().replaceFirstChar { it.uppercase() }}
            |§6${bedrockLocalization.getBedrockString(player, "guild.info.emoji")}§7: §f$emoji
            |§3${bedrockLocalization.getBedrockString(player, "guild.info.description")}§7: §7$description
            |§5${bedrockLocalization.getBedrockString(player, "guild.info.tag")}§7: §f$tag
            |§e${bedrockLocalization.getBedrockString(player, "guild.info.founded")}§7: §f$foundedDate
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
            |§b${bedrockLocalization.getBedrockString(player, "guild.info.members.total")}§7: §f$totalMembers
            |§a${bedrockLocalization.getBedrockString(player, "guild.info.members.online")}§7: §f$onlineMembers
        """.trimMargin()
    }

    private fun createRelationsSection(): String {
        // Placeholder for relations - would need RelationService integration
        val allies = bedrockLocalization.getBedrockString(player, "guild.info.relations.none")
        val enemies = bedrockLocalization.getBedrockString(player, "guild.info.relations.none")

        return """
            |§a${bedrockLocalization.getBedrockString(player, "guild.info.relations.allies")}§7: §7$allies
            |§c${bedrockLocalization.getBedrockString(player, "guild.info.relations.enemies")}§7: §7$enemies
        """.trimMargin()
    }

    override fun handleResponse(player: Player, response: Any?) {
        // This is a read-only information menu, just close it
        onFormResponseReceived()
    }
}
