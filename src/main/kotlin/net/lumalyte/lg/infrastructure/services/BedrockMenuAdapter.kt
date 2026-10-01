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
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.utils.BedrockIcons
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
 * Makes the Java chest menus readable for Bedrock players:
 *  - themed menu titles drop the font-glyph background (Bedrock draws it as junk characters);
 *  - Nexo icons are sent as their vanilla fallback item.
 *
 * Only what Bedrock players are *sent* changes. Server-side items, click handling and
 * everything Java players see stay exactly as they are. Icons are swapped at packet level
 * so menu refreshes (InventoryFramework `update()`) are covered too.
 */
class BedrockMenuAdapter(
    private val plugin: Plugin,
    private val platform: PlatformDetectionService,
) : PacketListenerAbstract(PacketListenerPriority.HIGHEST), Listener {

    private val bedrockPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.onlinePlayers.forEach(::track)
        if (packetEventsReady()) {
            PacketEvents.getAPI().eventManager.registerListener(this)
            plugin.logger.info("Bedrock menu adapter active (vanilla icons and plain titles for Bedrock players)")
        } else {
            plugin.logger.info("PacketEvents not available - Bedrock players keep plain titles but see Nexo base items")
        }
    }

    private fun track(player: Player) {
        if (platform.isBedrockPlayer(player)) bedrockPlayers.add(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) = track(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        bedrockPlayers.remove(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (player.uniqueId !in bedrockPlayers) return
        val title = event.titleOverride() ?: event.view.title()
        if (!BedrockIcons.isThemedTitle(PlainTextComponentSerializer.plainText().serialize(title))) return
        event.titleOverride(BedrockIcons.plainTitle(title))
    }

    override fun onPacketSend(event: PacketSendEvent) {
        val type = event.packetType
        if (type != PacketType.Play.Server.WINDOW_ITEMS && type != PacketType.Play.Server.SET_SLOT) return
        val uuid = event.user?.uuid ?: return
        if (uuid !in bedrockPlayers) return

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
