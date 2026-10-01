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
import net.lumalyte.lg.config.BedrockConfig
import net.lumalyte.lg.utils.BedrockIcons
import net.lumalyte.lg.utils.GuiTheme
import net.lumalyte.lg.utils.MenuTitleGlyphs
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.github.retrooper.packetevents.protocol.item.ItemStack as PacketItemStack

/**
 * Sends vanilla items in place of the custom Nexo menu icons to players who should not see them:
 *  - members of guilds that picked the Vanilla menu style ([GuiTheme.VANILLA]);
 *  - Bedrock players, only when `bedrock.java_menu_vanilla_icons` is on (off by default, because
 *    Geyser custom-item mappings can already draw the lg_ icons for them). With
 *    `bedrock.java_menu_plain_titles` on, their themed titles also drop the font-glyph background.
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
    private val bedrockConfig: () -> BedrockConfig,
    packetEventsReady: (() -> Boolean)? = null,
    hookPacketEvents: ((MenuIconAdapter) -> Unit)? = null,
    private val glyphLookup: (String) -> net.kyori.adventure.text.Component? = ::nexoGlyph,
) : PacketListenerAbstract(PacketListenerPriority.HIGHEST), Listener {

    private val bedrockPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val vanillaStylePlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    // Read on the main thread in refresh(); the packet thread only reads these flags.
    @Volatile private var bedrockVanillaIcons = false
    @Volatile private var bedrockPlainTitles = false

    private val packetEventsUp: () -> Boolean = packetEventsReady ?: ::defaultPacketEventsReady
    private val hook: (MenuIconAdapter) -> Unit =
        hookPacketEvents ?: { PacketEvents.getAPI().eventManager.registerListener(it) }
    @Volatile private var hooked = false

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.onlinePlayers.forEach(::refresh)
        // packetevents can enable after LumaGuilds even with the softdepend (seen on SMP Test),
        // so hook now if it is up, otherwise when it enables (onPluginEnable).
        if (!tryHook()) {
            runCatching { plugin.logger.info("Menu icon adapter waiting for packetevents to enable") }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginEnable(event: PluginEnableEvent) = pluginEnabled(event.plugin.name)

    fun pluginEnabled(name: String) {
        if (name.equals("packetevents", ignoreCase = true)) tryHook()
    }

    private fun tryHook(): Boolean {
        if (hooked) return true
        if (!runCatching { packetEventsUp() }.getOrDefault(false)) return false
        hook(this)
        hooked = true
        runCatching { plugin.logger.info("Menu icon adapter active (vanilla icons for Vanilla-style guilds; Bedrock per bedrock.java_menu_* settings)") }
        return true
    }

    /** Re-evaluates whether [player] should be sent vanilla icons. Main thread. */
    fun refresh(player: Player) {
        runCatching { bedrockConfig() }.getOrNull()?.let {
            bedrockVanillaIcons = it.javaMenuVanillaIcons
            bedrockPlainTitles = it.javaMenuPlainTitles
        }
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

    fun showsVanillaIcons(playerId: UUID): Boolean =
        playerId in vanillaStylePlayers || (bedrockVanillaIcons && playerId in bedrockPlayers)

    fun cleansTitlesFor(playerId: UUID): Boolean = bedrockPlainTitles && playerId in bedrockPlayers

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) = refresh(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = forget(event.player.uniqueId)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        refresh(player)
        val title = event.titleOverride() ?: event.view.title()
        if (!MenuTitleGlyphs.hasBackgroundGlyph(title)) return
        if (cleansTitlesFor(player.uniqueId)) {
            event.titleOverride(BedrockIcons.plainTitle(title))
            return
        }
        // Give the background glyph its Nexo font so it draws instead of showing as a box.
        val fixed = MenuTitleGlyphs.withGlyphFonts(title, glyphLookup)
        if (fixed != title) event.titleOverride(fixed)
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

    private fun defaultPacketEventsReady(): Boolean {
        if (plugin.server.pluginManager.getPlugin("packetevents")?.isEnabled != true) return false
        return runCatching { PacketEvents.getAPI().isLoaded && PacketEvents.getAPI().isInitialized }.getOrDefault(false)
    }
}

/** Nexo's glyph component (with its font) for a glyph id, or null when Nexo or the glyph is missing. */
internal fun nexoGlyph(id: String): net.kyori.adventure.text.Component? = runCatching {
    val fonts = com.nexomc.nexo.NexoPlugin.instance().fontManager()
    (fonts.glyphFromID(id) ?: fonts.glyphFromName(id))?.glyphComponent()
}.getOrNull()
