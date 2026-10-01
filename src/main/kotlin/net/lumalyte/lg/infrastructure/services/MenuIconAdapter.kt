package net.lumalyte.lg.infrastructure.services

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.component.ComponentTypes
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.utils.BedrockIcons
import net.lumalyte.lg.utils.GuiTheme
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.github.retrooper.packetevents.protocol.item.ItemStack as PacketItemStack

/**
 * Sends vanilla items in place of the custom Nexo menu icons to players who should not see them:
 *  - Bedrock players (Geyser cannot draw Nexo item models), who also get themed titles without
 *    the font-glyph background;
 *  - members of guilds that picked the Vanilla menu style ([GuiTheme.VANILLA]).
 *
 * Only what those players are *sent* changes. Server-side items, click handling and what everyone
 * else sees stay exactly as they are. Icons are swapped at packet level so InventoryFramework
 * refreshes are covered too. The decision is refreshed every time a player opens an inventory, so
 * a guild switching style takes effect on the next menu that opens.
 */
class MenuIconAdapter(
    private val plugin: Plugin,
    private val platform: PlatformDetectionService,
    private val guildService: GuildService,
) : PacketListenerAbstract(PacketListenerPriority.HIGHEST), Listener {

    private val bedrockPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val vanillaStylePlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.onlinePlayers.forEach(::refresh)
        if (packetEventsReady()) {
            PacketEvents.getAPI().eventManager.registerListener(this)
            plugin.logger.info("Menu icon adapter active (vanilla icons for Bedrock players and Vanilla-style guilds)")
        } else {
            plugin.logger.info("PacketEvents not available - Bedrock players and Vanilla-style guilds will still see Nexo icons")
        }
    }

    /** Re-evaluates whether [player] should be sent vanilla icons. Main thread. */
    fun refresh(player: Player) {
        val id = player.uniqueId
        if (runCatching { platform.isBedrockPlayer(player) }.getOrDefault(false)) bedrockPlayers.add(id) else bedrockPlayers.remove(id)
        val vanillaStyle = runCatching {
            guildService.getPlayerGuilds(id).any { it.guiTheme == GuiTheme.VANILLA }
        }.getOrDefault(false)
        if (vanillaStyle) vanillaStylePlayers.add(id) else vanillaStylePlayers.remove(id)
    }

    fun forget(playerId: UUID) {
        bedrockPlayers.remove(playerId)
        vanillaStylePlayers.remove(playerId)
    }

    fun showsVanillaIcons(playerId: UUID): Boolean = playerId in bedrockPlayers || playerId in vanillaStylePlayers

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) = refresh(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = forget(event.player.uniqueId)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        refresh(player)
        if (player.uniqueId !in bedrockPlayers) return
        val title = event.titleOverride() ?: event.view.title()
        if (!BedrockIcons.isThemedTitle(PlainTextComponentSerializer.plainText().serialize(title))) return
        event.titleOverride(BedrockIcons.plainTitle(title))
    }

    override fun onPacketSend(event: PacketSendEvent) {
        val type = event.packetType
        if (type != PacketType.Play.Server.WINDOW_ITEMS && type != PacketType.Play.Server.SET_SLOT) return
        val uuid = event.user?.uuid ?: return
        if (!showsVanillaIcons(uuid)) return

        if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            val wrapper = WrapperPlayServerWindowItems(event)
            var changed = false
            val items = wrapper.items.map { item -> swap(item)?.also { changed = true } ?: item }
            if (changed) {
                wrapper.items = items
                event.markForReEncode(true)
            }
        } else {
            val wrapper = WrapperPlayServerSetSlot(event)
            val swapped = swap(wrapper.item) ?: return
            wrapper.item = swapped
            event.markForReEncode(true)
        }
    }

    /** Vanilla stand-in for a tagged LumaGuilds icon, or null to leave the item alone. */
    private fun swap(item: PacketItemStack?): PacketItemStack? {
        if (item == null || item.isEmpty || !isTagged(item)) return null
        return runCatching {
            BedrockIcons.toBedrock(SpigotConversionUtil.toBukkitItemStack(item))
                ?.let(SpigotConversionUtil::fromBukkitItemStack)
        }.getOrNull()
    }

    /** Cheap NBT check so only our icons pay for a Bukkit conversion. */
    private fun isTagged(item: PacketItemStack): Boolean {
        val data = item.getComponent(ComponentTypes.CUSTOM_DATA).orElse(null) ?: return false
        return data.getCompoundTagOrNull(BedrockIcons.PDC_ROOT)?.getTagOrNull(BedrockIcons.PDC_KEY) != null
    }

    private fun packetEventsReady(): Boolean {
        if (plugin.server.pluginManager.getPlugin("packetevents")?.isEnabled != true) return false
        return runCatching { PacketEvents.getAPI().isLoaded && PacketEvents.getAPI().isInitialized }.getOrDefault(false)
    }
}
