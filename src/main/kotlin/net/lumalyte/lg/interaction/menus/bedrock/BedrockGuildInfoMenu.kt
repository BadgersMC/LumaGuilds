package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.infrastructure.i18n.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.GuildInfoRelationResolver
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.Form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Bedrock Edition guild information menu using Cumulus SimpleForm.
 * Displays guild details plus expandable ally/enemy browsers.
 */
class BedrockGuildInfoMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    logger: Logger
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val warService: net.lumalyte.lg.application.services.WarService by inject()
    private val lang: LangService by inject()

    override fun getForm(): Form {
        val config = getBedrockConfig()
        val relations = relationService.getGuildRelations(guild.id)
        val allies = GuildInfoRelationResolver.resolve(
            guild.id,
            RelationType.ALLY,
            relations,
            guildService::getGuild,
        )
        val enemies = GuildInfoRelationResolver.resolve(
            guild.id,
            RelationType.ENEMY,
            relations,
            guildService::getGuild,
        )

        val content = listOf(
            lang.bedrock("bedrock.info.description"),
            createSectionHeader(lang.bedrock("bedrock.info.header.overview")),
            createOverviewSection(),
            createSectionHeader(lang.bedrock("bedrock.info.header.members")),
            createMembersSection(),
            createSectionHeader(lang.bedrock("bedrock.info.header.relations")),
            createRelationsSection(allies.map { it.guild.name }, enemies.map { it.guild.name }),
            createSectionHeader(lang.bedrock("bedrock.info.header.wars")),
            createWarSection(),
        ).joinToString("\n\n")

        return SimpleForm.builder()
            .title(lang.bedrock("bedrock.info.title", "guild" to guild.name))
            .content(content)
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.info.button.allies", "count" to allies.size),
                config.guildMembersIconUrl,
                config.guildMembersIconPath,
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.info.button.enemies", "count" to enemies.size),
                config.cancelIconUrl,
                config.cancelIconPath,
            )
            .addButtonWithImage(
                config,
                lang.bedrock("bedrock.info.button.back"),
                config.backIconUrl,
                config.backIconPath,
            )
            .validResultHandler { response ->
                onFormResponseReceived()
                when (response.clickedButtonId()) {
                    0 -> openMenu(
                        menuFactory.createGuildRelationBrowserMenu(
                            menuNavigator,
                            player,
                            guild,
                            RelationType.ALLY,
                        )
                    )
                    1 -> openMenu(
                        menuFactory.createGuildRelationBrowserMenu(
                            menuNavigator,
                            player,
                            guild,
                            RelationType.ENEMY,
                        )
                    )
                    2 -> navigateBack()
                }
            }
            .closedOrInvalidResultHandler { _, _ -> navigateBack() }
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

    private fun createRelationsSection(allies: List<String>, enemies: List<String>): String {
        val none = lang.bedrock("bedrock.info.value.no_relations")
        return lang.bedrock(
            "bedrock.info.relations",
            "allies" to allies.take(3).joinToString(", ").ifEmpty { none },
            "enemies" to enemies.take(3).joinToString(", ").ifEmpty { none },
        )
    }

    private fun createWarSection(): String {
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
        if (activeWars.isEmpty()) return lang.bedrock("bedrock.info.wars.none")
        val target = warService.getWarKillWinTarget()
        return activeWars.joinToString("\n") { war ->
            val opponentId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val opponent = guildService.getGuild(opponentId)?.name ?: opponentId.toString().take(8)
            val stats = warService.getWarStats(war.id)
            val kills = if (war.declaringGuildId == guild.id) stats.declaringGuildKills else stats.defendingGuildKills
            lang.bedrock(
                "bedrock.info.wars.entry",
                "opponent" to opponent,
                "kills" to kills,
                "target" to target,
            )
        }
    }

    override fun handleResponse(player: Player, response: Any?) {
        // This is a read-only information menu, just close it
        onFormResponseReceived()
    }
}
