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
import net.kyori.adventure.text.Component
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
@Suppress("LongParameterList", "TooManyFunctions") // Test seams for Bukkit, packetevents and Nexo.
internal class MenuIconAdapter(
    private val plugin: Plugin,
    private val platform: PlatformDetectionService,
    private val guildService: GuildService,
    private val bedrockConfig: () -> BedrockConfig,
    packetEventsReady: (() -> Boolean)? = null,
    hookPacketEvents: ((MenuIconAdapter) -> Unit)? = null,
    private val glyphLookup: (String) -> Component? = ::nexoGlyph,
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

    /** Registers the Bukkit listeners and hooks the packet listener once packetevents is up. */
    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.onlinePlayers.forEach(::refresh)
        // packetevents can enable after LumaGuilds even with the softdepend (seen on SMP Test),
        // so hook now if it is up, otherwise when it enables (onPluginEnable).
        if (!tryHook()) {
            runCatching { plugin.logger.info("Menu icon adapter waiting for packetevents to enable") }
        }
    }

    /** Hooks the packet listener when packetevents enables after LumaGuilds. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginEnable(event: PluginEnableEvent) = pluginEnabled(event.plugin.name)

    /** Hooks the packet listener if the plugin called [name] is packetevents. */
    fun pluginEnabled(name: String) {
        if (name.equals("packetevents", ignoreCase = true)) tryHook()
    }

    private fun tryHook(): Boolean {
        if (!hooked && runCatching { packetEventsUp() }.getOrDefault(false)) {
            hook(this)
            hooked = true
            runCatching {
                plugin.logger.info(
                    "Menu icon adapter active (vanilla icons for Vanilla-style guilds; " +
                        "Bedrock per bedrock.java_menu_* settings)",
                )
            }
        }
        return hooked
    }

    /** Re-evaluates whether [player] should be sent vanilla icons. Main thread. */
    fun refresh(player: Player) {
        runCatching { bedrockConfig() }.getOrNull()?.let {
            bedrockVanillaIcons = it.javaMenuVanillaIcons
            bedrockPlainTitles = it.javaMenuPlainTitles
        }
        val id = player.uniqueId
        val bedrock = runCatching { platform.isBedrockPlayer(player) }.getOrDefault(false)
        if (bedrock) bedrockPlayers.add(id) else bedrockPlayers.remove(id)
        val vanillaStyle =
            runCatching {
                guildService.getPlayerGuilds(id).any { it.guiTheme == GuiTheme.VANILLA }
            }.getOrDefault(false)
        if (vanillaStyle) vanillaStylePlayers.add(id) else vanillaStylePlayers.remove(id)
    }

    /** Drops what is remembered about [playerId]. */
    fun forget(playerId: UUID) {
        bedrockPlayers.remove(playerId)
        vanillaStylePlayers.remove(playerId)
    }

    /** Whether [playerId] is sent vanilla items in place of LumaGuilds menu icons. */
    fun showsVanillaIcons(playerId: UUID): Boolean =
        playerId in vanillaStylePlayers || bedrockVanillaIcons && playerId in bedrockPlayers

    /** Whether themed menu titles lose their background glyph for [playerId]. */
    fun cleansTitlesFor(playerId: UUID): Boolean = bedrockPlainTitles && playerId in bedrockPlayers

    /** Works out the joining player's icon and title treatment. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) = refresh(event.player)

    /** Forgets the player who left. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = forget(event.player.uniqueId)

    /** Fixes up the title of a themed guild menu as it opens. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        refresh(player)
        val title = event.titleOverride() ?: event.view.title()
        replacementTitle(player.uniqueId, title)?.let(event::titleOverride)
    }

    private fun replacementTitle(playerId: UUID, title: Component): Component? = when {
        !MenuTitleGlyphs.hasBackgroundGlyph(title) -> null
        cleansTitlesFor(playerId) -> BedrockIcons.plainTitle(title)
        // Give the background glyph its Nexo font so it draws instead of showing as a box.
        else -> MenuTitleGlyphs.withGlyphFonts(title, glyphLookup).takeIf { it != title }
    }

    override fun onPacketSend(event: PacketSendEvent) {
        val swap = iconSwapFor(event) ?: return
        val uuid = event.user?.uuid
        if (uuid != null && showsVanillaIcons(uuid)) swap(event)
    }

    private fun iconSwapFor(event: PacketSendEvent): ((PacketSendEvent) -> Unit)? = when (event.packetType) {
        PacketType.Play.Server.WINDOW_ITEMS -> ::swapWindowItems
        PacketType.Play.Server.SET_SLOT -> ::swapSetSlot
        else -> null
    }

    private fun swapWindowItems(event: PacketSendEvent) {
        val wrapper = WrapperPlayServerWindowItems(event)
        var changed = false
        val items = wrapper.items.map { item -> vanillaStandIn(item)?.also { changed = true } ?: item }
        if (changed) {
            wrapper.items = items
            event.markForReEncode(true)
        }
    }

    private fun swapSetSlot(event: PacketSendEvent) {
        val wrapper = WrapperPlayServerSetSlot(event)
        vanillaStandIn(wrapper.item)?.let {
            wrapper.item = it
            event.markForReEncode(true)
        }
    }

    private fun vanillaStandIn(item: PacketItemStack?): PacketItemStack? {
        if (item == null || item.isEmpty || !isLumaGuildsIcon(item)) return null
        return runCatching {
            BedrockIcons.toBedrock(SpigotConversionUtil.toBukkitItemStack(item))
                ?.let(SpigotConversionUtil::fromBukkitItemStack)
        }.getOrNull()
    }

    // Cheap NBT check so only our icons pay for a Bukkit conversion.
    private fun isLumaGuildsIcon(item: PacketItemStack): Boolean {
        val data = item.getComponent(ComponentTypes.CUSTOM_DATA).orElse(null) ?: return false
        return data.getCompoundTagOrNull(BedrockIcons.PDC_ROOT)?.getTagOrNull(BedrockIcons.PDC_KEY) != null
    }

    private fun defaultPacketEventsReady(): Boolean {
        if (plugin.server.pluginManager.getPlugin("packetevents")?.isEnabled != true) return false
        return runCatching { PacketEvents.getAPI().isLoaded && PacketEvents.getAPI().isInitialized }.getOrDefault(false)
    }
}

/** Nexo's glyph component (with its font) for a glyph id, or null when Nexo or the glyph is missing. */
internal fun nexoGlyph(id: String): Component? = runCatching {
    val fonts = com.nexomc.nexo.NexoPlugin.instance().fontManager()
    (fonts.glyphFromID(id) ?: fonts.glyphFromName(id))?.glyphComponent()
}.getOrNull()
