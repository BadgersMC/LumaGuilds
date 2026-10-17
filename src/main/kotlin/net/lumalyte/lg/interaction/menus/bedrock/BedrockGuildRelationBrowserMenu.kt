package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.GuildInfoRelationResolver
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.util.logging.Logger

class BedrockGuildRelationBrowserMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    private val guild: Guild,
    private val relationType: RelationType,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {
    init {
        require(relationType == RelationType.ALLY || relationType == RelationType.ENEMY) {
            "BedrockGuildRelationBrowserMenu only supports ALLY or ENEMY"
        }
    }

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val lang: LangService by inject()

    private var currentPage = 0

    override fun getForm(): Form {
        val entries = GuildInfoRelationResolver.resolve(
            guild.id,
            relationType,
            relationService.getGuildRelations(guild.id),
            guildService::getGuild,
        )
        val totalPages = GuildInfoRelationResolver.totalPages(entries.size, ITEMS_PER_PAGE)
        currentPage = currentPage.coerceIn(0, totalPages - 1)
        val pageEntries = GuildInfoRelationResolver.page(entries, currentPage, ITEMS_PER_PAGE)
        val config = getBedrockConfig()
        val actions = mutableListOf<() -> Unit>()

        val title = if (relationType == RelationType.ALLY) {
            lang.bedrock("bedrock.info.relation_browser.allies.title", "guild" to guild.name)
        } else {
            lang.bedrock("bedrock.info.relation_browser.enemies.title", "guild" to guild.name)
        }
        val empty = if (relationType == RelationType.ALLY) {
            lang.bedrock("bedrock.info.relation_browser.allies.empty")
        } else {
            lang.bedrock("bedrock.info.relation_browser.enemies.empty")
        }

        val builder = SimpleForm.builder()
            .title(title)
            .content(
                if (entries.isEmpty()) {
                    empty
                } else {
                    val start = currentPage * ITEMS_PER_PAGE + 1
                    val end = minOf(start + pageEntries.size - 1, entries.size)
                    lang.bedrock(
                        "bedrock.info.relation_browser.page",
                        "page" to currentPage + 1,
                        "pages" to totalPages,
                        "start" to start,
                        "end" to end,
                        "total" to entries.size,
                    )
                }
            )
        pageEntries.forEach { entry ->
            val buttonText = if (relationType == RelationType.ALLY) {
                lang.bedrock(
                    "bedrock.info.relation_browser.allies.guild",
                    "guild" to entry.guild.name,
                    "level" to entry.guild.level,
                    "members" to memberService.getMemberCount(entry.guild.id),
                )
            } else {
                lang.bedrock(
                    "bedrock.info.relation_browser.enemies.guild",
                    "guild" to entry.guild.name,
                    "level" to entry.guild.level,
                    "members" to memberService.getMemberCount(entry.guild.id),
                )
            }
            builder.addButtonWithImage(
                config,
                buttonText,
                config.guildMembersIconUrl,
                config.guildMembersIconPath,
            )
            actions += {
                openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, entry.guild))
            }
        }

        if (currentPage > 0) {
            builder.addButtonWithImage(
                config,
                lang.bedrock("bedrock.info.relation_browser.previous"),
                config.backIconUrl,
                config.backIconPath,
            )
            actions += {
                currentPage--
                open()
            }
        }

        if (currentPage < totalPages - 1) {
            builder.addButtonWithImage(
                config,
                lang.bedrock("bedrock.info.relation_browser.next"),
                config.editIconUrl,
                config.editIconPath,
            )
            actions += {
                currentPage++
                open()
            }
        }

        builder.addButtonWithImage(
            config,
            lang.bedrock("bedrock.info.relation_browser.back"),
            config.backIconUrl,
            config.backIconPath,
        )
        actions += { navigateBack() }

        return builder
            .validResultHandler { response ->
                onFormResponseReceived()
                actions.getOrNull(response.clickedButtonId())?.invoke()
            }
            .closedOrInvalidResultHandler { _, _ -> navigateBack() }
            .build()
    }

    override fun handleResponse(player: Player, response: Any?) {
        onFormResponseReceived()
    }

    companion object {
        internal const val ITEMS_PER_PAGE = 12
    }
}
