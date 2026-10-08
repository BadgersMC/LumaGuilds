package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.deserializeToItemStack
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// Locale keys stay literal so LocaleContractTest can see them.
@Suppress("StringLiteralDuplication", "LibraryEntitiesShouldNotBePublic")
class GuildControlPanelMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    private val rankService: RankService,
    private val memberService: MemberService,
    private val vaultService: GuildVaultService,
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory,
    private val configService: ConfigService,
    private val progressionService: ProgressionService,
    private val progressionRepository: ProgressionRepository
): Menu, KoinComponent {

    private val lang: LangService by inject()
    private val nexoEmojiService: NexoEmojiService by inject()

    override fun open() {
        val playerId = player.uniqueId

        // Security check: Only guild members can access guild actions
        if (memberService.getMember(playerId, guild.id) == null) {
            player.sendMessage(lang.msg("menu.control_panel.feedback.not_member"))
            menuNavigator.goBack()
            return
        }

        // Guild Actions: the things that are not already a Dashboard section.
        // Everything else (settings, members, ranks, bank, wars, ...) lives on the Dashboard,
        // so it is not repeated here.
        val heading = lang.guiTitle("menu.control_panel.title", "guild" to guild.name)
        val gui = ChestGui(ROWS, MenuTitleBuilder.build(guild.guiTheme, ROWS, heading))
        val pane = StaticPane(0, 0, COLUMNS, ROWS)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.addPane(pane)

        // Row 0: guild features
        addPartyManagementButton(pane, PARTY_SLOT, 0)
        addVaultButton(pane, VAULT_SLOT, 0)
        pane.addItem(GuiItem(ItemStack.of(Material.BOOK).name(lang.gui("community.chat.settings"))) {
            menuNavigator.openMenu(menuFactory.createPlayerChatSettingsMenu(menuNavigator, player))
        }, 4, 1)

        // Row 2: danger actions in the corners, Back in the standard centre slot
        addLeaveGuildButton(pane, 0, 2)
        addBackButton(pane, BACK_SLOT, 2)
        addDisbandGuildButton(pane, DISBAND_SLOT, 2)

        gui.show(player)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem =
            NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
                .name(lang.gui("menu.common.item.back.name"))
        pane.addItem(GuiItem(backItem) { menuNavigator.goBack() }, x, y)
    }

    private fun addPartyManagementButton(pane: StaticPane, x: Int, y: Int) {
        val partyItem =
            NexoItemProvider.getItemStackOrFallback("lg_party") { ItemStack.of(Material.FIREWORK_ROCKET) }
                .name(lang.gui("menu.control_panel.item.party.name"))
                .lore(lang.gui("menu.control_panel.item.party.lore.description"))
                .lore(lang.gui("menu.control_panel.item.party.lore.details"))
        val guiItem = GuiItem(partyItem) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addVaultButton(pane: StaticPane, x: Int, y: Int) {
        val vaultItem = when (guild.vaultStatus) {
            net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE -> {
                NexoItemProvider.getItemStackOrFallback("lg_vault") { ItemStack.of(Material.CHEST) }
                    .name(lang.gui("menu.control_panel.item.vault.name"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.available"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.storage_line_1"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.storage_line_2"))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.open"))
            }
            net.lumalyte.lg.domain.entities.VaultStatus.UNAVAILABLE -> {
                NexoItemProvider.getItemStackOrFallback("lg_vault_unavailable") { ItemStack.of(Material.BARRIER) }
                    .name(lang.gui("menu.control_panel.item.vault.name"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.not_placed"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.place_line_1"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.place_line_2"))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.obtain"))
            }
            net.lumalyte.lg.domain.entities.VaultStatus.NEVER_PLACED -> {
                NexoItemProvider.getItemStackOrFallback("lg_vault_unavailable") { ItemStack.of(Material.BARRIER) }
                    .name(lang.gui("menu.control_panel.item.vault.name"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.never_placed"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.place_line_1"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.place_line_2"))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.control_panel.item.vault.lore.obtain"))
            }
        }

        val guiItem = GuiItem(vaultItem) {
            // Re-fetch guild to get current vault status — the cached guild object may be stale
            // if another player broke the vault after this menu was opened
            val currentGuild = guildService.getGuild(guild.id)
            if (currentGuild == null) {
                player.sendMessage(lang.msg("menu.control_panel.feedback.guild_missing"))
                return@GuiItem
            }
            if (currentGuild.vaultStatus != net.lumalyte.lg.domain.entities.VaultStatus.AVAILABLE) {
                player.sendMessage(lang.msg("menu.control_panel.feedback.vault_unavailable"))
                return@GuiItem
            }
            // Only close the menu once we've confirmed the vault is currently available
            player.closeInventory()
            val result = vaultService.openVaultInventory(player, currentGuild)
            when (result) {
                is VaultResult.Success -> {
                    // Vault opened successfully
                }
                is VaultResult.Failure -> {
                    player.sendMessage(lang.msg("menu.control_panel.feedback.vault_failure", "error" to result.message))
                }
            }
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addDisbandGuildButton(pane: StaticPane, x: Int, y: Int) {
        val disbandItem =
            NexoItemProvider.getItemStackOrFallback("lg_disband") { ItemStack.of(Material.TNT) }
                .name(lang.gui("menu.control_panel.item.disband.name"))
                .lore(lang.gui("menu.control_panel.item.disband.lore.warning"))
                .lore(lang.gui("menu.control_panel.item.disband.lore.description"))
                .lore(lang.gui("menu.control_panel.item.disband.lore.members"))
        val guiItem = GuiItem(disbandItem) {
            menuNavigator.openMenu(menuFactory.createGuildDisbandConfirmationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun addLeaveGuildButton(pane: StaticPane, x: Int, y: Int) {
        val leaveItem =
            NexoItemProvider.getItemStackOrFallback("lg_leave") { ItemStack.of(Material.DARK_OAK_DOOR) }
                .name(lang.gui("menu.control_panel.item.leave.name"))
                .lore(lang.gui("menu.control_panel.item.leave.lore.description"))
                .lore(lang.gui("menu.control_panel.item.leave.lore.rejoin"))
        val guiItem = GuiItem(leaveItem) {
            menuNavigator.openMenu(menuFactory.createGuildLeaveConfirmationMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }

    private companion object {
        const val ROWS = 3
        const val COLUMNS = 9
        const val PARTY_SLOT = 3
        const val VAULT_SLOT = 5
        const val BACK_SLOT = 4
        const val DISBAND_SLOT = 8
    }
}
