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
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.SeasonalIcons
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.Optional
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
 * Members of a guild using a holiday style ([GuiTheme.seasonalIcons]) are instead sent each icon
 * drawn with its `<id>_<style>` Nexo variant where one exists (REQ-121, [SeasonalIcons]).
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
    private val variantLookup: (String) -> ItemStack? = NexoItemProvider::getItemStack,
) : PacketListenerAbstract(PacketListenerPriority.HIGHEST), Listener {
    private val bedrockPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val vanillaStylePlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val seasonalStylePlayers = ConcurrentHashMap<UUID, GuiTheme>()

    // Built Nexo variants by id (Optional.empty() = no such variant), dropped when Nexo reloads.
    private val variants = ConcurrentHashMap<String, Optional<ItemStack>>()

    @Volatile private var variantsLoadCount = -1

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
        val themes = runCatching { guildService.getPlayerGuilds(id).map { it.guiTheme } }.getOrDefault(emptyList())
        val vanillaStyle = themes.any { it == GuiTheme.VANILLA }
        if (vanillaStyle) vanillaStylePlayers.add(id) else vanillaStylePlayers.remove(id)
        val seasonal = SeasonalIcons.styleFor(themes)
        if (seasonal != null) seasonalStylePlayers[id] = seasonal else seasonalStylePlayers.remove(id)
    }

    /** Drops what is remembered about [playerId]. */
    fun forget(playerId: UUID) {
        bedrockPlayers.remove(playerId)
        vanillaStylePlayers.remove(playerId)
        seasonalStylePlayers.remove(playerId)
    }

    /** Whether [playerId] is sent vanilla items in place of LumaGuilds menu icons. */
    fun showsVanillaIcons(playerId: UUID): Boolean =
        playerId in vanillaStylePlayers || bedrockVanillaIcons && playerId in bedrockPlayers

    /** The holiday style whose icons [playerId] is sent, or null for the normal icons. */
    fun seasonalStyleFor(playerId: UUID): GuiTheme? =
        if (showsVanillaIcons(playerId)) null else seasonalStylePlayers[playerId]

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

    private fun replacementTitle(playerId: UUID, title: Component): Component? {
        return when {
            !MenuTitleGlyphs.hasBackgroundGlyph(title) -> null
            cleansTitlesFor(playerId) -> BedrockIcons.plainTitle(title)
            // Give the background glyph its Nexo font so it draws instead of showing as a box.
            else -> MenuTitleGlyphs.withGlyphFonts(title, glyphLookup).takeIf { it != title }
        }
    }

    override fun onPacketSend(event: PacketSendEvent) {
        val type = event.packetType
        if (type != PacketType.Play.Server.WINDOW_ITEMS && type != PacketType.Play.Server.SET_SLOT) return
        val uuid = event.user?.uuid ?: return
        val standIn: (PacketItemStack?) -> PacketItemStack? =
            if (showsVanillaIcons(uuid)) {
                ::vanillaStandIn
            } else {
                seasonalStyleFor(uuid)?.let { style -> { item -> seasonalStandIn(item, style) } } ?: return
            }
        if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            swapWindowItems(event, standIn)
        } else {
            swapSetSlot(event, standIn)
        }
    }

    private fun swapWindowItems(event: PacketSendEvent, standIn: (PacketItemStack?) -> PacketItemStack?) {
        val wrapper = WrapperPlayServerWindowItems(event)
        var changed = false
        val items = wrapper.items.map { item -> standIn(item)?.also { changed = true } ?: item }
        if (changed) {
            wrapper.items = items
            event.markForReEncode(true)
        }
    }

    private fun swapSetSlot(event: PacketSendEvent, standIn: (PacketItemStack?) -> PacketItemStack?) {
        val wrapper = WrapperPlayServerSetSlot(event)
        standIn(wrapper.item)?.let {
            wrapper.item = it
            event.markForReEncode(true)
        }
    }

    private fun seasonalStandIn(item: PacketItemStack?, style: GuiTheme): PacketItemStack? {
        if (item == null || item.isEmpty || !hasPdcKey(item, SeasonalIcons.PDC_KEY)) return null
        return runCatching { restyledPacket(item, style) }.getOrNull()
    }

    private fun restyledPacket(item: PacketItemStack, style: GuiTheme): PacketItemStack? {
        val bukkit = SpigotConversionUtil.toBukkitItemStack(item)
        val variantId = SeasonalIcons.iconId(bukkit)?.let { SeasonalIcons.variantId(it, style) } ?: return null
        val variant = variant(variantId) ?: return null
        return SpigotConversionUtil.fromBukkitItemStack(SeasonalIcons.restyle(bukkit, variant))
    }

    /** The built Nexo item [id], cached until Nexo reloads its items. */
    private fun variant(id: String): ItemStack? {
        val loads = NexoItemProvider.loadCount
        if (loads != variantsLoadCount) {
            variants.clear()
            variantsLoadCount = loads
        }
        return variants.computeIfAbsent(id) { Optional.ofNullable(variantLookup(it)) }.orElse(null)
    }

    private fun vanillaStandIn(item: PacketItemStack?): PacketItemStack? {
        if (item == null || item.isEmpty || !isLumaGuildsIcon(item)) return null
        return runCatching {
            BedrockIcons.toBedrock(SpigotConversionUtil.toBukkitItemStack(item))
                ?.let(SpigotConversionUtil::fromBukkitItemStack)
        }.getOrNull()
    }

    // Cheap NBT check so only our icons pay for a Bukkit conversion.
    private fun isLumaGuildsIcon(item: PacketItemStack): Boolean = hasPdcKey(item, BedrockIcons.PDC_KEY)

    private fun hasPdcKey(item: PacketItemStack, key: String): Boolean {
        val data = item.getComponent(ComponentTypes.CUSTOM_DATA).orElse(null) ?: return false
        return data.getCompoundTagOrNull(BedrockIcons.PDC_ROOT)?.getTagOrNull(key) != null
    }

    private fun defaultPacketEventsReady(): Boolean {
        if (plugin.server.pluginManager.getPlugin("packetevents")?.isEnabled != true) return false
        return runCatching { PacketEvents.getAPI().isLoaded && PacketEvents.getAPI().isInitialized }.getOrDefault(false)
    }
}

/** Nexo's glyph component (with its font) for a glyph id, or null when Nexo or the glyph is missing. */
internal fun nexoGlyph(id: String): Component? {
    return runCatching {
        val fonts = com.nexomc.nexo.NexoPlugin.instance().fontManager()
        (fonts.glyphFromID(id) ?: fonts.glyphFromName(id))?.glyphComponent()
    }.getOrNull()
}
