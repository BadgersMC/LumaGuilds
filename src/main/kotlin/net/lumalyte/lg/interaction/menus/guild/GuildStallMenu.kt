package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
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
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
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

    private data class Row(val name: Component, val lore: List<Component>, val action: () -> Unit)

    private fun showStalls(stalls: List<GuildStallInfo>, token: Long) {
        if (stalls.isEmpty()) {
            selected = null
            display(emptyList(), lang.gui("guild_stall.none"), token)
            return
        }
        val stall = selected?.let { id -> stalls.firstOrNull { it.id == id } }
        if (selected != null && stall == null) selected = null
        val rows =
            if (stall == null) {
                stalls.map { info ->
                    Row(Component.text(info.region), presentation.metadata(info)) {
                        selected = info.id
                        page = 0
                        open()
                    }
                }
            } else {
                stall.members.map { member ->
                    val name = Bukkit.getOfflinePlayer(member.playerId).name ?: member.playerId.toString()
                    Row(Component.text(name), member.permissions.map(presentation::permissionText)) { open() }
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

    private fun display(rows: List<Row>, summary: Component, token: Long) {
        val maxPage = (rows.size - 1).coerceAtLeast(0) / PAGE_SIZE
        page = page.coerceIn(0, maxPage)
        val visible = rows.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        val controls = controls(maxPage, summary)
        if (bedrock) {
            StallForm(navigator, player, token, summary, visible + controls).open()
        } else {
            displayInventory(visible, controls, summary, token)
        }
    }

    private fun controls(
        maxPage: Int,
        summary: Component,
    ): List<Row> {
        val navigation =
            listOf(
                Row(lang.gui("guild_stall.back"), emptyList()) {
                    goBack()
                },
                Row(lang.gui("guild_stall.previous"), emptyList()) {
                    page = (page - 1).coerceAtLeast(0)
                    open()
                },
                Row(lang.gui("guild_stall.refresh"), summaryLines(summary)) { open() },
                Row(lang.gui("guild_stall.next"), emptyList()) {
                    page = (page + 1).coerceAtMost(maxPage)
                    open()
                },
            )
        val stall = selected
        if (stall == null) return navigation
        if (!canShowSales(stall)) return navigation
        return navigation +
            Row(lang.gui("community.stall.sales"), emptyList()) {
                if (members.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.EDIT_SHOP_STOCK)) {
                    player.closeInventory()
                    player.performCommand("guildsales $stall")
                }
            }
    }

    private fun canShowSales(stall: String): Boolean {
        if (!stall.matches(Regex("[A-Za-z0-9_.:-]+"))) return false
        if (Bukkit.getCommandMap().getCommand("guildsales") == null) return false
        return members.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.EDIT_SHOP_STOCK)
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

    private fun displayInventory(visible: List<Row>, controls: List<Row>, summary: Component, token: Long) {
        val title = MenuTitleBuilder.build(guild.guiTheme, MENU_ROWS, lang.guiTitle("guild_stall.title"))
        val gui = ChestGui(MENU_ROWS, title)
        val pane = StaticPane(0, 0, WIDTH, MENU_ROWS)
        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnClose { if (navigator.isNavigationCurrent(token)) navigator.invalidateCurrentNavigation() }
        visible.forEachIndexed { index, row -> addRow(pane, index, row, token) }
        controls.forEachIndexed { index, row -> addRow(pane, PAGE_SIZE + index, row, token) }
        addRow(pane, STATUS_SLOT, Row(summary, emptyList()) {}, token)
        gui.show(player)
    }

    private fun addRow(pane: StaticPane, slot: Int, row: Row, token: Long) {
        val item = ItemStack.of(if (slot < PAGE_SIZE) Material.OAK_SIGN else Material.PAPER)
        item.editMeta { meta ->
            meta.displayName(if (slot == STATUS_SLOT) lang.gui("guild_stall.title") else row.name)
            meta.lore(if (slot == STATUS_SLOT) summaryLines(row.name) else row.lore)
        }
        pane.addItem(
            GuiItem(item) {
                if (navigator.isNavigationCurrent(token) && isMember()) row.action()
            },
            slot % WIDTH,
            slot / WIDTH,
        )
    }

    private fun summaryLines(text: Component): List<Component> =
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText()
            .serialize(text)
            .lines()
            .map(Component::text)

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
        const val MENU_ROWS = 6
        const val PAGE_SIZE = 45
        const val STATUS_SLOT = 53
    }
}
