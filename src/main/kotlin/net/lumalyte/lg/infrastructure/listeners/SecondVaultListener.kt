package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.common.PluginKeys
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.services.SecondVaultHolder
import net.lumalyte.lg.infrastructure.services.SecondVaultService
import net.lumalyte.lg.interaction.listeners.VaultItemPermissions
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * REQ-242 to REQ-246: placing, opening, protecting and using the Prestige VI second vault.
 * Hoppers, pistons, signs and adjacent chests are handled by [VaultProtectionListener], which
 * treats the second vault like the main one.
 */
class SecondVaultListener : Listener, KoinComponent {
    private val secondVault: SecondVaultService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val lang: LangService by inject()
    private val breakWarnings = ConcurrentHashMap<Pair<UUID, UUID>, Long>()

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (event.block.type != Material.CHEST) return
        val id = event.itemInHand.itemMeta?.persistentDataContainer
            ?.get(PluginKeys.SECOND_VAULT_ID, PersistentDataType.STRING) ?: return
        val player = event.player
        val guild = runCatching { UUID.fromString(id) }.getOrNull()?.let(guildService::getGuild)
        if (guild == null || memberService.getMember(player.uniqueId, guild.id) == null) {
            event.isCancelled = true
            player.sendMessage(lang.msg("second_vault.place.not_yours"))
            return
        }
        when (secondVault.place(guild, event.block.location, player)) {
            SecondVaultService.Placement.PLACED -> {
                player.sendMessage(lang.msg("second_vault.place.placed"))
                player.sendMessage(lang.msg(
                    "second_vault.place.where",
                    "x" to event.block.x, "y" to event.block.y, "z" to event.block.z,
                ))
                player.sendMessage(lang.msg("second_vault.place.capacity", "slots" to slots(guild.level)))
                player.playSound(player.location, Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.2f)
            }
            SecondVaultService.Placement.LOCKED -> deny(event, player, lang.msg("second_vault.place.locked"))
            SecondVaultService.Placement.ALREADY_PLACED -> deny(event, player, lang.msg("second_vault.place.already"))
            SecondVaultService.Placement.NEXT_TO_VAULT -> deny(event, player, lang.msg("second_vault.place.next_to_vault"))
            SecondVaultService.Placement.FAILED -> deny(event, player, lang.msg("second_vault.place.failed"))
        }
    }

    private fun slots(level: Int) =
        org.koin.core.context.GlobalContext.get().get<net.lumalyte.lg.application.services.GuildVaultService>()
            .getCapacityForLevel(level)

    private fun deny(event: BlockPlaceEvent, player: Player, message: net.kyori.adventure.text.Component) {
        event.isCancelled = true
        player.sendMessage(message)
        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1f, 0.9f)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val block = event.clickedBlock ?: return
        if (block.type != Material.CHEST) return
        val guildId = secondVault.guildAt(block.location) ?: return
        event.isCancelled = true
        val player = event.player
        val guild = guildService.getGuild(guildId) ?: return
        if (memberService.getMember(player.uniqueId, guildId) == null) {
            player.sendMessage(lang.msg("second_vault.open.members_only"))
            return
        }
        open(player, guild)
    }

    fun open(player: Player, guild: net.lumalyte.lg.domain.entities.Guild) {
        when (secondVault.open(player, guild)) {
            SecondVaultService.Opening.OPENED -> player.playSound(player.location, Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f)
            SecondVaultService.Opening.NOT_PLACED -> player.sendMessage(lang.msg("second_vault.open.not_placed"))
            SecondVaultService.Opening.NO_ACCESS -> player.sendMessage(lang.msg("notification.vault.inventory.access_denied"))
        }
    }

    /** REQ-246: the same two-step break confirmation and drop rule as the main vault. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.CHEST) return
        val guildId = secondVault.guildAt(event.block.location) ?: return
        val player = event.player
        if (!memberService.hasPermission(player.uniqueId, guildId, RankPermission.BREAK_VAULT)) {
            event.isCancelled = true
            player.sendMessage(lang.msg("notification.vault.break.no_permission"))
            return
        }
        val settings = configService.loadConfig().vault
        val key = player.uniqueId to guildId
        val now = System.currentTimeMillis()
        val last = breakWarnings[key]
        if (last == null || now - last > settings.breakWarningTimeoutSeconds * 1000L) {
            event.isCancelled = true
            breakWarnings[key] = now
            player.sendMessage(lang.msg("second_vault.break.warning"))
            player.sendMessage(
                if (settings.dropItemsOnBreak) lang.msg("second_vault.break.warning_drop") else lang.msg("second_vault.break.warning_keep"),
            )
            player.sendMessage(lang.msg("notification.vault.break.warning_confirm", "seconds" to settings.breakWarningTimeoutSeconds))
            return
        }
        breakWarnings.remove(key)
        event.isDropItems = false
        secondVault.remove(guildId, settings.dropItemsOnBreak, clearBlock = false)
        player.sendMessage(
            if (settings.dropItemsOnBreak) lang.msg("second_vault.break.dropped") else lang.msg("second_vault.break.kept"),
        )
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) = protect(event.blockList())

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) = protect(event.blockList())

    /** Explosions follow the main vault's drop_items_on_explosion rule. */
    private fun protect(blocks: MutableList<org.bukkit.block.Block>) {
        val drop = configService.loadConfig().vault.dropItemsOnExplosion
        blocks.removeIf { block ->
            val guildId = if (block.type == Material.CHEST) secondVault.guildAt(block.location) else null
            if (guildId != null) secondVault.remove(guildId, drop, clearBlock = true)
            guildId != null
        }
    }

    /** REQ-245: withdraw only below the tier; every way of putting items in is refused. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? SecondVaultHolder ?: return
        val player = event.whoClicked as? Player ?: return
        if (!memberService.hasPermission(player.uniqueId, holder.guildId, RankPermission.ACCESS_VAULT)) {
            event.isCancelled = true
            player.sendMessage(lang.msg("notification.vault.inventory.access_denied"))
            player.closeInventory()
            return
        }
        val required = VaultItemPermissions.required(event.action, event.rawSlot, event.inventory.size, goldButton = false)
        if (event.action == InventoryAction.UNKNOWN ||
            !required.all { memberService.hasPermission(player.uniqueId, holder.guildId, it) }
        ) {
            event.isCancelled = true
            player.sendMessage(lang.msg("notification.vault.inventory.action_denied"))
            return
        }
        if (RankPermission.DEPOSIT_TO_VAULT in required && !secondVault.unlocked(holder.guildId)) {
            event.isCancelled = true
            player.sendMessage(lang.msg("second_vault.withdraw_only"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.6f, 1f)
            return
        }
        if (required.isNotEmpty()) secondVault.changed(holder.guildId)
    }

    /** Drags into the vault are refused, as in the main vault. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onDrag(event: InventoryDragEvent) {
        if (event.inventory.holder !is SecondVaultHolder) return
        val size = event.inventory.size
        if ((event.rawSlots + event.newItems.keys).any { it in 0 until size }) {
            event.isCancelled = true
            (event.whoClicked as? Player)?.sendMessage(lang.msg("second_vault.no_drag"))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? SecondVaultHolder ?: return
        secondVault.closed(holder.guildId, event.inventory)
    }
}
