package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.GuildRecruitmentPermissions
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.GuildApplication
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import net.lumalyte.lg.domain.values.JoinRequirement
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

internal enum class RecruitmentScreen {
    BROWSE,
    FILTER,
    PROFILE,
    MINE,
    APPLICATION,
    INBOX,
    REVIEW,
    SETTINGS,
    METHOD,
    CONFIRM,
    DISCORD,
}

internal data class RecruitmentButton(
    val label: String,
    val details: List<String> = emptyList(),
    val icon: String = "lg_members",
    val guild: Guild? = null,
    val action: () -> Unit = {
    },
)

internal data class RecruitmentView(
    val title: String,
    val items: List<RecruitmentButton>,
    val navigation: List<RecruitmentButton>,
)

internal data class RecruitmentInput(val label: String, val value: String, val accept: (String) -> Unit)

/** Shared interaction state; every private read and mutation is authorized in the application service. */
internal class RecruitmentController(private val player: Player, private val refresh: () -> Unit) : KoinComponent {
    private val service: GuildRecruitmentService by inject()
    private val guilds: GuildService by inject()
    private val members: MemberService by inject()
    private val lfg: LfgService by inject()
    private val directory: RecruitmentDirectoryRepository by inject()
    private val lang: LangService by inject()
    private var screen = RecruitmentScreen.BROWSE
    private var selected: UUID? = null
    private var selectedApplication: UUID? = null
    private var quotedGuild: Guild? = null
    private var quotedFee: JoinRequirement? = null
    private var query = ""
    private var peaceful: Boolean? = null
    private var recruitingOnly = true
    private var beginnersOnly = false
    private var joiningMethod: RecruitmentMode? = null
    var page = 0
    var input: RecruitmentInput? = null
        private set

