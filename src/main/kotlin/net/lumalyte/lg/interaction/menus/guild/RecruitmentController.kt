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
    SETUP,
    SETUP_PREVIEW,
    REVIEW_CONFIRM,
    MANAGE,
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
    val icon: String = "lg_nav_members",
    val guild: Guild? = null,
    val action: () -> Unit = {
    },
)

internal data class RecruitmentView(
    val title: String,
    val items: List<RecruitmentButton>,
    val navigation: List<RecruitmentButton>,
    val quickFilters: List<RecruitmentButton> = emptyList(),
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
    private var initialized = false
    private var browsePage = 0
    private var interest: String? = null
    private var sorting = 0
    private var setupDraft: RecruitmentProfile? = null
    private var setupMode = RecruitmentMode.CLOSED
    private var reviewApprove = false
    private var reviewReason = ""
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
        if (!initialized) {
            initialized = true
            if (ownGuild() != null) screen = RecruitmentScreen.MANAGE
        }
        val navigation =
            mutableListOf(
                RecruitmentButton(lang.raw("recruitment.browse"), icon = "lg_back") { go(RecruitmentScreen.BROWSE) },
                RecruitmentButton(lang.raw("recruitment.mine"), icon = "lg_description") { go(RecruitmentScreen.MINE) },
                RecruitmentButton(lang.raw("recruitment.search"), listOf(query), "lg_tag") {
                    ask(lang.raw("recruitment.search"), query) {
                        query = it.take(64)
                        browsePage = 0
                        go(RecruitmentScreen.BROWSE)
                    }
                },
                RecruitmentButton(lang.raw("recruitment.filter"), listOf(guildMode()), "lg_nav_settings") {
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
            navigation.removeAt(1)
            navigation.add(1, RecruitmentButton(lang.raw("recruitment.manage"), icon = "lg_nav_members") {
                go(RecruitmentScreen.MANAGE)
            })
            if (GuildRecruitmentPermissions.canReview(members, player.uniqueId, guild.id)) {
                navigation +=
                    RecruitmentButton(lang.raw("recruitment.inbox"), icon = "lg_nav_members") {
                        go(RecruitmentScreen.INBOX)
                    }
            }
            if (GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id)) {
                navigation +=
                    RecruitmentButton(lang.raw("recruitment.settings"), icon = "lg_nav_settings") {
                        go(RecruitmentScreen.SETTINGS)
                    }
            }
        }
        val items =
            when (screen) {
                RecruitmentScreen.SETUP -> setupMethods()
                RecruitmentScreen.SETUP_PREVIEW -> setupPreview()
                RecruitmentScreen.REVIEW_CONFIRM -> reviewConfirmation()
                RecruitmentScreen.MANAGE -> management()
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
            if (screen == RecruitmentScreen.MANAGE) lang.raw("recruitment.manage") else lang.raw("recruitment.title"),
            items.ifEmpty {
                listOf(RecruitmentButton(lang.raw("recruitment.empty")))
            },
            navigation,
            if (screen == RecruitmentScreen.BROWSE) quickFilters() else emptyList(),
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
        if (screen == RecruitmentScreen.BROWSE) browsePage = page
        screen = next
        page = if (next == RecruitmentScreen.BROWSE) browsePage else 0
        refresh()
    }

    private fun management(): List<RecruitmentButton> {
        val guild = ownGuild() ?: return browse()
        return buildList {
            add(RecruitmentButton(lang.raw("recruitment.preview"), details(guild), "lg_nav_info", guild) {
                selected = guild.id
                go(RecruitmentScreen.PROFILE)
            })
            if (GuildRecruitmentPermissions.canReview(members, player.uniqueId, guild.id)) {
                add(RecruitmentButton(lang.raw("recruitment.inbox"), icon = "lg_nav_members") { go(RecruitmentScreen.INBOX) })
            }
            if (GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id)) {
                add(RecruitmentButton(lang.raw("recruitment.setup"), icon = "lg_tag") {
                    setupDraft = service.profile(guild).copy(mode = RecruitmentMode.CLOSED)
                    go(RecruitmentScreen.SETUP)
                })
                add(RecruitmentButton(lang.raw("recruitment.settings"), icon = "lg_nav_settings") { go(RecruitmentScreen.SETTINGS) })
            }
            add(RecruitmentButton(lang.raw("recruitment.browse"), icon = "lg_nav_info") { go(RecruitmentScreen.BROWSE) })
        }
    }

    private fun setupMethods(): List<RecruitmentButton> = RecruitmentMode.entries.map { method ->
        RecruitmentButton(mode(method), icon = "lg_nav_settings") {
            val guild = requireNotNull(ownGuild())
            require(GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id))
            setupMode = method
            setupInput(0)
        }
    }

    private fun setupInput(step: Int) {
        val draft = requireNotNull(setupDraft)
        val fields = listOf(
            lang.raw("recruitment.pitch") to draft.pitch,
            lang.raw("recruitment.requirements") to draft.requirements,
            lang.raw("recruitment.interests") to draft.interests.joinToString(","),
            lang.raw("recruitment.times") to draft.preferredTimes,
            lang.raw("recruitment.language") to draft.language,
            lang.raw("recruitment.discord") to (draft.discordInvite ?: ""),
            lang.raw("recruitment.instructions") to draft.discordInstructions,
        )
        val count = if (setupMode == RecruitmentMode.DISCORD) 7 else 5
        if (step == count) { go(RecruitmentScreen.SETUP_PREVIEW); return }
        ask("${step + 1}/$count ${fields[step].first}", fields[step].second) { value ->
            val current = requireNotNull(setupDraft)
            setupDraft = when (step) {
                0 -> current.copy(pitch = value)
                1 -> current.copy(requirements = value)
                2 -> current.copy(interests = value.split(',').map(String::trim).filter(String::isNotBlank).toSet())
                3 -> current.copy(preferredTimes = value)
                4 -> current.copy(language = value)
                5 -> current.copy(discordInvite = value.ifBlank { null })
                else -> current.copy(discordInstructions = value)
            }
            setupInput(step + 1)
        }
    }

    private fun setupPreview(): List<RecruitmentButton> {
        val guild = requireNotNull(ownGuild())
        require(GuildRecruitmentPermissions.canEditSettings(members, player.uniqueId, guild.id))
        val draft = requireNotNull(setupDraft)
        return listOf(
            RecruitmentButton(lang.raw("recruitment.preview"), listOf(mode(setupMode), draft.pitch,
                draft.requirements, draft.interests.joinToString(", "), draft.preferredTimes, draft.language,
                draft.discordInvite ?: "", draft.discordInstructions).filter(String::isNotBlank), "lg_nav_info", guild),
            RecruitmentButton(lang.raw("recruitment.beginner"), listOf(enabled(draft.beginnerFriendly)), "lg_toggle_on") {
                setupDraft = draft.copy(beginnerFriendly = !draft.beginnerFriendly); refresh()
            },
            RecruitmentButton(lang.raw("recruitment.publish"), icon = "lg_confirm") {
                if (setupMode == RecruitmentMode.DISCORD && (draft.discordInvite == null || draft.discordInstructions.isBlank())) {
                    player.sendMessage(lang.msg("recruitment.discord_setup")); setupInput(5)
                } else {
                    service.saveProfile(player.uniqueId, draft.copy(mode = setupMode))
                    setupDraft = null; go(RecruitmentScreen.MANAGE)
                }
            },
            RecruitmentButton(lang.raw("recruitment.discard"), icon = "lg_cancel") {
                setupDraft = null; go(RecruitmentScreen.MANAGE)
            },
        )
    }

    private fun resetBrowse() {
        page = 0
        browsePage = 0
        refresh()
    }

    private fun quickFilters(): List<RecruitmentButton> = listOf(
        RecruitmentButton(lang.raw("recruitment.filter_recruiting"), listOf(enabled(recruitingOnly)), "lg_confirm") {
            recruitingOnly = !recruitingOnly; resetBrowse()
        },
        RecruitmentButton(lang.raw("recruitment.filter_beginner"), listOf(enabled(beginnersOnly)), "lg_nav_members") {
            beginnersOnly = !beginnersOnly; resetBrowse()
        },
        RecruitmentButton(lang.raw("recruitment.building"), listOf(enabled(interest == "building")), "lg_home") {
            interest = if (interest == "building") null else "building"; resetBrowse()
        },
        RecruitmentButton(lang.raw("recruitment.pvp"), listOf(enabled(interest == "pvp")), "lg_nav_warfare") {
            interest = if (interest == "pvp") null else "pvp"; resetBrowse()
        },
        RecruitmentButton(lang.raw("recruitment.mode.discord"), listOf(enabled(joiningMethod == RecruitmentMode.DISCORD)), "lg_invite") {
            joiningMethod = if (joiningMethod == RecruitmentMode.DISCORD) null else RecruitmentMode.DISCORD; resetBrowse()
        },
        RecruitmentButton(lang.raw("recruitment.sort"), listOf(when (sorting) {
            1 -> lang.raw("recruitment.sort_1")
            2 -> lang.raw("recruitment.sort_2")
            else -> lang.raw("recruitment.sort_0")
        }), "lg_sort") {
            sorting = (sorting + 1) % 3; resetBrowse()
        },
        RecruitmentButton(lang.raw("recruitment.reset"), icon = "lg_cancel") {
            query = ""; peaceful = null; recruitingOnly = true; beginnersOnly = false
            joiningMethod = null; interest = null; sorting = 0; resetBrowse()
        },
    )

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
            "lg_description",
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
                (interest == null || profile.interests.any { it.equals(interest, true) }) &&
                (guild.name + " " + profile.pitch + " " + profile.interests.joinToString(" ")).contains(query, true)
        }.sortedWith(
            when (sorting) {
                1 -> compareByDescending<Guild> { it.level }.thenBy { it.name.lowercase() }
                2 -> compareBy<Guild> { it.name.lowercase() }
                else -> compareByDescending<Guild> {
                    service.profile(it).mode != RecruitmentMode.CLOSED && service.spaces(it.id) > 0
                }.thenByDescending { guild ->
                    members.getGuildMembers(guild.id).count { member ->
                        org.bukkit.Bukkit.getPlayer(member.playerId)?.isOnline == true &&
                            GuildRecruitmentPermissions.canReview(members, member.playerId, guild.id)
                    }
                }.thenBy { it.name.lowercase() }
            },
        ).map { guild ->
            RecruitmentButton(guild.name, details(guild), "lg_nav_info", guild) {
                selectedApplication = null
                selected = guild.id
                go(RecruitmentScreen.PROFILE)
            }
        }

    private fun filters(): List<RecruitmentButton> =
        listOf(
            RecruitmentButton(lang.raw("recruitment.filter"), listOf(guildMode()), "lg_nav_settings") {
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
                "lg_nav_settings",
            ) {
                recruitingOnly = !recruitingOnly
                refresh()
            },
            RecruitmentButton(lang.raw("recruitment.filter_beginner"), listOf(enabled(beginnersOnly)), "lg_nav_settings") {
                beginnersOnly = !beginnersOnly
                refresh()
            },
            RecruitmentButton(
                lang.raw("recruitment.method"),
                listOf(joiningMethod?.let(::mode) ?: lang.raw("recruitment.any")),
                "lg_nav_settings",
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
        val items = mutableListOf(RecruitmentButton(guild.name, details(guild), "lg_nav_info", guild))
        if (service.spaces(guild.id) > 0 && members.getPlayerGuilds(player.uniqueId).isEmpty()) {
            when (profile.mode) {
                RecruitmentMode.JOIN_IMMEDIATELY ->
                    items +=
                        RecruitmentButton(mode(profile.mode), icon = "lg_confirm") {
                            quotedGuild = guild
                            quotedFee = lfg.getJoinRequirement(guild)
                            go(RecruitmentScreen.CONFIRM)
                        }
                RecruitmentMode.IN_GAME ->
                    items +=
                        RecruitmentButton(mode(profile.mode), icon = "lg_description") {
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
                RecruitmentButton(lang.raw("recruitment.discord"), icon = "lg_invite") {
                    go(RecruitmentScreen.DISCORD)
                }
        }
        return items
    }

    private fun applicationButton(application: GuildApplication): RecruitmentButton {
        val name = guilds.getGuild(application.guildId)?.name ?: application.guildId.toString()
        return RecruitmentButton(
            name,
            listOf(status(application.status), lang.raw("recruitment.submitted_at") + application.createdAt,
                lang.raw("recruitment.expires_at") + application.expiresAt, application.decisionReason),
            "lg_description",
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
                RecruitmentButton(lang.raw("recruitment.offer"), icon = "lg_confirm") {
                    quotedGuild = selectedGuild()
                    quotedFee = lfg.getJoinRequirement(requireNotNull(quotedGuild))
                    go(RecruitmentScreen.CONFIRM)
                }
        }
        if (application.status == ApplicationStatus.ADMISSION_PENDING) {
            items += RecruitmentButton(lang.raw("recruitment.pending"), icon = "lg_description")
        }
        return items
    }

    private fun inbox(): List<RecruitmentButton> {
        val guild = requireNotNull(ownGuild())
        return service.inbox(player.uniqueId, guild.id).filter { it.status == ApplicationStatus.SUBMITTED }.map {
                application ->
            RecruitmentButton(
                org.bukkit.Bukkit.getOfflinePlayer(application.applicantId).name ?: application.applicantId.toString(),
                listOf(status(application.status), lang.raw("recruitment.submitted_at") + application.createdAt,
                    lang.raw("recruitment.expires_at") + application.expiresAt),
                "lg_nav_members",
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
            RecruitmentButton(lang.raw("recruitment.submitted_at") + application.createdAt,
                listOf(status(application.status), lang.raw("recruitment.expires_at") + application.expiresAt), "lg_history"),
            RecruitmentButton(lang.raw("recruitment.send_offer"), icon = "lg_confirm") {
                reviewApprove = true; reviewReason = ""; go(RecruitmentScreen.REVIEW_CONFIRM)
            },
            RecruitmentButton(lang.raw("recruitment.decline"), icon = "lg_cancel") {
                ask(lang.raw("recruitment.reason")) {
                    reviewApprove = false; reviewReason = it; go(RecruitmentScreen.REVIEW_CONFIRM)
                }
            },
        )
    }

    private fun reviewConfirmation(): List<RecruitmentButton> {
        val guild = requireNotNull(ownGuild())
        val application = requireNotNull(service.inbox(player.uniqueId, guild.id).firstOrNull { it.id == selectedApplication })
        return listOf(
            answers(application),
            RecruitmentButton(if (reviewApprove) lang.raw("recruitment.send_offer") else lang.raw("recruitment.decline"),
                listOf(reviewReason), "lg_confirm") {
                service.review(player.uniqueId, application.id, reviewApprove, reviewReason)
                go(RecruitmentScreen.INBOX)
            },
            RecruitmentButton(lang.raw("recruitment.cancel_review"), icon = "lg_back") { go(RecruitmentScreen.REVIEW) },
        )
    }

    private fun confirm(): List<RecruitmentButton> {
        val quote = requireNotNull(quotedGuild)
        return listOf(
            RecruitmentButton(quote.name, details(quote), "lg_nav_info", quote),
            RecruitmentButton(lang.raw("recruitment.confirm"), icon = "lg_confirm") {
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
                "lg_invite",
            ),
            RecruitmentButton(lang.raw("recruitment.discord_confirm"), listOf(invite), "lg_confirm") {
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
        ) = RecruitmentButton(label, listOf(value), "lg_tag") {
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
            RecruitmentButton(lang.raw("recruitment.method"), listOf(mode(profile.mode)), "lg_nav_settings") {
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
            RecruitmentButton(mode(method), icon = "lg_nav_settings") {
                val guild = requireNotNull(ownGuild())
                val profile = service.profile(guild)
                if (method == RecruitmentMode.DISCORD && (profile.discordInvite == null || profile.discordInstructions.isBlank())) {
                    player.sendMessage(lang.msg("recruitment.discord_setup"))
                    go(RecruitmentScreen.SETTINGS)
                    return@RecruitmentButton
                }
                service.saveProfile(player.uniqueId, profile.copy(mode = method))
                go(RecruitmentScreen.SETTINGS)
            }
        }
}
