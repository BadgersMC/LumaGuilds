package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.lumalyte.lg.application.services.GuildStallInfo
import net.lumalyte.lg.application.services.GuildStallReadService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.StallReadResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.services.GuildStallLoadGuard
import net.lumalyte.lg.infrastructure.services.GuildStallReadClient
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BaseBedrockMenu
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// Literal title keys keep the existing locale source-contract scanner authoritative.

/** Read-only, paginated guild stall inventory shared by Java and Bedrock. */
@Suppress("StringLiteralDuplication")
internal class GuildStallMenu(
    private val navigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val bedrock: Boolean,
    private val client: GuildStallReadService = GuildStallReadClient(),
) : Menu,
    KoinComponent {
    private val plugin: Plugin by inject()
    private val members: MemberService by inject()
    private val lang: LangService by inject()
    private val presentation by lazy { GuildStallPresentation(lang) }
    private var selected: String? = null
    private var page = 0

    override fun open() {
        navigator.invalidateCurrentNavigation()
        if (!isMember()) {
            player.sendMessage(lang.msg("guild_stall.not_member"))
            navigator.goBack()
            return
        }
        val expected = navigator.currentNavigationToken()
        display(emptyList(), lang.gui("guild_stall.loading"), expected)
        val loadingInventory = if (bedrock) null else player.openInventory.topInventory
        client.read(guild.id, player.uniqueId).thenAccept { result ->
            if (plugin.isEnabled) {
                Bukkit.getScheduler().runTask(plugin, Runnable { acceptResult(result, expected, loadingInventory) })
            }
        }
    }

    private fun acceptResult(result: StallReadResult, expected: Long, loadingInventory: Inventory?) {
        val current = navigator.currentNavigationToken()
        if (!GuildStallLoadGuard.accepts(expected, current, player.isOnline, isMember())) return
        if (loadingInventory != null && player.openInventory.topInventory !== loadingInventory) return
        navigator.invalidateCurrentNavigation()
        val token = navigator.currentNavigationToken()
        when (result) {
            StallReadResult.Unavailable -> display(emptyList(), lang.gui("guild_stall.unavailable"), token)
            is StallReadResult.Available -> showStalls(result.stalls, token)
        }
    }

    private fun isMember(): Boolean = members.getMember(player.uniqueId, guild.id) != null

    private enum class Role { ITEM, BACK, PREVIOUS, REFRESH, NEXT, CONTROL }

    private data class Row(
        val name: Component,
        val lore: List<Component>,
        val icon: GuildStallIcon = GuildStallIcon.STALL,
        val role: Role = Role.ITEM,
        val action: () -> Unit,
    )

    private var selectedStall: GuildStallInfo? = null

    private fun showStalls(stalls: List<GuildStallInfo>, token: Long) {
        if (stalls.isEmpty()) {
            selected = null
            display(emptyList(), lang.gui("guild_stall.none"), token)
            return
        }
        val stall = selected?.let { id -> stalls.firstOrNull { it.id == id } }
        if (selected != null && stall == null) selected = null
        selectedStall = stall
        val rows =
            if (stall == null) {
                stalls.map { info ->
                    Row(stallName(info), presentation.metadata(info)) {
                        selected = info.id
                        page = 0
                        open()
                    }
                }
            } else {
                stall.members.map { member ->
                    val name = Bukkit.getOfflinePlayer(member.playerId).name ?: member.playerId.toString()
                    Row(
                        Component.text(name, NamedTextColor.GOLD),
                        member.permissions.map(presentation::permissionText),
                        GuildStallIcon.MEMBER,
                    ) { open() }
                }
            }
        val summary =
            if (stall == null) {
                lang.gui("guild_stall.choose")
            } else {
                presentation
                    .metadata(
                        stall,
                    )
                    .fold(lang.gui("guild_stall.access_note")) { text, line -> text.appendNewline().append(line) }
            }
        display(rows, summary, token)
    }

    private fun stallName(info: GuildStallInfo): Component =
        Component.text(GuildStallLayout.displayName(info.region), NamedTextColor.GOLD)

    private fun display(rows: List<Row>, summary: Component, token: Long) {
        val pageSize = when {
            bedrock -> PAGE_SIZE
            selected == null -> GuildStallLayout.LIST_PAGE
            else -> GuildStallLayout.DETAIL_PAGE
        }
        val maxPage = (rows.size - 1).coerceAtLeast(0) / pageSize
        page = page.coerceIn(0, maxPage)
        val visible = rows.drop(page * pageSize).take(pageSize)
        val controls = controls(maxPage, summary)
        if (bedrock) {
            StallForm(navigator, player, token, summary, visible + controls).open()
        } else {
            displayInventory(visible, controls, summary, maxPage, token)
        }
    }

    private fun controls(maxPage: Int, summary: Component): List<Row> {
        val navigation = navigationControls(maxPage, summary)
        val stall = selected ?: return navigation
        val manager = GuildStallManagerControls(player, guild.id, members, lang)
        return navigation + manager.rows(stall).map { Row(it.name, emptyList(), it.icon, Role.CONTROL, it.action) }
    }

    private fun navigationControls(maxPage: Int, summary: Component): List<Row> {
        val navigation =
            listOf(
                Row(lang.gui("guild_stall.back"), emptyList(), GuildStallIcon.BACK, Role.BACK) {
                    goBack()
                },
                Row(lang.gui("guild_stall.previous"), emptyList(), GuildStallIcon.PREVIOUS, Role.PREVIOUS) {
                    page = (page - 1).coerceAtLeast(0)
                    open()
                },
                Row(lang.gui("guild_stall.refresh"), summaryLines(summary), GuildStallIcon.REFRESH, Role.REFRESH) { open() },
                Row(lang.gui("guild_stall.next"), emptyList(), GuildStallIcon.NEXT, Role.NEXT) {
                    page = (page + 1).coerceAtMost(maxPage)
                    open()
                },
            )
        return navigation
    }

    private fun goBack() {
        if (selected == null) {
            navigator.goBack()
        } else {
            selected = null
            page = 0
            open()
        }
    }

    /** REQ-258: stalls centred in a 3-row menu; one stall's members centred under it in 6 rows. */
    private fun displayInventory(visible: List<Row>, controls: List<Row>, summary: Component, maxPage: Int, token: Long) {
        val stall = selectedStall?.takeIf { selected != null }
        val rows = if (stall == null) GuildStallLayout.LIST_ROWS else GuildStallLayout.DETAIL_ROWS
        val heading = if (stall == null) lang.guiTitle("guild_stall.title")
            else lang.guiTitle("guild_stall.stall_title", "stall" to GuildStallLayout.displayName(stall.region))
        val gui = ChestGui(rows, MenuTitleBuilder.build(guild.guiTheme, rows, heading))
        val pane = StaticPane(0, 0, WIDTH, rows)
        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnClose { if (navigator.isNavigationCurrent(token)) navigator.invalidateCurrentNavigation() }
        val paged = maxPage > 0
        if (stall == null) {
            if (visible.isEmpty()) {
                addRow(pane, GuildStallLayout.LIST_STATUS, Row(lang.gui("guild_stall.title"), summaryLines(summary), GuildStallIcon.STATUS) {}, token)
            }
            visible.zip(GuildStallLayout.stalls(visible.size)).forEach { (row, slot) ->
                addRow(pane, slot, row.copy(lore = row.lore + Component.empty() + lang.gui("guild_stall.open_hint")), token)
            }
            controls.forEach { row ->
                when (row.role) {
                    Role.BACK -> addRow(pane, GuildStallLayout.LIST_BACK, row, token)
                    Role.PREVIOUS -> if (paged) addRow(pane, 18, row, token)
                    Role.NEXT -> if (paged) addRow(pane, 26, row, token)
                    else -> Unit
                }
            }
        } else {
            addRow(pane, GuildStallLayout.DETAIL_STALL, Row(stallName(stall), presentation.metadata(stall), GuildStallIcon.STATUS) {}, token)
            if (visible.isEmpty()) {
                addRow(pane, 22, Row(lang.gui("guild_stall.nobody"), listOf(lang.gui("guild_stall.nobody_hint")), GuildStallIcon.MEMBER) {}, token)
            }
            visible.zip(GuildStallLayout.members(visible.size)).forEach { (row, slot) -> addRow(pane, slot, row, token) }
            val extra = GuildStallLayout.DETAIL_CONTROLS.iterator()
            controls.forEach { row ->
                when (row.role) {
                    Role.BACK -> addRow(pane, GuildStallLayout.DETAIL_BACK, row, token)
                    Role.PREVIOUS -> if (paged) addRow(pane, GuildStallLayout.DETAIL_PREVIOUS, row, token)
                    Role.NEXT -> if (paged) addRow(pane, GuildStallLayout.DETAIL_NEXT, row, token)
                    Role.CONTROL -> if (extra.hasNext()) addRow(pane, extra.next(), row, token)
                    else -> Unit
                }
            }
        }
        gui.show(player)
    }

    private fun addRow(pane: StaticPane, slot: Int, row: Row, token: Long) {
        val item = row.icon.item()
        item.editMeta { meta ->
            meta.displayName(row.name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
            meta.lore(row.lore.map { it.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE) })
        }
        pane.addItem(
            GuiItem(item) {
                if (navigator.isNavigationCurrent(token) && isMember()) row.action()
            },
            slot % WIDTH,
            slot / WIDTH,
        )
    }

    private inner class StallForm(
        menuNavigator: MenuNavigator,
        actor: Player,
        private val token: Long,
        private val summary: Component,
        private val rows: List<Row>,
    ) : BaseBedrockMenu(menuNavigator, actor, plugin.logger) {
        override fun getForm(): Form {
            val plain =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText()
            val builder =
                SimpleForm
                    .builder()
                    .title(lang.bedrock("guild_stall.title"))
                    .content(plain.serialize(summary))
            rows.forEach { row -> builder.button(buttonText(row, plain)) }
            return builder
                .validResultHandler { response ->
                    Bukkit.getScheduler().runTask(plugin, Runnable { acceptClick(response.clickedButtonId()) })
                }
                .closedOrInvalidResultHandler(
                    Runnable {
                        Bukkit.getScheduler().runTask(plugin, Runnable { acceptClose() })
                    },
                )
                .build()
        }

        private fun buttonText(
            row: Row,
            plain: net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer,
        ): String = plain.serialize(row.name) + row.lore.joinToString("") { "\n" + plain.serialize(it) }

        private fun acceptClick(index: Int) {
            if (navigator.isNavigationCurrent(token) && player.isOnline && isMember()) {
                onFormResponseReceived()
                rows.getOrNull(index)?.action?.invoke()
            }
        }

        private fun acceptClose() {
            if (navigator.isNavigationCurrent(token)) {
                onFormResponseReceived()
                navigator.goBack()
            }
        }

        override fun handleResponse(player: Player, response: Any?) = Unit
    }

    private companion object {
        const val WIDTH = 9
        const val PAGE_SIZE = 45
    }
}

private fun summaryLines(text: Component): List<Component> {
    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        .serialize(text)
        .lines()
        .map(Component::text)
}