    @Suppress(
        "TooGenericExceptionCaught",
    ) // UI boundary: arbitrary adapter failures must close private screens and report failure.
    fun run(action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            player.closeInventory()
            org.slf4j.LoggerFactory.getLogger(
                javaClass,
            ).warn("Recruitment action failed for {}", player.uniqueId, error)
            player.sendMessage(lang.msg("recruitment.feedback.failed"))
        }
    }

    fun view(): RecruitmentView {
        val navigation =
            mutableListOf(
                RecruitmentButton(lang.raw("recruitment.browse"), icon = "lg_back") { go(RecruitmentScreen.BROWSE) },
                RecruitmentButton(lang.raw("recruitment.mine"), icon = "lg_scroll") { go(RecruitmentScreen.MINE) },
                RecruitmentButton(lang.raw("recruitment.search"), listOf(query), "lg_rename") {
                    ask(lang.raw("recruitment.search"), query) {
                        query = it.take(64)
                        go(RecruitmentScreen.BROWSE)
                    }
                },
                RecruitmentButton(lang.raw("recruitment.filter"), listOf(guildMode()), "lg_settings") {
                    go(RecruitmentScreen.FILTER)
                },
                RecruitmentButton(
                    lang.raw("recruitment.reminders"),
                    listOf(enabled(directory.remindersEnabled(player.uniqueId))),
                    "lg_toggle_on",
                ) {
                    directory.setReminders(player.uniqueId, !directory.remindersEnabled(player.uniqueId))
                    refresh()
                },
            )
        ownGuild()?.let { guild ->
            if (GuildRecruitmentPermissions.canReview(members, player.uniqueId, guild.id)) {
                navigation +=
                    RecruitmentButton(lang.raw("recruitment.inbox"), icon = "lg_members") {
                        go(RecruitmentScreen.INBOX)
                    }
            }
            if (GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id)) {
                navigation +=
                    RecruitmentButton(lang.raw("recruitment.settings"), icon = "lg_settings") {
                        go(RecruitmentScreen.SETTINGS)
                    }
            }
        }
        val items =
            when (screen) {
                RecruitmentScreen.BROWSE -> browse()
                RecruitmentScreen.FILTER -> filters()
                RecruitmentScreen.PROFILE -> profile()
                RecruitmentScreen.MINE -> service.mine(player.uniqueId).map(::applicationButton)
                RecruitmentScreen.APPLICATION -> applicantActions()
                RecruitmentScreen.INBOX -> inbox()
                RecruitmentScreen.REVIEW -> review()
                RecruitmentScreen.SETTINGS -> settings()
                RecruitmentScreen.METHOD -> methodSettings()
                RecruitmentScreen.CONFIRM -> confirm()
                RecruitmentScreen.DISCORD -> discord()
            }
        return RecruitmentView(
            lang.raw("recruitment.title"),
            items.ifEmpty {
                listOf(RecruitmentButton(lang.raw("recruitment.empty")))
            },
            navigation,
        )
    }

    fun acceptInput(value: String) {
        val current = input ?: return
        input = null
        run { current.accept(value.trim()) }
        refresh()
    }

    fun cancelInput() {
        input = null
        refresh()
    }

    private fun go(next: RecruitmentScreen) {
        screen = next
        page = 0
        refresh()
    }

    private fun ask(
        label: String,
        value: String = "",
        accept: (String) -> Unit,
    ) {
        input = RecruitmentInput(label, value, accept)
        refresh()
    }

    private fun ownGuild(): Guild? = guilds.getPlayerGuilds(player.uniqueId).firstOrNull()

    fun theme(): net.lumalyte.lg.utils.GuiTheme = ownGuild()?.guiTheme ?: net.lumalyte.lg.utils.GuiTheme.DEFAULT

    private fun enabled(value: Boolean): String =
        if (value) {
            lang.raw(
                "recruitment.enabled",
            )
        } else {
            lang.raw("recruitment.disabled")
        }

    private fun guildMode(): String =
        when (peaceful) {
            null -> lang.raw("recruitment.any")
            true -> lang.raw("recruitment.peaceful")
            false -> lang.raw("recruitment.competitive")
        }

    private fun status(value: ApplicationStatus): String =
        when (value) {
            ApplicationStatus.SUBMITTED -> lang.raw("recruitment.status.submitted")
            ApplicationStatus.OFFERED -> lang.raw("recruitment.status.offered")
            ApplicationStatus.ADMISSION_PENDING -> lang.raw("recruitment.status.admission_pending")
            ApplicationStatus.JOINED -> lang.raw("recruitment.status.joined")
            ApplicationStatus.DECLINED -> lang.raw("recruitment.status.declined")
            ApplicationStatus.WITHDRAWN -> lang.raw("recruitment.status.withdrawn")
            ApplicationStatus.EXPIRED -> lang.raw("recruitment.status.expired")
            ApplicationStatus.CLOSED -> lang.raw("recruitment.status.closed")
        }

    private fun answers(application: GuildApplication): RecruitmentButton =
        RecruitmentButton(
            status(application.status),
            listOf(application.answers, application.decisionReason),
            "lg_scroll",
        ) {
            // The visible lore is a summary. Fetch and authorize again before revealing the complete private answer.
            val fresh =
                if (application.applicantId == player.uniqueId) {
                    service.mine(player.uniqueId).firstOrNull { it.id == application.id }
                } else {
                    service.inbox(player.uniqueId, application.guildId).firstOrNull { it.id == application.id }
                }
            player.sendMessage(Component.text(requireNotNull(fresh).answers))
        }

    private fun selectedGuild(): Guild = requireNotNull(selected?.let(guilds::getGuild))

    private fun selectedOwnApplication(): GuildApplication =
        requireNotNull(
            service.mine(player.uniqueId).firstOrNull {
                it.id == selectedApplication
            },
        )

    private fun mode(mode: RecruitmentMode): String =
        when (mode) {
            RecruitmentMode.JOIN_IMMEDIATELY -> lang.raw("recruitment.mode.join")
            RecruitmentMode.IN_GAME -> lang.raw("recruitment.mode.apply")
            RecruitmentMode.DISCORD -> lang.raw("recruitment.mode.discord")
            RecruitmentMode.CLOSED -> lang.raw("recruitment.mode.closed")
        }

    private fun browse(): List<RecruitmentButton> =
        service.browse().filter { guild ->
            val profile = service.profile(guild)
            (peaceful == null || peaceful == (guild.mode == GuildMode.PEACEFUL)) &&
                (!recruitingOnly || (profile.mode != RecruitmentMode.CLOSED && service.spaces(guild.id) > 0)) &&
                (!beginnersOnly || profile.beginnerFriendly) &&
                (joiningMethod == null || joiningMethod == profile.mode) &&
                (guild.name + " " + profile.pitch + " " + profile.interests.joinToString(" ")).contains(query, true)
        }.map { guild ->
            RecruitmentButton(guild.name, details(guild), "lg_banner", guild) {
                selectedApplication = null
                selected = guild.id
                go(RecruitmentScreen.PROFILE)
            }
        }

    private fun filters(): List<RecruitmentButton> =
        listOf(
            RecruitmentButton(lang.raw("recruitment.filter"), listOf(guildMode()), "lg_settings") {
                peaceful =
                    when (peaceful) {
                        null -> true
                        true -> false
                        false -> null
                    }
                refresh()
            },
            RecruitmentButton(
                lang.raw("recruitment.filter_recruiting"),
                listOf(enabled(recruitingOnly)),
                "lg_settings",
            ) {
                recruitingOnly = !recruitingOnly
                refresh()
            },
            RecruitmentButton(lang.raw("recruitment.filter_beginner"), listOf(enabled(beginnersOnly)), "lg_settings") {
                beginnersOnly = !beginnersOnly
                refresh()
            },
            RecruitmentButton(
                lang.raw("recruitment.method"),
                listOf(joiningMethod?.let(::mode) ?: lang.raw("recruitment.any")),
                "lg_settings",
            ) {
                joiningMethod =
                    when (joiningMethod) {
                        null -> RecruitmentMode.JOIN_IMMEDIATELY
                        RecruitmentMode.CLOSED -> null
                        else -> RecruitmentMode.entries[requireNotNull(joiningMethod).ordinal + 1]
                    }
                refresh()
            },
        )

    private fun details(guild: Guild): List<String> {
        val profile = service.profile(guild)
        val fee = lfg.getJoinRequirement(guild)
        return listOf(
            mode(profile.mode),
            lang.raw("recruitment.spaces") + ": " + service.spaces(guild.id),
            lang.raw("recruitment.level") + ": " + guild.level,
            lang.raw("recruitment.fee") + ": " + (
                fee?.let {
                    "${it.amount} ${it.currencyName}"
                } ?: if (guild.joinFeeEnabled && guild.joinFeeAmount > 0) {
                    lang.raw("recruitment.fee_unavailable")
                } else {
                    "0"
                }
            ),
            profile.pitch, profile.requirements,
            profile.interests.joinToString(
                ", ",
            ),
            profile.preferredTimes, profile.language,
            if (profile.beginnerFriendly) lang.raw("recruitment.beginner") else "",
        ).filter(String::isNotBlank)
    }

    private fun profile(): List<RecruitmentButton> {
        val guild = selectedGuild()
        val profile = service.profile(guild)
        val items = mutableListOf(RecruitmentButton(guild.name, details(guild), "lg_banner"))
        if (service.spaces(guild.id) > 0 && members.getPlayerGuilds(player.uniqueId).isEmpty()) {
            when (profile.mode) {
                RecruitmentMode.JOIN_IMMEDIATELY ->
                    items +=
                        RecruitmentButton(mode(profile.mode), icon = "lg_accept") {
                            quotedGuild = guild
                            quotedFee = lfg.getJoinRequirement(guild)
                            go(RecruitmentScreen.CONFIRM)
                        }
                RecruitmentMode.IN_GAME ->
                    items +=
                        RecruitmentButton(mode(profile.mode), icon = "lg_scroll") {
                            ask(lang.raw("recruitment.introduction")) {
                                service.submit(player.uniqueId, guild.id, it)
                                go(RecruitmentScreen.MINE)
                            }
                        }
                // The optional Discord button below opens exact-destination confirmation.
                RecruitmentMode.DISCORD -> Unit
                RecruitmentMode.CLOSED -> Unit
            }
        }
        if (profile.discordInvite != null) {
            items +=
                RecruitmentButton(lang.raw("recruitment.discord"), icon = "lg_announcement") {
                    go(RecruitmentScreen.DISCORD)
                }
        }
        return items
    }

    private fun applicationButton(application: GuildApplication): RecruitmentButton {
        val name = guilds.getGuild(application.guildId)?.name ?: application.guildId.toString()
        return RecruitmentButton(
            name,
            listOf(status(application.status), application.expiresAt.toString(), application.decisionReason),
            "lg_scroll",
        ) {
            selectedApplication = application.id
            selected = application.guildId
            go(RecruitmentScreen.APPLICATION)
        }
    }

    private fun applicantActions(): List<RecruitmentButton> {
        val application = selectedOwnApplication()
        val items =
            mutableListOf(answers(application))
        if (application.status in setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.OFFERED)) {
            items +=
                RecruitmentButton(lang.raw("recruitment.withdraw"), icon = "lg_cancel") {
                    service.withdraw(player.uniqueId, application.id)
                    go(RecruitmentScreen.MINE)
                }
        }
        if (application.status == ApplicationStatus.OFFERED) {
            items +=
                RecruitmentButton(lang.raw("recruitment.offer"), icon = "lg_accept") {
                    quotedGuild = selectedGuild()
                    quotedFee = lfg.getJoinRequirement(requireNotNull(quotedGuild))
                    go(RecruitmentScreen.CONFIRM)
                }
        }
        if (application.status == ApplicationStatus.ADMISSION_PENDING) {
            items += RecruitmentButton(lang.raw("recruitment.pending"), icon = "lg_scroll")
        }
        return items
    }

    private fun inbox(): List<RecruitmentButton> {
        val guild = requireNotNull(ownGuild())
        return service.inbox(player.uniqueId, guild.id).filter { it.status == ApplicationStatus.SUBMITTED }.map {
                application ->
            RecruitmentButton(
                org.bukkit.Bukkit.getOfflinePlayer(application.applicantId).name ?: application.applicantId.toString(),
                listOf(application.createdAt.toString()),
                "lg_members",
            ) {
                selectedApplication = application.id
                selected = guild.id
                go(RecruitmentScreen.REVIEW)
            }
        }
    }

    private fun review(): List<RecruitmentButton> {
        val guild = requireNotNull(ownGuild())
        val application =
            requireNotNull(service.inbox(player.uniqueId, guild.id).firstOrNull { it.id == selectedApplication })
        return listOf(
            answers(application),
            RecruitmentButton(lang.raw("recruitment.send_offer"), icon = "lg_accept") {
                service.review(player.uniqueId, application.id, true, "")
                go(RecruitmentScreen.INBOX)
            },
            RecruitmentButton(lang.raw("recruitment.decline"), icon = "lg_cancel") {
                ask(lang.raw("recruitment.reason")) {
                    service.review(player.uniqueId, application.id, false, it)
                    go(RecruitmentScreen.INBOX)
                }
            },
        )
    }

    private fun confirm(): List<RecruitmentButton> {
        val quote = requireNotNull(quotedGuild)
        return listOf(
            RecruitmentButton(quote.name, details(quote), "lg_scroll"),
            RecruitmentButton(lang.raw("recruitment.confirm"), icon = "lg_accept") {
                require(lfg.getJoinRequirement(requireNotNull(guilds.getGuild(quote.id))) == quotedFee)
                val result =
                    if (selectedApplication != null) {
                        service.confirm(player.uniqueId, requireNotNull(selectedApplication), quote, quotedFee)
                    } else {
                        lfg.joinGuild(player.uniqueId, quote)
                    }
                when (result) {
                    is LfgJoinResult.Success -> {
                        player.sendMessage(lang.msg("recruitment.feedback.joined"))
                        go(RecruitmentScreen.MINE)
                    }
                    else -> {
                        player.sendMessage(lang.msg("recruitment.feedback.failed"))
                        go(RecruitmentScreen.MINE)
                    }
                }
            },
        )
    }

    private fun discord(): List<RecruitmentButton> {
        val guild = selectedGuild()
        val profile = service.profile(guild)
        val invite = requireNotNull(profile.discordInvite)
        return listOf(
            RecruitmentButton(
                lang.raw("recruitment.discord"),
                listOf(invite, profile.discordInstructions, lang.raw("recruitment.discord_notice")),
                "lg_announcement",
            ),
            RecruitmentButton(lang.raw("recruitment.discord_confirm"), listOf(invite), "lg_accept") {
                require(service.profile(selectedGuild()).discordInvite == invite) { "Invite changed" }
                player.closeInventory()
                player.sendMessage(Component.text(invite).clickEvent(ClickEvent.openUrl(invite)))
            },
        )
    }

    private fun settings(): List<RecruitmentButton> {
        val guild = requireNotNull(ownGuild())
        require(GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id))
        val profile = service.profile(guild)

        fun edit(
            label: String,
            value: String,
            change: (RecruitmentProfile, String) -> RecruitmentProfile,
        ) = RecruitmentButton(label, listOf(value), "lg_rename") {
            ask(
                label,
                value,
            ) {
                service.saveProfile(
                    player.uniqueId,
                    change(service.profile(requireNotNull(guilds.getGuild(guild.id))), it),
                )
            }
        }
        return listOf(
            RecruitmentButton(lang.raw("recruitment.method"), listOf(mode(profile.mode)), "lg_settings") {
                go(RecruitmentScreen.METHOD)
            },
            edit(lang.raw("recruitment.pitch"), profile.pitch) { current, value -> current.copy(pitch = value) },
            edit(
                lang.raw("recruitment.requirements"),
                profile.requirements,
            ) { current, value -> current.copy(requirements = value) },
            edit(
                lang.raw("recruitment.interests"),
                profile.interests.joinToString(","),
            ) { current, value ->
                current.copy(
                    interests = value.split(',').map(String::trim).filter(String::isNotBlank).toSet(),
                )
            },
            edit(
                lang.raw("recruitment.times"),
                profile.preferredTimes,
            ) { current, value -> current.copy(preferredTimes = value) },
            edit(
                lang.raw("recruitment.language"),
                profile.language,
            ) { current, value -> current.copy(language = value) },
            RecruitmentButton(
                lang.raw("recruitment.beginner"),
                listOf(enabled(profile.beginnerFriendly)),
                "lg_toggle_on",
            ) {
                val current = service.profile(requireNotNull(guilds.getGuild(guild.id)))
                service.saveProfile(player.uniqueId, current.copy(beginnerFriendly = !current.beginnerFriendly))
                refresh()
            },
            edit(
                lang.raw("recruitment.discord"),
                profile.discordInvite ?: "",
            ) { current, value ->
                current.copy(
                    discordInvite =
                        value.ifBlank {
                            null
                        },
                )
            },
            edit(
                lang.raw("recruitment.instructions"),
                profile.discordInstructions,
            ) { current, value -> current.copy(discordInstructions = value) },
        )
    }

    private fun methodSettings(): List<RecruitmentButton> =
        RecruitmentMode.entries.map { method ->
            RecruitmentButton(mode(method), icon = "lg_settings") {
                val guild = requireNotNull(ownGuild())
                service.saveProfile(player.uniqueId, service.profile(guild).copy(mode = method))
                go(RecruitmentScreen.SETTINGS)
            }
        }
}
