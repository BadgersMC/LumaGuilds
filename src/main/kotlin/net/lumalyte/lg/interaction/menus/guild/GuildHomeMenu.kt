package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane

import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.HomeActivationCostResult
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

// Locale keys stay literal so LocaleContractTest can see them.
@Suppress("StringLiteralDuplication", "LibraryEntitiesShouldNotBePublic")
class GuildHomeMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val homeActivationService: net.lumalyte.lg.application.services.GuildHomeActivationService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val progressionService: net.lumalyte.lg.application.services.ProgressionService by inject()
    private val teleportationService: net.lumalyte.lg.infrastructure.services.TeleportationService by inject()
    private val rankService: net.lumalyte.lg.application.services.RankService by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.guild_home.title", "guild" to guild.name)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Home slots and status
        addHomeSlotsDisplay(pane, 0, 0)

        // Set home buttons
        addSetHomeButtons(pane, 0, 2)

        // Teleport buttons
        addTeleportButtons(pane, 0, 4)

        // Per-home access config buttons (row 3) — managers only
        addHomeAccessButtons(pane)

        // Ally home teleport buttons (if perk unlocked)
        if (progressionService.hasPerkUnlocked(guild.id, net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS)) {
            addAllyHomeButtons(pane, 0, 5)
            addAllyHomeAccessButton(pane)
        }

        // Back button: bottom centre, same as every guild menu (ally homes skip this slot)
        addBackButton(pane, BACK_SLOT, BOTTOM_ROW)

        gui.show(player)
    }

    private fun addHomeSlotsDisplay(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        val slotsItem =
            NexoItemProvider.getItemStackOrFallback("lg_home") { ItemStack.of(Material.BOOK) }
                .name(lang.gui("menu.guild_home.slots.name"))
                .lore(lang.gui("menu.guild_home.slots.count", "count" to allHomes.size, "total" to availableSlots))
                .lore(lang.gui("menu.common.blank"))

        if (allHomes.hasHomes()) {
            allHomes.homes.forEach { entry ->
                val name = entry.key
                val home = entry.value
                val marker = if (name == "main") lang.gui("menu.guild_home.slots.main") else net.kyori.adventure.text.Component.empty()
                val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
                slotsItem.lore(lang.gui("menu.guild_home.slots.row", "home" to name, "marker" to marker, "world" to worldName))
            }
        } else {
            slotsItem.lore(lang.gui("menu.guild_home.slots.none"))
        }

        slotsItem.lore(lang.gui("menu.common.blank"))
        val lockedHomes = allHomes.homeNames.count { guildService.isHomeLocked(guild.id, it) }
        if (lockedHomes > 0) slotsItem.lore(net.kyori.adventure.text.Component.text("$lockedHomes preserved homes locked until Outpost repurchase"))
        if (guildService.canCreateNewHome(guild.id)) {
            slotsItem.lore(lang.gui("menu.guild_home.slots.additional"))
        } else {
            slotsItem.lore(lang.gui("menu.guild_home.slots.maximum"))
        }

        val guiItem = GuiItem(slotsItem)
        pane.addItem(guiItem, x, y)
    }

    private fun addSetHomeButtons(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val availableSlots = guildService.getAvailableHomeSlots(guild.id)

        // Set Main Home button
        if (!allHomes.homes.containsKey("main")) {
            val setMainItem =
                NexoItemProvider.getItemStackOrFallback("lg_home_set") { ItemStack.of(Material.GREEN_WOOL) }
                    .name(lang.gui("menu.guild_home.set.main.name"))
                    .lore(lang.gui("menu.guild_home.set.main.description"))
                    .lore(lang.gui("menu.guild_home.set.main.command"))

            val mainGuiItem = GuiItem(setMainItem) {
                setGuildHome("main")
            }
            pane.addItem(mainGuiItem, x, y)
        }

        // Set Additional Home button (if slots available)
        if (guildService.canCreateNewHome(guild.id)) {
            val freeSlots = (availableSlots - allHomes.size).coerceAtLeast(1)
            val setAdditionalItem =
                NexoItemProvider.getItemStackOrFallback("lg_home_add") { ItemStack.of(Material.LIME_WOOL) }
                    .name(lang.gui("menu.guild_home.set.additional.name"))
                    .lore(lang.gui("menu.guild_home.set.additional.description"))
                    .lore(lang.gui("menu.guild_home.set.additional.command"))
                    .lore(lang.gui("menu.guild_home.set.additional.available", "count" to freeSlots))

            val additionalGuiItem = GuiItem(setAdditionalItem) {
                // This would open a menu to input home name, but for now let's use a simple approach
                player.sendMessage(lang.msg("menu.guild_home.feedback.sethome_command"))
                player.sendMessage(lang.msg("menu.guild_home.feedback.sethome_example"))
            }
            pane.addItem(additionalGuiItem, x + 2, y)
        }

        // Remove Homes button
        if (allHomes.hasHomes()) {
            val removeItem =
                NexoItemProvider.getItemStackOrFallback("lg_home_remove") { ItemStack.of(Material.RED_WOOL) }
                    .name(lang.gui("menu.guild_home.remove.name"))
                    .lore(lang.gui("menu.guild_home.remove.description"))

            val removeGuiItem = GuiItem(removeItem) {
                showRemoveHomesMenu()
            }
            pane.addItem(removeGuiItem, x + 4, y)
        }
    }

    private fun addTeleportButtons(pane: StaticPane, x: Int, y: Int) {
        val allHomes = guildService.getHomes(guild.id)
        val hasActiveTeleport = teleportationService.hasActiveTeleport(player.uniqueId)

        if (hasActiveTeleport) {
            // Show cancel teleport button
            val remaining = teleportationService.getRemainingSeconds(player.uniqueId) ?: 0
            val cancelItem =
                NexoItemProvider.getItemStackOrFallback("lg_cancel") { ItemStack.of(Material.CLOCK) }
                    .name(lang.gui("menu.guild_home.teleport.cancel.name"))
                    .lore(lang.gui("menu.guild_home.teleport.cancel.description"))
                    .lore(lang.gui("menu.guild_home.teleport.cancel.remaining", "seconds" to remaining))

            val cancelGuiItem = GuiItem(cancelItem) {
                teleportationService.cancelTeleport(player.uniqueId)
                player.sendMessage(lang.msg("menu.guild_home.feedback.teleport_cancelled"))
                open() // Refresh menu
            }
            pane.addItem(cancelGuiItem, x, y)
        } else if (allHomes.hasHomes()) {
            // Show teleport/activation for the default home. Saved Season 1 locations remain locked until paid.
            val mainEntry = allHomes.homes.entries.firstOrNull { it.key == "main" } ?: allHomes.homes.entries.firstOrNull()
            if (mainEntry != null) {
                val mainHomeName = mainEntry.key
                val mainHome = mainEntry.value
                val locked = guildService.isHomeLocked(guild.id, mainHomeName)
                val active = homeActivationService.isActive(guild.id, mainHomeName)
                val mainIcon = if (locked) ItemStack.of(Material.BARRIER)
                    else if (active) teleportIcon() else ItemStack.of(Material.GOLD_INGOT)
                val mainName =
                    if (locked) {
                        net.kyori.adventure.text.Component.text("Locked Outpost: $mainHomeName")
                    } else if (active) {
                        lang.gui("menu.guild_home.teleport.main.name")
                    } else {
                        lang.gui("menu.guild_home.activation.name", "home" to mainHomeName)
                    }
                val mainDescription =
                    if (active) {
                        lang.gui("menu.guild_home.teleport.main.description")
                    } else {
                        lang.gui("menu.guild_home.activation.description")
                    }
                val mainWorld = Bukkit.getWorld(mainHome.worldId)?.name ?: lang.raw("general.unknown")
                val teleportItem =
                    mainIcon
                        .name(mainName)
                        .lore(mainDescription)
                        .lore(lang.gui("menu.guild_home.world", "world" to mainWorld))
                if (active) teleportItem.lore(lang.gui("menu.guild_home.teleport.main.countdown"))

                val teleportGuiItem = GuiItem(teleportItem) {
                    if (guildService.isHomeLocked(guild.id, mainHomeName)) restoreLockedOutpost(mainHomeName)
                    else if (!homeActivationService.isActive(guild.id, mainHomeName)) activateSavedHome(mainHomeName)
                    else if (guildService.canUseHome(player.uniqueId, guild.id, mainHomeName))
                        startTeleportCountdown(mainHome, mainHomeName)
                    else player.sendMessage(lang.msg("menu.guild_home.feedback.access_denied", "home" to mainHomeName))
                }
                pane.addItem(teleportGuiItem, x, y)
            }

            // Show list homes button if there are multiple homes
            if (allHomes.size > 1) {
                val listItem =
                    NexoItemProvider.getItemStackOrFallback("lg_home_list") { ItemStack.of(Material.COMPASS) }
                        .name(lang.gui("menu.guild_home.list.name"))
                        .lore(lang.gui("menu.guild_home.list.description"))
                        .lore(lang.gui("menu.guild_home.list.command"))

                val listGuiItem = GuiItem(listItem) {
                    showHomesList()
                }
                pane.addItem(listGuiItem, x + 2, y)
            }
        } else {
            // No homes set
            val noHomeItem =
                NexoItemProvider.getItemStackOrFallback("lg_home_none") { ItemStack.of(Material.GRAY_DYE) }
                    .name(lang.gui("menu.guild_home.teleport.none.name"))
                    .lore(lang.gui("menu.guild_home.teleport.none.description"))

            pane.addItem(GuiItem(noHomeItem), x, y)
        }
    }

    private fun addHomeAccessButtons(pane: StaticPane) {
        if (!rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_HOME)) return
        val homes = guildService.getHomes(guild.id).homes.entries.toList()
        homes.take(9).forEachIndexed { idx, (homeName, _) ->
            val locked = guildService.isHomeLocked(guild.id, homeName)
            val active = homeActivationService.isActive(guild.id, homeName)
            val item = if (locked) {
                ItemStack.of(Material.BARRIER)
                    .name(net.kyori.adventure.text.Component.text("Locked Outpost: $homeName"))
                    .lore(net.kyori.adventure.text.Component.text("Purchase an Outpost after prestige to restore"))
            } else if (active) {
                NexoItemProvider.getItemStackOrFallback("lg_home_access") { ItemStack.of(Material.IRON_DOOR) }
                    .name(lang.gui("menu.guild_home.access.name", "home" to homeName))
                    .lore(lang.gui("menu.guild_home.access.description"))
                    .lore(lang.gui("menu.guild_home.access.click"))
            } else {
                ItemStack.of(Material.GOLD_INGOT)
                    .name(lang.gui("menu.guild_home.activation.name", "home" to homeName))
                    .lore(lang.gui("menu.guild_home.activation.description"))
                    .lore(lang.gui("menu.guild_home.activation.click"))
            }
            pane.addItem(GuiItem(item) {
                if (guildService.isHomeLocked(guild.id, homeName)) restoreLockedOutpost(homeName)
                else if (homeActivationService.isActive(guild.id, homeName))
                    menuNavigator.openMenu(menuFactory.createHomeAccessMenu(menuNavigator, player, guild, homeName))
                else activateSavedHome(homeName)
            }, idx, 3)
        }
    }

    private fun addAllyHomeAccessButton(pane: StaticPane) {
        if (!rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_HOME)) return
        val allyAccessItem =
            NexoItemProvider.getItemStackOrFallback("lg_ally_access") { ItemStack.of(Material.IRON_DOOR) }
                .name(lang.gui("menu.guild_home.ally_access.name"))
                .lore(lang.gui("menu.guild_home.ally_access.description"))
                .lore(lang.gui("menu.guild_home.access.click"))
        val allyAccessGuiItem = GuiItem(allyAccessItem) {
            menuNavigator.openMenu(menuFactory.createAllyHomeAccessMenu(menuNavigator, player, guild))
        }
        pane.addItem(allyAccessGuiItem, ALLY_ACCESS_SLOT, BOTTOM_ROW)
    }

    private fun addAllyHomeButtons(pane: StaticPane, x: Int, y: Int) {
        val allyHomes = guildService.getAllyHomes(guild.id)
        if (allyHomes.isEmpty()) {
            val noAllyItem =
                NexoItemProvider.getItemStackOrFallback("lg_ally_home_locked") { ItemStack.of(Material.GRAY_DYE) }
                    .name(lang.gui("menu.guild_home.ally.none.name"))
                    .lore(lang.gui("menu.guild_home.ally.none.perk"))
                    .lore(lang.gui("menu.guild_home.ally.none.home"))

            pane.addItem(GuiItem(noAllyItem), x, y)
            return
        }

        var slot = x
        for ((guildName, home) in allyHomes) {
            if (slot == 4) slot++ // slot 4 is the standard Back position
            if (slot >= 7) break // Max 6 ally homes on row (slots 0-3 and 5-6)
            val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
            val targetGuild = guildService.getGuildByName(guildName)
            val allowed = targetGuild != null &&
                guildService.canUseAllyHome(player.uniqueId, guild.id, targetGuild.id)
            val allyIcon =
                NexoItemProvider.getItemStackOrFallback(if (allowed) "lg_ally_home" else "lg_ally_home_locked") {
                    ItemStack.of(if (allowed) Material.ENDER_EYE else Material.BARRIER)
                }
            val allyName =
                if (allowed) {
                    lang.gui("menu.guild_home.ally.name", "guild" to guildName)
                } else {
                    lang.gui("menu.guild_home.ally.locked", "guild" to guildName)
                }
            val allyAction =
                if (allowed) lang.gui("menu.guild_home.ally.teleport") else lang.gui("menu.guild_home.ally.denied")
            val allyItem =
                allyIcon
                    .name(allyName)
                    .lore(lang.gui("menu.guild_home.ally.description"))
                    .lore(lang.gui("menu.guild_home.world", "world" to worldName))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(allyAction)

            val guiItem = GuiItem(allyItem) {
                teleportToAllyHome(targetGuild?.id)
            }
            pane.addItem(guiItem, slot, y)
            slot++
        }
    }

    private fun teleportToAllyHome(targetGuildId: UUID?) {
        if (targetGuildId == null) return denyAllyHome()
        val currentTarget = guildService.getGuild(targetGuildId) ?: return denyAllyHome()
        val currentHome = currentTarget.allyHome ?: return denyAllyHome()
        if (!canTeleportToAllyHome(currentTarget, currentHome)) return denyAllyHome()
        startTeleportCountdown(currentHome)
    }

    private fun canTeleportToAllyHome(target: Guild, home: GuildHome): Boolean {
        val eligibleHome = guildService.getAllyHomes(guild.id)[target.name]
        return eligibleHome == home && guildService.canUseAllyHome(player.uniqueId, guild.id, target.id)
    }

    private fun denyAllyHome() {
        player.sendMessage(lang.msg("menu.guild_home.feedback.ally_denied"))
        player.sendMessage(lang.msg("menu.guild_home.feedback.ally_reason"))
    }

    private fun showRemoveHomesMenu() {
        val allHomes = guildService.getHomes(guild.id)
        if (!allHomes.hasHomes()) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.no_homes_remove"))
            return
        }

        val gui = ChestGui(4, MenuTitleBuilder.build(guild.guiTheme, 4, lang.guiTitle("menu.guild_home.remove.title")))
        val pane = StaticPane(0, 0, 9, 4)

        // Prevent moving items in the top or bottom inventory (same as main menu)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        
        // Deleting an over-cap saved home cannot be undone until enough capacity is earned.
        val overEarnedCapacity = allHomes.size > guildService.getAvailableHomeSlots(guild.id)

        // List homes for removal
        var slot = 0
        allHomes.homes.forEach { entry ->
            val name = entry.key
            val home = entry.value
            if (slot < 27) { // Max 27 slots
                val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
                val removeItem =
                    NexoItemProvider.getItemStackOrFallback("lg_home_remove") { ItemStack.of(Material.RED_WOOL) }
                        .name(lang.gui("menu.guild_home.remove.home", "home" to name))
                        .lore(lang.gui("menu.guild_home.world", "world" to worldName))
                        .lore(lang.gui("menu.common.blank"))
                        .lore(lang.gui("menu.guild_home.remove.click"))
                if (overEarnedCapacity) removeItem.lore(lang.gui("menu.guild_home.remove.over_cap_warning"))

                val removeGuiItem = GuiItem(removeItem) {
                    val success = guildService.removeHome(guild.id, name, player.uniqueId)
                    if (success) {
                        player.sendMessage(lang.msg("menu.guild_home.feedback.removed", "home" to name))
                        showRemoveHomesMenu() // Refresh menu
                    } else {
                        player.sendMessage(lang.msg("menu.guild_home.feedback.remove_failed", "home" to name))
                    }
                }
                pane.addItem(removeGuiItem, slot % 9, slot / 9)
                slot++
            }
        }

        // Back button
        val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.guild_home.remove.back.name"))
            .lore(lang.gui("menu.guild_home.remove.back.description"))

        val backGuiItem = GuiItem(backItem) {
            open() // Return to main home menu
        }
        pane.addItem(backGuiItem, 4, 3)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun showHomesList() {
        val allHomes = guildService.getHomes(guild.id)
        if (!allHomes.hasHomes()) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.no_homes"))
            return
        }

        player.sendMessage(lang.msg("menu.guild_home.list.header"))
        allHomes.homes.forEach { entry ->
            val name = entry.key
            val home = entry.value
            val marker = if (name == "main") lang.raw("menu.guild_home.slots.main") else ""
            val worldName = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")
            player.sendMessage(lang.msg("menu.guild_home.slots.row", "home" to name, "marker" to marker, "world" to worldName))
            if (guildService.isHomeLocked(guild.id, name)) {
                player.sendMessage("§c  [LOCKED — /guild home unlock $name after an Outpost purchase]")
            }
        }
        player.sendMessage(lang.msg("menu.guild_home.list.command"))
        player.sendMessage(lang.msg("menu.guild_home.list.footer"))
    }

    private fun setGuildHome(homeName: String = "main") {
        val location = player.location
        val home = GuildHome(
            worldId = location.world.uid,
            position = net.lumalyte.lg.domain.values.Position3D(
                location.x.toInt(), location.y.toInt(), location.z.toInt()
            )
        )

        // Check if location is safe (if safety check is enabled)
        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(location)) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.unsafe_set"))
            player.sendMessage(lang.msg("menu.guild_home.feedback.safety_hint"))
            open() // Reopen menu to show current state
            return
        }

        val existing = guildService.getHome(guild.id, homeName) != null
        val result = homeActivationService.persistLocation(
            UUID.randomUUID(), guild.id, homeName, player.uniqueId, existedBefore = existing,
            proposedHome = home,
        ) { guildService.setHome(guild.id, homeName, home, player.uniqueId) }

        when (result) {
            is HomeActivationCostResult.Applied -> {
                val homeLabel = if (homeName == "main") {
                    lang.raw("menu.guild_home.feedback.main_home")
                } else {
                    lang.msg("menu.guild_home.feedback.named_home", "home" to homeName)
                }
                player.sendMessage(lang.msg("menu.guild_home.feedback.set", "home" to homeLabel))
                if (result.cost > 0) {
                    player.sendMessage(lang.msg("menu.guild_home.feedback.activation_paid", "cost" to result.cost))
                }
                player.sendMessage(lang.msg("menu.guild_home.feedback.teleport_command", "home" to if (homeName == "main") "" else homeName))
                guild = guildService.getGuild(guild.id) ?: guild
            }
            is HomeActivationCostResult.Rejected -> {
                if (result.reason == net.lumalyte.lg.domain.gold.GuildGoldRejection.INSUFFICIENT_FUNDS) {
                    player.sendMessage(lang.msg("menu.guild_home.feedback.activation_insufficient"))
                    player.sendMessage(lang.msg("menu.guild_home.feedback.funding_tip"))
                } else {
                    player.sendMessage(lang.msg("menu.guild_home.feedback.activation_rejected", "reason" to result.reason.name))
                }
            }
            HomeActivationCostResult.ConfigurationError ->
                player.sendMessage(lang.msg("menu.guild_home.feedback.activation_config_error"))
            is HomeActivationCostResult.PaymentFailed ->
                player.sendMessage(lang.msg("menu.guild_home.feedback.activation_review", "transaction" to result.transactionId))
            is HomeActivationCostResult.ActivationFailed -> {
                player.sendMessage(lang.msg("menu.guild_home.feedback.set_failed"))
                if (!result.compensated) {
                    player.sendMessage(lang.msg("menu.guild_home.feedback.activation_review_no_transaction"))
                }
            }
        }
        open()
    }


    private fun restoreLockedOutpost(homeName: String) {
        val success = guildService.restoreLockedOutpost(guild.id, homeName, player.uniqueId)
        if (success) player.sendMessage("§aGuild home '$homeName' restored using a paid Outpost credit.")
        else player.sendMessage("§c'$homeName' is locked. Purchase an Outpost at this prestige first.")
        open()
    }

    private fun activateSavedHome(homeName: String) {
        if (guildService.isHomeLocked(guild.id, homeName)) {
            restoreLockedOutpost(homeName)
            return
        }
        if (!rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_HOME)) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.activation_no_permission"))
            return
        }
        when (val result = homeActivationService.activateSavedHome(UUID.randomUUID(), guild.id, homeName, player.uniqueId)) {
            is HomeActivationCostResult.Applied -> {
                player.sendMessage(lang.msg("menu.guild_home.feedback.activation_success", "home" to homeName))
                if (result.cost > 0) player.sendMessage(lang.msg("menu.guild_home.feedback.activation_paid", "cost" to result.cost))
            }
            is HomeActivationCostResult.Rejected -> {
                if (result.reason == net.lumalyte.lg.domain.gold.GuildGoldRejection.INSUFFICIENT_FUNDS) {
                    player.sendMessage(lang.msg("menu.guild_home.feedback.activation_insufficient"))
                    player.sendMessage(lang.msg("menu.guild_home.feedback.funding_tip"))
                } else {
                    player.sendMessage(lang.msg("menu.guild_home.feedback.activation_rejected", "reason" to result.reason.name))
                }
            }
            HomeActivationCostResult.ConfigurationError -> player.sendMessage(lang.msg("menu.guild_home.feedback.activation_config_error"))
            is HomeActivationCostResult.PaymentFailed -> player.sendMessage(lang.msg("menu.guild_home.feedback.activation_review", "transaction" to result.transactionId))
            is HomeActivationCostResult.ActivationFailed -> player.sendMessage(lang.msg("menu.guild_home.feedback.activation_failed", "home" to homeName))
        }
        open()
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.guild_home.back.name"))
            .lore(lang.gui("menu.guild_home.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun startTeleportCountdown(home: GuildHome, homeName: String? = null) {
        val world = Bukkit.getWorld(home.worldId)
        if (world == null) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.world_missing"))
            return
        }

        val targetLocation = org.bukkit.Location(
            world,
            home.position.x.toDouble() + 0.5,
            home.position.y.toDouble(),
            home.position.z.toDouble() + 0.5,
            player.location.yaw,
            player.location.pitch
        )

        if (configService.loadConfig().guild.homeTeleportSafetyCheck && !isLocationSafe(targetLocation)) {
            player.sendMessage(lang.msg("menu.guild_home.feedback.unsafe_teleport"))
            player.sendMessage(lang.msg("menu.guild_home.feedback.safety_hint"))
            return
        }

        if (homeName != null) {
            teleportationService.startTeleportGuarded(
                player, targetLocation,
                mayProceed = {
                    guildService.canUseHome(player.uniqueId, guild.id, homeName) &&
                        homeActivationService.isActive(guild.id, homeName) &&
                        guildService.getHome(guild.id, homeName) == home
                },
            )
        } else {
            teleportationService.startTeleport(player, targetLocation)
        }
    }

    private fun isLocationSafe(location: org.bukkit.Location): Boolean =
        net.lumalyte.lg.utils.GuildHomeSafety.isSafeForUnconfirmedUse(location)

    private fun teleportIcon(): ItemStack {
        return NexoItemProvider.getItemStackOrFallback("lg_home_teleport") { ItemStack.of(Material.ENDER_PEARL) }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }

    private companion object {
        const val ALLY_ACCESS_SLOT = 7
        const val BACK_SLOT = 4
        const val BOTTOM_ROW = 5
    }
}
