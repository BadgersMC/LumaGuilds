package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildListEntry
import net.lumalyte.lg.application.services.GuildListService
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.inject
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

class BedrockGuildListMenu(
    menuNavigator: MenuNavigator,
    player: Player,
    logger: Logger,
) : BaseBedrockMenu(menuNavigator, player, logger) {

    private val guildListService: GuildListService by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    private var sortKey = GuildListSortKey.ALL_TIME_ACTIVE

    override fun shouldBuildAsync(): Boolean = true

    override fun getForm(): Form {
        val page = guildListService.getPage(
            page = currentPage,
            pageSize = guildListService.configuredPageSize(),
            sortKey = sortKey,
            ascending = sortKey.defaultAscending,
        )
        currentPage = page.page
        val actions = mutableListOf<() -> Unit>()
        return SimpleForm.builder()
            .title(lang.bedrock(
                "bedrock.guild_list.title",
                "page" to page.page + 1,
                "pages" to page.totalPages,
            ))
            .content(lang.bedrock(
                "bedrock.guild_list.content",
                "sort" to sortLabel(sortKey),
                "total" to page.totalCount,
            ))
            .apply {
                page.entries.forEach { entry ->
                    val guild = entry.guild
                    val activity = activityText(entry)
                    val details =
                        lang.bedrock(
                            "community.directory.bedrock_details",
                            "owners" to
                                entry.details.owners.joinToString(", ", transform = ::ownerName)
                                    .ifEmpty { lang.bedrock("community.directory.unknown") },
                            "allies" to
                                entry.details.allies
                                    .joinToString(", ")
                                    .ifEmpty { lang.bedrock("community.directory.none") },
                            "recruitment" to
                                if (guild.isOpen) {
                                    lang.bedrock(
                                        "community.directory.recruitment_open",
                                    )
                                } else {
                                    lang.bedrock("community.directory.recruitment_closed")
                                },
                        )
                    button(
                        lang.bedrock(
                            "bedrock.guild_list.guild_button",
                            "guild" to guild.name,
                            "level" to guild.level,
                            "members" to entry.memberCount,
                            "created" to CREATED_DATE.format(guild.createdAt),
                            "activity" to activity,
                            "details" to details,
                        ),
                    )
                    actions += {
                        menuNavigator.openMenu(
                            menuFactory.createGuildInfoMenu(menuNavigator, player, guild)
                        )
                    }
                }

                GuildListSortKey.entries.forEach { key ->
                    val selected = if (key == sortKey) {
                        lang.bedrock("bedrock.guild_list.sort.selected")
                    } else {
                        ""
                    }
                    button(lang.bedrock(
                        "bedrock.guild_list.sort.button",
                        "sort" to sortLabel(key),
                        "selected" to selected,
                    ))
                    actions += {
                        if (sortKey != key) {
                            sortKey = key
                            currentPage = 0
                        }
                        open()
                    }
                }

                if (page.page > 0) {
                    button(lang.bedrock("bedrock.guild_list.previous"))
                    actions += {
                        currentPage--
                        open()
                    }
                }
                if (page.page < page.totalPages - 1) {
                    button(lang.bedrock("bedrock.guild_list.next"))
                    actions += {
                        currentPage++
                        open()
                    }
                }
                button(lang.bedrock("bedrock.guild_list.close"))
                actions += { navigateBack() }
            }

            .validResultHandler { response ->
                actions.getOrNull(response.clickedButtonId())?.invoke()
            }
            .closedOrInvalidResultHandler(Runnable { navigateBack() })
            .build()
    }

    private fun sortLabel(key: GuildListSortKey): String = when (key) {
        GuildListSortKey.ALL_TIME_ACTIVE ->
            lang.bedrock("bedrock.guild_list.sort.all_time_active")
        GuildListSortKey.WEEKLY_ACTIVE ->
            lang.bedrock("bedrock.guild_list.sort.weekly_active")
        GuildListSortKey.GUILD_LEVEL ->
            lang.bedrock("bedrock.guild_list.sort.guild_level")
        GuildListSortKey.CREATED_AT ->
            lang.bedrock("bedrock.guild_list.sort.created_at")
    }

    private fun ownerName(id: java.util.UUID): String = org.bukkit.Bukkit.getPlayer(id)?.name ?: id.toString()

    private fun activityText(entry: GuildListEntry): String = when (sortKey) {
        GuildListSortKey.ALL_TIME_ACTIVE ->
            lang.bedrock(
                "bedrock.guild_list.activity",
                "activity_score" to entry.sortValue,
            )
        GuildListSortKey.WEEKLY_ACTIVE ->
            lang.bedrock(
                "bedrock.guild_list.weekly_activity",
                "activity_score" to entry.sortValue,
                "kills" to entry.uniquePvpKills,
            )
        GuildListSortKey.GUILD_LEVEL, GuildListSortKey.CREATED_AT -> ""
    }

    override fun handleResponse(player: Player, response: Any?) = Unit

    companion object {
        private val CREATED_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
    }
}
