package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
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
    val icon: String = RecruitmentIcons.NAV_MEMBERS,
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
internal class RecruitmentController(
    internal val player: Player,
    internal val refresh: () -> Unit,
    internal val returnGuard: () -> (() -> Boolean) = { { true } },
) : KoinComponent {
    internal val service: GuildRecruitmentService by inject()
    internal val guilds: GuildService by inject()
    internal val members: MemberService by inject()
    internal val lfg: LfgService by inject()
    internal val directory: RecruitmentDirectoryRepository by inject()
    internal val lang: LangService by inject()
    internal var screen = RecruitmentScreen.BROWSE
    internal var initialized = false
    internal var browsePage = 0
    internal var interest: String? = null
    internal var sorting = 0
    internal var languageFilter = ""
    internal var timeFilter = ""
    internal var setupDraft: RecruitmentProfile? = null
    internal var setupMode = RecruitmentMode.CLOSED
    internal var reviewApprove = false
    internal var reviewReason = ""
    internal var selected: UUID? = null
    internal var selectedApplication: UUID? = null
    internal var quotedGuild: Guild? = null
    internal var quotedFee: JoinRequirement? = null
    internal var query = ""
    internal var peaceful: Boolean? = null
    internal var recruitingOnly = true
    internal var beginnersOnly = false
    internal var joiningMethod: RecruitmentMode? = null
    var page = 0
    var input: RecruitmentInput? = null

    private val renderers: Map<RecruitmentScreen, () -> List<RecruitmentButton>> by lazy {
        mapOf(
            RecruitmentScreen.SETUP to ::setupMethods,
            RecruitmentScreen.SETUP_PREVIEW to ::setupPreview,
            RecruitmentScreen.REVIEW_CONFIRM to ::reviewConfirmation,
            RecruitmentScreen.MANAGE to ::management,
            RecruitmentScreen.BROWSE to ::browse,
            RecruitmentScreen.FILTER to ::filters,
            RecruitmentScreen.PROFILE to ::profile,
            RecruitmentScreen.MINE to { service.mine(player.uniqueId).map(::applicationButton) },
            RecruitmentScreen.APPLICATION to ::applicantActions,
            RecruitmentScreen.INBOX to ::inbox,
            RecruitmentScreen.REVIEW to ::review,
            RecruitmentScreen.SETTINGS to ::settings,
            RecruitmentScreen.METHOD to ::methodSettings,
            RecruitmentScreen.CONFIRM to ::confirm,
            RecruitmentScreen.DISCORD to ::discord,
        )
    }

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
            )
                .warn("Recruitment action failed for {}", player.uniqueId, error)
            player.sendMessage(recruitmentRejectionMessage(lang, error))
        }
    }

    fun view(): RecruitmentView {
        if (!initialized) {
            initialized = true
            if (ownGuild() != null) screen = RecruitmentScreen.MANAGE
        }
        val navigation = navigation()
        val items = requireNotNull(renderers[screen]).invoke()
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
        run {
            try {
                current.accept(value.trim())
            } catch (error: IllegalArgumentException) {
                org.slf4j.LoggerFactory.getLogger(javaClass).debug("Recruitment input validation failed", error)
                input = current
                player.sendMessage(recruitmentRejectionMessage(lang, error, input = true))
            }
        }
        refresh()
    }

    fun cancelInput() {
        input = null
        refresh()
    }

    fun theme(): net.lumalyte.lg.utils.GuiTheme = ownGuild()?.guiTheme ?: net.lumalyte.lg.utils.GuiTheme.DEFAULT
}
