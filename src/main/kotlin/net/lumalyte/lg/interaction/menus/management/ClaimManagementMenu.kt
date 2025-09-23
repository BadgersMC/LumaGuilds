package net.lumalyte.lg.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.actions.claim.ConvertClaimToGuild
import net.lumalyte.lg.application.results.claim.ConvertClaimToGuildResult
import net.lumalyte.lg.application.actions.claim.flag.GetClaimFlags
import net.lumalyte.lg.application.actions.claim.permission.GetPlayersWithPermissionInClaim
import net.lumalyte.lg.application.persistence.ClaimRepository
import net.lumalyte.lg.application.actions.player.RegisterClaimMenuOpening
import net.lumalyte.lg.application.actions.player.tool.GivePlayerClaimTool
import net.lumalyte.lg.application.actions.player.tool.GivePlayerMoveTool
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.lumalyte.lg.utils.enchantment
import net.lumalyte.lg.utils.flag
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ClaimManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                          private var claim: Claim): Menu, KoinComponent {
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()
    private val getClaimFlags: GetClaimFlags by inject()
    private val getPlayersWithPermissionInClaim: GetPlayersWithPermissionInClaim by inject()
    private val convertClaimToGuild: ConvertClaimToGuild by inject()
    private val claimRepository: ClaimRepository by inject()
    private val registerClaimMenuOpening: RegisterClaimMenuOpening by inject()
    private val givePlayerClaimTool: GivePlayerClaimTool by inject()
    private val givePlayerMoveTool: GivePlayerMoveTool by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val playerId = player.uniqueId
        val gui = ChestGui(1, localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_TITLE, claim.name))
        val pane = StaticPane(0, 0, 9, 1)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }
        gui.addPane(pane)

        // Add a give claim tool button
        val claimToolItem = ItemStack(Material.STICK)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_TOOL_NAME))
            .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_TOOL_LORE))
            .enchantment(Enchantment.LUCK_OF_THE_SEA)
            .flag(ItemFlag.HIDE_ENCHANTS)
        val guiClaimToolItem = GuiItem(claimToolItem) { guiEvent ->
            guiEvent.isCancelled = true
            givePlayerClaimTool.execute(player.uniqueId)
        }
        pane.addItem(guiClaimToolItem, 0, 0)

        // Add update icon menu button
        val iconEditorItem = ItemStack(Material.valueOf(claim.icon))
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_ICON_NAME))
            .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_ICON_LORE))
        val guiIconEditorItem = GuiItem(iconEditorItem) {
            menuNavigator.openMenu(menuFactory.createClaimIconMenu(player, menuNavigator, claim)) }
        pane.addItem(guiIconEditorItem, 2, 0)

        // Add a claim renaming button
        val renamingItem = ItemStack(Material.NAME_TAG)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_RENAME_NAME))
            .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_RENAME_LORE))
        val guiRenamingItem = GuiItem(renamingItem) { menuNavigator.openMenu(
            ClaimRenamingMenu(menuNavigator, player, claim)) }
        pane.addItem(guiRenamingItem, 3, 0)

        // Add a player trusts button
        val playerTrustItem = ItemStack(Material.PLAYER_HEAD)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_PERMISSIONS_NAME))
            .lore("${getPlayersWithPermissionInClaim.execute(claim.id).count()}")
        val guiPlayerTrustItem = GuiItem(playerTrustItem) {
            menuNavigator.openMenu(menuFactory.createClaimTrustMenu(menuNavigator, player, claim)) }
        pane.addItem(guiPlayerTrustItem, 5, 0)

        // Add a convert to guild claim button (only for personal claims)
        if (claim.teamId == null) {
            val convertGuildItem = ItemStack(Material.WHITE_BANNER)
                .name(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_CONVERT_GUILD_NAME))
                .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_CONVERT_GUILD_LORE))
            val guiConvertGuildItem = GuiItem(convertGuildItem) { guiEvent ->
                guiEvent.isCancelled = true

                val result = convertClaimToGuild.execute(claim.id, playerId)
                when (result) {
                    is ConvertClaimToGuildResult.Success -> {
                        player.sendMessage(Component.text("Claim successfully converted to guild claim!")
                            .color(TextColor.color(85, 255, 85)))
                        player.sendMessage(Component.text("Guild members now have access based on their roles.")
                            .color(TextColor.color(255, 255, 85)))
                    }
                    is ConvertClaimToGuildResult.ClaimNotFound -> {
                        player.sendMessage(Component.text("Claim not found.")
                            .color(TextColor.color(255, 85, 85)))
                    }
                    is ConvertClaimToGuildResult.NotClaimOwner -> {
                        player.sendMessage(Component.text("You are not the owner of this claim.")
                            .color(TextColor.color(255, 85, 85)))
                    }
                    is ConvertClaimToGuildResult.AlreadyGuildOwned -> {
                        player.sendMessage(Component.text("This claim is already a guild claim.")
                            .color(TextColor.color(255, 85, 85)))
                    }
                    is ConvertClaimToGuildResult.PlayerNotInGuild -> {
                        player.sendMessage(Component.text("You must be in a guild to convert a claim.")
                            .color(TextColor.color(255, 85, 85)))
                    }
                    else -> {
                        player.sendMessage(Component.text("An error occurred while converting the claim.")
                            .color(TextColor.color(255, 85, 85)))
                    }
                }

                // Refresh the menu to reflect the change
                if (result is ConvertClaimToGuildResult.Success) {
                    // Reload the claim from repository to get updated guild ownership
                    val updatedClaim = claimRepository.getById(claim.id)
                    if (updatedClaim != null) {
                        claim = updatedClaim
                    }
                }
                open()
            }
            pane.addItem(guiConvertGuildItem, 4, 0)
        }

        // Add a claim flags button
        val claimFlagsItem = ItemStack(Material.ACACIA_HANGING_SIGN)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_FLAGS_NAME))
            .lore("${getClaimFlags.execute(claim.id).count()}")
        val guiClaimFlagsItem = GuiItem(claimFlagsItem) {
            menuNavigator.openMenu(menuFactory.createClaimFlagMenu(menuNavigator, player, claim)) }
        pane.addItem(guiClaimFlagsItem, 7, 0)

        // Add a claim move button
        val deleteItem = ItemStack(Material.PISTON)
            .name(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_MOVE_NAME))
            .lore(localizationProvider.get(playerId, LocalizationKeys.MENU_MANAGEMENT_ITEM_MOVE_LORE))
        val guiDeleteItem = GuiItem(deleteItem) { guiEvent ->
            guiEvent.isCancelled = true
            givePlayerMoveTool.execute(player.uniqueId, claim.id)
        }
        pane.addItem(guiDeleteItem, 8, 0)

        // Register the player being in the menu and open it
        registerClaimMenuOpening.execute(player.uniqueId, claim.id)
        gui.show(player)
    }

    override fun passData(data: Any?) {
        claim = data as? Claim ?: return
    }
}

