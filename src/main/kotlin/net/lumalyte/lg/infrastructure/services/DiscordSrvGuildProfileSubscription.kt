package net.lumalyte.lg.infrastructure.services

import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.api.Subscribe
import github.scarsz.discordsrv.api.commands.PluginSlashCommand
import github.scarsz.discordsrv.api.commands.SlashCommand
import github.scarsz.discordsrv.api.commands.SlashCommandProvider
import github.scarsz.discordsrv.api.events.DiscordReadyEvent
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.ButtonClickEvent
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionType
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.SubcommandData
import github.scarsz.discordsrv.dependencies.jda.api.interactions.components.ActionRow
import github.scarsz.discordsrv.dependencies.jda.api.interactions.components.Button
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.DiscordGuildProfileSubscription
import net.lumalyte.lg.application.services.GuildDiscordProfile
import net.lumalyte.lg.application.services.GuildDiscordProfileLookup
import net.lumalyte.lg.application.services.GuildDiscordProfileService
import net.lumalyte.lg.application.services.GuildListPage
import net.lumalyte.lg.application.services.GuildListService
import net.lumalyte.lg.application.services.KillService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.domain.entities.GuildMode
import org.slf4j.LoggerFactory
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class DiscordSrvGuildProfileSubscription(
    private val plugin: LumaGuilds,
    private val profiles: GuildDiscordProfileService,
    private val guildListService: GuildListService,
    private val killService: KillService,
    private val warService: WarService,
    private val seasonalElo: SeasonalEloCoordinator,
    private val executor: ExecutorService,
) : ListenerAdapter(), DiscordGuildProfileSubscription, SlashCommandProvider {
    private val logger = LoggerFactory.getLogger(DiscordSrvGuildProfileSubscription::class.java)
    private val registered = AtomicBoolean(false)
    private val jdaListenerRegistered = AtomicBoolean(false)

    override fun subscribe() {
        if (!registered.compareAndSet(false, true)) return

        try {
            DiscordSRV.api.addSlashCommandProvider(this)
            DiscordSRV.api.subscribe(this)
            if (DiscordSRV.isReady) activateReadyIntegration()
            logger.info("Registered LumaGuilds public guild slash commands")
        } catch (error: Throwable) {
            registered.set(false)
            runCatching { DiscordSRV.api.unsubscribe(this) }
            runCatching { DiscordSRV.api.removeSlashCommandProvider(this) }
            logger.warn("Failed to register LumaGuilds Discord guild commands", error)
        }
    }

    override fun unsubscribe() {
        if (!registered.compareAndSet(true, false)) return

        if (jdaListenerRegistered.compareAndSet(true, false)) {
            runCatching { DiscordSRV.getPlugin().jda.removeEventListener(this) }
                .onFailure { logger.warn("Failed to unregister LumaGuilds Discord button listener", it) }
        }

        runCatching { DiscordSRV.api.unsubscribe(this) }
            .onFailure { logger.warn("Failed to unsubscribe from DiscordSRV API events", it) }
        runCatching { DiscordSRV.api.removeSlashCommandProvider(this) }
            .onFailure { logger.warn("Failed to unregister LumaGuilds Discord guild commands", it) }

        if (DiscordSRV.isReady) {
            runCatching { DiscordSRV.api.updateSlashCommands() }
                .onFailure { logger.warn("Failed to refresh Discord slash commands after unregistering", it) }
        }
    }

    @Subscribe
    fun onDiscordReady(@Suppress("UNUSED_PARAMETER") event: DiscordReadyEvent) {
        activateReadyIntegration()
    }

    private fun activateReadyIntegration() {
        if (!registered.get()) return

        if (jdaListenerRegistered.compareAndSet(false, true)) {
            try {
                DiscordSRV.getPlugin().jda.addEventListener(this)
            } catch (error: Throwable) {
                jdaListenerRegistered.set(false)
                logger.warn("Failed to register LumaGuilds Discord button listener", error)
            }
        }

        runCatching { DiscordSRV.api.updateSlashCommands() }
            .onFailure { logger.warn("Failed to refresh LumaGuilds Discord slash commands", it) }
    }

    override fun getSlashCommands(): Set<PluginSlashCommand> {
        val discordGuild = runCatching { DiscordSRV.getPlugin().mainGuild }.getOrNull()
            ?: return emptySet()

        val info = SubcommandData("info", "View a LumaGuilds guild profile")
            .addOption(OptionType.STRING, "guild", "Guild name", true)
        val list = SubcommandData("list", "Browse all LumaGuilds guilds")
            .addOption(OptionType.INTEGER, "page", "Page number", false)

        val command = CommandData("guild", "Browse LumaGuilds guilds")
            .addSubcommands(info, list)

        return setOf(
            PluginSlashCommand(plugin, command)
                .addGuildFilter(discordGuild)
        )
    }

    @SlashCommand(path = "guild/info", deferReply = true)
    fun guildInfo(event: SlashCommandEvent) {
        val query = event.getOption("guild")?.asString?.trim().orEmpty()
        val hook = event.hook

        CompletableFuture.supplyAsync({ profiles.lookup(query) }, executor)
            .whenComplete { result, error ->
                if (error != null) {
                    logger.warn("Failed to load Discord guild profile for '$query'", error.cause ?: error)
                    hook.editOriginal("That guild profile could not be loaded right now.").queue()
                    return@whenComplete
                }

                when (result) {
                    is GuildDiscordProfileLookup.Found -> {
                        hook.editOriginalEmbeds(profileEmbed(result.profile))
                            .setActionRows(actionRow(result.profile.guildId, null))
                            .queue()
                    }

                    is GuildDiscordProfileLookup.Ambiguous -> {
                        val names = result.matches.joinToString(", ") { "**$it**" }
                        hook.editOriginal(
                            "More than one guild matches **$query**: $names. Please use the full guild name."
                        ).queue()
                    }

                    GuildDiscordProfileLookup.NotFound ->
                        hook.editOriginal("No LumaGuilds guild matched **$query**.").queue()

                    null ->
                        hook.editOriginal("That guild profile could not be loaded right now.").queue()
                }
            }
    }

    @SlashCommand(path = "guild/list", deferReply = true)
    fun guildList(event: SlashCommandEvent) {
        val requestedPage = (event.getOption("page")?.asLong ?: 1L)
            .coerceIn(1L, MAX_PAGE.toLong())
            .toInt()

        guildListService.getPageAsync(
            page = requestedPage - 1,
            pageSize = LIST_PAGE_SIZE,
            sortKey = GuildListSortKey.GUILD_LEVEL,
            ascending = false,
        ).whenComplete { page, error ->
            if (error != null) {
                logger.warn("Failed to render Discord guild list", error.cause ?: error)
                event.hook.editOriginal("The guild list could not be loaded right now.").queue()
                return@whenComplete
            }

            val embed = EmbedBuilder()
                .setTitle("LumaGuilds • Guild Browser")
                .setDescription(buildListDescription(page))
                .setFooter("Page ${page.page + 1}/${page.totalPages} • ${page.totalCount} guilds")
                .setColor(EMBED_COLOR)
                .build()
            event.hook.editOriginalEmbeds(embed).queue()
        }
    }

    override fun onButtonClick(event: ButtonClickEvent) {
        val target = parseButtonTarget(event.componentId) ?: return

        event.deferEdit().queue(
            { hook ->
                CompletableFuture.supplyAsync(
                    { buildDetailEmbed(target.guildId, target.view) },
                    executor,
                ).whenComplete { embed, error ->
                    if (error != null) {
                        logger.warn(
                            "Failed to render Discord guild ${target.view.id} view for ${target.guildId}",
                            error.cause ?: error,
                        )
                        hook.editOriginalEmbeds(errorEmbed("That guild view could not be loaded right now."))
                            .setActionRows(actionRow(target.guildId, target.view))
                            .queue()
                    } else if (embed == null) {
                        hook.editOriginalEmbeds(errorEmbed("That guild no longer exists."))
                          .setActionRows(actionRow(target.guildId, target.view))
                          .queue()
                    } else {
                        hook.editOriginalEmbeds(embed)
                            .setActionRows(actionRow(target.guildId, target.view))
                            .queue()
                    }
                }
            },
            { error -> logger.warn("Failed to acknowledge LumaGuilds Discord button interaction", error) },
        )
    }

    private fun buildDetailEmbed(guildId: UUID, view: GuildView): MessageEmbed? {
        val profile = profiles.get(guildId) ?: return null
        return when (view) {
            GuildView.MEMBERS -> membersEmbed(profile)
            GuildView.PROGRESSION -> progressionEmbed(profile)
            GuildView.DIPLOMACY -> diplomacyEmbed(profile)
            GuildView.STATISTICS -> statisticsEmbed(profile)
            GuildView.ELO -> eloEmbed(profile)
        }
    }

    private fun profileEmbed(profile: GuildDiscordProfile): MessageEmbed = EmbedBuilder()
        .setTitle(profile.name)
        .setDescription(profile.description?.take(MAX_DESCRIPTION) ?: "No guild description set.")
        .setColor(EMBED_COLOR)
        .addField("Established", DATE_FORMAT.format(profile.createdAt), true)
        .addField("Members", memberCount(profile), true)
        .addField("Status", status(profile), true)
        .addField("Progression", progression(profile), false)
        .addField("Diplomacy", "${profile.allies.size} allies • ${profile.enemies.size} enemies", true)
        .setFooter("LumaGuilds • Public guild profile")
        .build()

    private fun membersEmbed(profile: GuildDiscordProfile): MessageEmbed = EmbedBuilder()
        .setTitle("${profile.name} • Members")
        .setDescription("${memberCount(profile)} members")
        .setColor(EMBED_COLOR)
        .apply {
            profile.rankGroups.take(MAX_MEMBER_RANK_FIELDS).forEach { group ->
                addField(
                    "${group.name} (${group.members.size})",
                    summarize(group.members, MEMBER_FIELD_LIMIT),
                    false,
                )
            }
            if (profile.rankGroups.isEmpty()) {
                addField("Members", "No members found.", false)
            } else if (profile.rankGroups.size > MAX_MEMBER_RANK_FIELDS) {
                addField(
                    "More ranks",
                    "+${profile.rankGroups.size - MAX_MEMBER_RANK_FIELDS} additional rank group(s)",
                    false,
                )
            }
        }
        .setFooter("LumaGuilds • Members")
        .build()

    private fun progressionEmbed(profile: GuildDiscordProfile): MessageEmbed = EmbedBuilder()
        .setTitle("${profile.name} • Progression")
        .setColor(EMBED_COLOR)
        .addField("Level", profile.level.toString(), true)
        .addField("Prestige", profile.prestigeCount.toString(), true)
        .addField("Lifetime XP", formatNumber(profile.totalExperience), true)
        .addField("Level Progress", progression(profile), false)
        .setFooter("LumaGuilds • Progression")
        .build()

    private fun diplomacyEmbed(profile: GuildDiscordProfile): MessageEmbed = EmbedBuilder()
        .setTitle("${profile.name} • Diplomacy")
        .setColor(EMBED_COLOR)
        .addField("Mode", if (profile.mode == GuildMode.PEACEFUL) "Peaceful" else "Hostile", true)
        .addField("Allies (${profile.allies.size})", summarize(profile.allies, RELATION_FIELD_LIMIT), false)
        .addField("Enemies (${profile.enemies.size})", summarize(profile.enemies, RELATION_FIELD_LIMIT), false)
        .setFooter("LumaGuilds • Diplomacy")
        .build()

    private fun statisticsEmbed(profile: GuildDiscordProfile): MessageEmbed {
        val kills = killService.getGuildKillStats(profile.guildId)
        val activeWars = warService.getWarsForGuild(profile.guildId).count { it.isActive }
        val completedWars = warService.getWarHistory(profile.guildId, WAR_HISTORY_LIMIT)
            .filter { it.isEnded }
        val wins = completedWars.count { it.winner == profile.guildId }
        val losses = completedWars.count { it.winner != null && it.winner != profile.guildId }
        val draws = completedWars.count { it.winner == null }
        val winRate = if (completedWars.isEmpty()) 0.0
            else wins.toDouble() / completedWars.size.toDouble() * 100.0

        return EmbedBuilder()
            .setTitle("${profile.name} • Statistics")
            .setColor(EMBED_COLOR)
            .addField(
                "Combat",
                "**${formatNumber(kills.totalKills)}** kills\n" +
                    "**${formatNumber(kills.totalDeaths)}** deaths\n" +
                    "**${formatNumber(kills.netKills)}** net\n" +
                    "**${String.format(Locale.US, "%.2f", kills.killDeathRatio)}** K/D",
                true,
            )
            .addField(
                "Wars",
                "**$activeWars** active\n" +
                    "**$wins** wins • **$losses** losses • **$draws** draws\n" +
                    "**${String.format(Locale.US, "%.1f", winRate)}%** win rate",
                true,
            )
            .addField("Members", "**${profile.memberCount}** current", true)
            .setFooter("LumaGuilds • Statistics • Up to $WAR_HISTORY_LIMIT recent wars")
            .build()
    }

    private fun eloEmbed(profile: GuildDiscordProfile): MessageEmbed {
        val view = seasonalElo.view(profile.guildId)
            ?: return EmbedBuilder()
                .setTitle("${profile.name} • ELO")
                .setDescription("Seasonal ELO is not currently available.")
                .setColor(EMBED_COLOR)
                .setFooter("LumaGuilds • Seasonal ELO")
                .build()

        return EmbedBuilder()
            .setTitle("${profile.name} • ELO")
            .setColor(EMBED_COLOR)
            .addField("Rating", formatNumber(view.rating), true)
            .addField("Display Level", view.displayLevel.toString(), true)
            .addField("Rank", view.rank?.let { "#$it" } ?: "Unranked", true)
            .addField("Chapter", view.chapterId, true)
            .addField("Rated-war eligible", if (view.eligible) "Yes" else "No", true)
            .setFooter("LumaGuilds • Seasonal ELO")
            .build()
    }

    private fun actionRow(guildId: UUID, active: GuildView?): ActionRow =
        ActionRow.of(
            GuildView.entries.map { view ->
                val customId = "$BUTTON_PREFIX:${view.id}:$guildId"
                if (view == active) Button.primary(customId, view.label)
                else Button.secondary(customId, view.label)
            }
        )

    private fun parseButtonTarget(componentId: String): ButtonTarget? {
        if (!componentId.startsWith("$BUTTON_PREFIX:")) return null
        val parts = componentId.split(':')
        if (parts.size != 4 || parts[0] != "lumaguilds" || parts[1] != "guild") return null
        val view = GuildView.entries.firstOrNull { it.id == parts[2] } ?: return null
        val guildId = runCatching { UUID.fromString(parts[3]) }.getOrNull() ?: return null
        return ButtonTarget(guildId, view)
    }

    private fun memberCount(profile: GuildDiscordProfile): String =
        profile.memberLimit?.let { "${profile.memberCount} / $it" } ?: profile.memberCount.toString()

    private fun status(profile: GuildDiscordProfile): String {
        val mode = if (profile.mode == GuildMode.PEACEFUL) "Peaceful" else "Hostile"
        val recruitment = if (profile.recruiting) "Recruiting" else "Invite only"
        return "$mode • $recruitment"
    }

    private fun progression(profile: GuildDiscordProfile): String {
        val filled = (profile.progressFraction * PROGRESS_SEGMENTS).toInt()
            .coerceIn(0, PROGRESS_SEGMENTS)
        val progress = "■".repeat(filled) + "□".repeat(PROGRESS_SEGMENTS - filled)
        val percent = String.format(Locale.US, "%.1f", profile.progressFraction * 100.0)
        val current = formatNumber(profile.experienceThisLevel)
        val target = formatNumber(profile.experienceForNextLevel)

        val next = if (profile.experienceForNextLevel > 0) {
            val remaining = (profile.experienceForNextLevel - profile.experienceThisLevel).coerceAtLeast(0)
            "\n${formatNumber(remaining)} XP to level ${profile.level + 1}"
        } else {
            "\nMax level"
        }

        return "$progress **$percent%**\n$current / $target XP$next"
    }

    private fun summarize(values: List<String>, maxChars: Int): String {
        if (values.isEmpty()) return "None"

        val output = StringBuilder()
        var included = 0
        for (value in values) {
            val separator = if (included == 0) "" else ", "
            val reserve = 20
            if (output.length + separator.length + value.length + reserve > maxChars) break
            output.append(separator).append(value)
            included++
        }

        if (included == 0) {
            output.append(values.first().take((maxChars - 1).coerceAtLeast(1))).append("…")
            included = 1
        }
        if (included < values.size) output.append("\n+${values.size - included} more")
        return output.toString()
    }

    private fun buildListDescription(page: GuildListPage): String {
        if (page.entries.isEmpty()) return "No guilds found."

        return page.entries.mapIndexed { index, entry ->
            val position = page.page * page.pageSize + index + 1
            val recruiting = if (entry.guild.isOpen) " • Recruiting" else ""
            "**$position. ${entry.guild.name}** — Level ${entry.guild.level} • " +
                "${entry.memberCount} member${if (entry.memberCount == 1) "" else "s"}$recruiting"
        }.joinToString("\n")
    }

    private fun errorEmbed(message: String): MessageEmbed = EmbedBuilder()
        .setTitle("LumaGuilds")
        .setDescription(message)
        .setColor(EMBED_COLOR)
        .build()

    private fun formatNumber(value: Int): String = String.format(Locale.US, "%,d", value)

    private data class ButtonTarget(
        val guildId: UUID,
        val view: GuildView,
    )

    private enum class GuildView(val id: String, val label: String) {
        MEMBERS("members", "Members"),
        PROGRESSION("progression", "Progression"),
        DIPLOMACY("diplomacy", "Diplomacy"),
        STATISTICS("statistics", "Statistics"),
        ELO("elo", "ELO"),
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM uuuu")
            .withZone(ZoneOffset.UTC)
        private const val EMBED_COLOR = 0x2FB7A3
        private const val LIST_PAGE_SIZE = 10
        private const val MAX_PAGE = 10_000
        private const val MAX_DESCRIPTION = 500
        private const val MAX_MEMBER_RANK_FIELDS = 20
        private const val MEMBER_FIELD_LIMIT = 900
        private const val RELATION_FIELD_LIMIT = 900
        private const val PROGRESS_SEGMENTS = 10
        private const val WAR_HISTORY_LIMIT = 100
        private const val BUTTON_PREFIX = "lumaguilds:guild"
    }
}
