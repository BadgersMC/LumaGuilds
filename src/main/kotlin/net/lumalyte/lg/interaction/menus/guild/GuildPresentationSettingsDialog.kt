package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildDialogPresenter
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.utils.GuiTheme
import org.bukkit.entity.Player

/** Saved preferences and a preview/apply frame flow use existing guild settings authority. */
internal class GuildPresentationSettingsDialog(
    private val player: Player,
    private val guild: () -> Guild,
    private val guilds: GuildService,
    private val recruitment: GuildRecruitmentService,
    private val lang: LangService,
    private val valid: () -> Boolean,
    private val unlocked: (GuiTheme) -> Boolean,
    private val more: () -> Unit,
    private val back: () -> Unit,
    private val dialog: GuildDialogPresenter = GuildMenuDialog(),
) {
    private var frames = false
    private var preview: GuiTheme? = null

    fun open(): Boolean {
        val current = guild()
        val profile = recruitment.listings.profile(current)
        val page = if (frames) frames(current) else settings(current, profile)
        return dialog.show(player, page, { player.isOnline && valid() }) {
            player.sendMessage(lang.msg("recruitment.feedback.failed"))
        }
    }

    private fun settings(
        current: Guild,
        profile: RecruitmentProfile,
    ): GuildDialogPage {
        val entries =
            listOf(
                Triple(
                    "public",
                    profile.publicListing,
                    { p: RecruitmentProfile -> p.copy(publicListing = !p.publicListing) },
                ),
                Triple(
                    "announcements",
                    profile.joinAnnouncements,
                    { p: RecruitmentProfile -> p.copy(joinAnnouncements = !p.joinAnnouncements) },
                ),
                Triple("banner", profile.showBanner, { p: RecruitmentProfile -> p.copy(showBanner = !p.showBanner) }),
                Triple(
                    "reminders",
                    profile.questReminders,
                    { p: RecruitmentProfile -> p.copy(questReminders = !p.questReminders) },
                ),
            )
        val cards =
            entries.map { (key, enabled, change) ->
                val settingKey = "dialog_v2.setting_$key"
                val descriptionKey = settingKey + "_description"
                val title = lang.gui(settingKey)
                referenceItem(
                    if (enabled) "lg_toggle_on" else "lg_toggle_off",
                    listOf(
                        title,
                        lang.gui(descriptionKey),
                    ),
                    if (!canEdit(current)) {
                        null
                    } else {
                        referenceAction(title, "lg_nav_settings") {
                            recruitment.listings.savePresentation(player.uniqueId, current.id, change)
                            open()
                        }
                    },
                    right = listOf(lang.gui(if (enabled) "dialog_v2.on" else "dialog_v2.off")),
                )
            }
        return GuildDialogPage(
            lang.gui("dialog_v2.guild_settings"),
            cards,
            listOf(
                referenceAction(lang.gui("dialog_v2.guild_frame"), "lg_tag") {
                    frames = true
                    preview = null
                    open()
                },
                referenceAction(lang.gui("dialog_v2.more_settings"), "lg_nav_settings", more),
            ),
            referenceAction(lang.gui("recruitment.dialog.back"), "lg_back", back),
            theme = current.guiTheme,
            subtitle = Component.text(current.name),
            columns = 2,
            framedHeading = true,
        )
    }

    private fun frames(current: Guild): GuildDialogPage {
        val chosen = preview ?: current.guiTheme.resolved()
        val cards =
            GuiTheme.SELECTABLE.filter { it != GuiTheme.VANILLA }.map { theme ->
                val available = unlocked(theme)
                val title = Component.text(theme.displayName)
                val status =
                    when {
                        !available -> "dialog_v2.frame_locked"
                        theme == current.guiTheme.resolved() -> "dialog_v2.frame_current"
                        theme == chosen -> "dialog_v2.frame_preview"
                        else -> "dialog_v2.frame_choose"
                    }
                val hint = when {
                    !available -> lang.gui("dialog_v2.frame_locked")
                    theme == current.guiTheme.resolved() -> lang.gui("dialog_v2.frame_current")
                    else -> lang.gui("dialog_v2.frame_choose")
                }
                referenceItem(
                    "lg_theme_${theme.name.lowercase()}",
                    listOf(title, hint),
                    if (!available || theme == chosen) {
                        null
                    } else {
                        referenceAction(title, "lg_tag") {
                            preview = theme
                            open()
                        }
                    },
                    right = listOf(if (theme == chosen || !available) lang.gui(status) else Component.empty()),
                )
            }
        val actions =
            if (!canEdit(current) || preview == null || chosen == current.guiTheme.resolved()) {
                listOf(referenceAction(Component.text(GuiTheme.VANILLA.displayName), "lg_tag") {
                    preview = GuiTheme.VANILLA
                    open()
                })
            } else {
                listOf(
                    referenceAction(lang.gui("dialog_v2.apply_frame"), "lg_confirm") {
                        check(unlocked(chosen)) { "This theme is no longer unlocked" }
                        check(
                            guilds.setGuiTheme(current.id, chosen, player.uniqueId),
                        ) { "Theme permission or availability changed" }
                        preview = null
                        frames = false
                        open()
                    },
                )
            }
        return GuildDialogPage(
            lang.gui("dialog_v2.guild_frame"),
            cards,
            actions,
            referenceAction(lang.gui("recruitment.dialog.back"), "lg_back") {
                frames = false
                preview = null
                open()
            },
            theme = chosen,
            subtitle = lang.gui("dialog_v2.frame_subtitle"),
            columns = 2,
            framedHeading = true,
        )
    }

    private fun canEdit(current: Guild): Boolean =
        guilds.hasPermission(
            player.uniqueId,
            current.id,
            net.lumalyte.lg.domain.entities.RankPermission.MANAGE_GUILD_SETTINGS,
        )
}
