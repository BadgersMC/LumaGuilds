package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.HallThemeService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.rewards.HallDisplay
import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import net.lumalyte.lg.domain.values.PrestigeNumeral
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildDialogPresenter
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * REQ-261: Hall Display. Everyone in the guild can look; members who can manage the guild banner
 * change what the statue wears from the themes the guild owns, pick open themes and choose whose
 * skin it wears. Choices are saved now and used once the guild has a Hall of Prestige pedestal.
 */
internal class GuildHallDisplayMenu(
    private val navigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val prestigeCount: Int,
    private val presenter: GuildDialogPresenter = GuildMenuDialog(),
) : Menu, KoinComponent {
    private val themes: HallThemeService by inject()
    private val members: MemberService by inject()
    private val ranks: net.lumalyte.lg.application.services.RankService by inject()
    private val lang: LangService by inject()
    private val preview: net.lumalyte.lg.infrastructure.services.HallPreviewService? by lazy { getKoin().getOrNull() }
    private var skinPage = 0

    override fun open() = main()

    private fun canManage() = members.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_BANNER)

    private val loader: net.lumalyte.lg.interaction.menus.MenuLoader by lazy {
        getKoin().getOrNull() ?: net.lumalyte.lg.interaction.menus.MenuLoader.Immediate
    }

    /** REQ-266: the Hall read runs off the main thread; [show] runs back on it. */
    private fun load(show: (HallDisplay) -> Unit) {
        val token = navigator.currentNavigationToken()
        loader.load(player, { navigator.isNavigationCurrent(token) }, { themes.display(guild.id) }) { display ->
            if (display == null) player.sendMessage(lang.msg("hall.feedback.failed")) else show(display)
        }
    }

    private fun main() {
        load { display ->
            val manage = canManage()
            val cards = buildList {
                add(statueCard(display, manage))
                if (display.picksLeft > 0) {
                    add(card(ItemStack.of(org.bukkit.Material.NETHER_STAR), listOf(
                        lang.gui("hall.display.picks_title", "count" to display.picksLeft),
                        lang.gui("hall.display.picks_body"),
                    ), if (manage) action(lang.gui("hall.display.pick_action"), "lg_prestige") { pick() } else null))
                }
                if (display.owned.isEmpty()) {
                    add(referenceItem("lg_prestige", listOf(lang.gui("hall.display.none_title"), lang.gui("hall.display.none_body"))))
                } else {
                    HallSlot.entries.forEach { slot ->
                        val theme = display.wearing(slot) ?: return@forEach
                        add(card(ItemStack.of(HallThemeText.icon(theme)), listOf(
                            lang.gui("hall.display.slot", "slot" to slot.label),
                            lang.gui("hall.display.slot_value", "value" to theme.cosmetic(slot), "theme" to theme.label),
                        ), if (manage) action(Component.text(slot.label), "lg_nav_settings") { slot(slot) } else null))
                    }
                    add(card(ItemStack.of(org.bukkit.Material.ARMOR_STAND), listOf(
                        lang.gui("hall.display.skin"),
                        lang.gui("hall.display.skin_value", "name" to skinName(display)),
                    ), if (manage) action(lang.gui("hall.display.skin"), "lg_nav_members") { skinPage = 0; skin() } else null))
                }
            }
            val actions = buildList {
                if (preview != null) add(action(lang.gui("hall.display.preview"), "lg_prestige") { previewInWorld() })
                if (manage && display.owned.size > 1) add(action(lang.gui("hall.display.wear_all"), "lg_prestige") { wearAll() })
            }
            show(cards, actions, lang.gui("hall.display.subtitle", "guild" to guild.name,
                "tier" to PrestigeNumeral.of(prestigeCount), "owned" to display.owned.size), back = navigator::goBack)
        }
    }

    private fun statueCard(display: HallDisplay, manage: Boolean): GuildDialogItem {
        val nameplate = display.wearing(HallSlot.TITLE)?.cosmetic(HallSlot.TITLE)?.replace("Name", guild.name) ?: guild.name
        val lines = buildList {
            add(lang.gui("hall.display.statue_name", "name" to nameplate))
            add(lang.gui("hall.display.statue_tier", "tier" to PrestigeNumeral.of(prestigeCount)))
            add(lang.gui("hall.display.statue_skin", "name" to skinName(display)))
            display.wearing(HallSlot.POSE)?.let { pose ->
                add(lang.gui("hall.display.statue_look", "pose" to pose.cosmetic(HallSlot.POSE),
                    "aura" to (display.wearing(HallSlot.AURA) ?: pose).cosmetic(HallSlot.AURA)))
            }
            add(lang.gui("hall.display.statue_note"))
            if (!manage) add(lang.gui("hall.display.view_only"))
        }
        return referenceItem("lg_prestige", lines)
    }

    private fun slot(slot: HallSlot) {
        load { display ->
            val cards = HallTheme.entries.map { theme ->
                val owned = theme in display.owned
                val wearing = display.wearing(slot) == theme
                card(ItemStack.of(HallThemeText.icon(theme)), listOf(
                    lang.gui("hall.display.option", "value" to theme.cosmetic(slot)),
                    when {
                        wearing -> lang.gui("hall.display.option_wearing")
                        owned -> lang.gui("hall.display.option_owned", "theme" to theme.label)
                        else -> lang.gui("hall.display.option_locked")
                    },
                ), if (owned && !wearing) action(Component.text(theme.cosmetic(slot)), "lg_confirm") {
                    feedback(themes.wear(player.uniqueId, guild.id, slot, theme))
                    main()
                } else null)
            }
            val actions = listOf(action(lang.gui("hall.display.use_default"), "lg_cancel") {
                feedback(themes.wear(player.uniqueId, guild.id, slot, null))
                main()
            })
            show(cards, actions, lang.gui("hall.display.slot_subtitle", "slot" to slot.label), back = ::main)
        }
    }

    private fun wearAll() {
        load { display ->
            val cards = display.owned.map { theme ->
                card(ItemStack.of(HallThemeText.icon(theme)), HallThemeText.cosmeticLines(lang, theme).take(1) +
                    lang.gui("hall.pick.name", "theme" to theme.label),
                    action(lang.gui("hall.pick.name", "theme" to theme.label), "lg_confirm") {
                        feedback(themes.wearAll(player.uniqueId, guild.id, theme))
                        main()
                    })
            }
            show(cards, emptyList(), lang.gui("hall.display.wear_all_subtitle"), back = ::main)
        }
    }

    private fun pick() {
        load { display ->
            val cards = display.unowned.map { theme ->
                card(ItemStack.of(HallThemeText.icon(theme)),
                    listOf(lang.gui("hall.pick.name", "theme" to theme.label)) + HallThemeText.cosmeticLines(lang, theme).drop(1),
                    action(lang.gui("hall.pick.name", "theme" to theme.label), "lg_confirm") {
                        feedback(themes.earn(player.uniqueId, guild.id, theme))
                        main()
                    })
            }
            show(cards, emptyList(), lang.gui("hall.display.pick_subtitle", "count" to display.picksLeft), back = ::main)
        }
    }

    private fun skin() {
        val ids = members.getGuildMembers(guild.id).map { it.playerId }.sortedBy { Bukkit.getOfflinePlayer(it).name ?: "" }
        val pages = ((ids.size + SKIN_PAGE - 1) / SKIN_PAGE).coerceAtLeast(1)
        skinPage = skinPage.coerceIn(0, pages - 1)
        val cards = ids.drop(skinPage * SKIN_PAGE).take(SKIN_PAGE).map { id ->
            val name = Bukkit.getOfflinePlayer(id).name ?: id.toString()
            referenceItem("lg_nav_members", listOf(Component.text(name)), action(Component.text(name), "lg_nav_members") {
                feedback(themes.setSkin(player.uniqueId, guild.id, id))
                main()
            })
        }
        val actions = buildList {
            add(action(lang.gui("hall.display.skin_owner"), "lg_home") {
                feedback(themes.setSkin(player.uniqueId, guild.id, null))
                main()
            })
            if (skinPage > 0) add(action(lang.gui("chronicle.earlier"), "lg_page_prev") { skinPage--; skin() })
            if (skinPage + 1 < pages) add(action(lang.gui("chronicle.later"), "lg_page_next") { skinPage++; skin() })
        }
        show(cards, actions, lang.gui("hall.display.skin_subtitle", "page" to skinPage + 1, "pages" to pages), back = ::main)
    }

    /** REQ-264: closes the dialog and builds a 30-second statue only this player can see. */
    private fun previewInWorld() {
        load { display ->
            player.closeInventory()
            val service = preview ?: return@load
            service.show(player, guild.name, prestigeCount, display, display.skin ?: owner())
            val pose = display.wearing(HallSlot.POSE)?.cosmetic(HallSlot.POSE)
            player.sendMessage(lang.msg("hall.feedback.preview", "seconds" to net.lumalyte.lg.infrastructure.services.HallPreviewService.SECONDS,
                "pose" to (pose ?: "-")))
        }
    }

    private fun skinName(display: HallDisplay): String {
        val id: UUID? = display.skin ?: owner()
        return id?.let { Bukkit.getOfflinePlayer(it).name } ?: guild.name
    }

    /** The statue wears the guild owner's skin until someone picks another member. */
    private fun owner(): UUID? = runCatching {
        val top = ranks.getHighestRank(guild.id) ?: return@runCatching null
        members.getGuildMembers(guild.id).firstOrNull { it.rankId == top.id }?.playerId
    }.getOrNull()

    private fun feedback(outcome: HallThemeService.Outcome) {
        player.sendMessage(when (outcome) {
            HallThemeService.Outcome.DONE -> lang.msg("hall.feedback.saved")
            HallThemeService.Outcome.NOT_ALLOWED -> lang.msg("hall.feedback.not_allowed")
            HallThemeService.Outcome.NO_PICKS -> lang.msg("hall.feedback.no_picks")
            HallThemeService.Outcome.NOT_OWNED -> lang.msg("hall.feedback.not_owned")
            HallThemeService.Outcome.NOT_A_MEMBER -> lang.msg("hall.feedback.not_a_member")
            HallThemeService.Outcome.FAILED -> lang.msg("hall.feedback.failed")
        })
    }

    private fun card(item: ItemStack, lines: List<Component>, action: GuildDialogAction?) =
        GuildDialogItem(item, Component.join(JoinConfiguration.newlines(), lines), lines, action)

    private fun action(label: Component, icon: String, run: () -> Unit) = referenceAction(label, icon) {
        if (valid()) run()
    }

    private fun valid() = player.isOnline && members.getMember(player.uniqueId, guild.id) != null

    private fun show(items: List<GuildDialogItem>, actions: List<GuildDialogAction>, subtitle: Component, back: () -> Unit) {
        val token = navigator.currentNavigationToken()
        val theme = getKoin().getOrNull<GuildService>()?.getGuild(guild.id)?.guiTheme ?: guild.guiTheme
        val page = GuildDialogPage(lang.gui("hall.display.title"), items, actions,
            referenceAction(lang.gui("recruitment.dialog.back"), "lg_back", back),
            columns = 2, theme = theme, subtitle = subtitle)
        if (!presenter.show(player, page, { valid() && navigator.isNavigationCurrent(token) }, { player.sendMessage(lang.msg("hall.feedback.failed")) })) {
            player.sendMessage(lang.msg("hall.feedback.failed"))
        }
    }

    private companion object {
        const val SKIN_PAGE = 8
    }
}
