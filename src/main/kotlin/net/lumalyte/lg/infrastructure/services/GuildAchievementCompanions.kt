package net.lumalyte.lg.infrastructure.services

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

/** Optional runtime event adapters; companion classes are not shaded or replaced. */
internal class GuildAchievementCompanions(private val plugin: JavaPlugin, private val runtime: GuildAchievementRuntime) : Listener {
    private val bound = mutableSetOf<String>()
    private val delivered = Collections.newSetFromMap(WeakHashMap<Event, Boolean>())
    private val streakFile = plugin.dataFolder.toPath().resolve("achievement-koth-streaks.json")
    private val streaks = if (Files.exists(streakFile)) JSONObject(Files.readString(streakFile)) else JSONObject()

    fun start() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        bind()
    }
    @EventHandler fun enabled(event: PluginEnableEvent) { bind(); runtime.refreshProjection() }

    private fun bind() {
        hook("EnthusiaMarket", "net.badgersmc.em.events.ShopCreatedEvent") { event ->
            val owner = value(event, "OwnerId") as? UUID ?: return@hook
            Bukkit.getPlayer(owner)?.let { runtime.signal(it, "SHOP_CREATED") }
        }
        hook("EnthusiaKOTH", "net.badgersmc.ek.api.KothLifecycleEvent") { event ->
            if (value(event, "Lifecycle").toString() != "COMPLETED") return@hook
            val snapshot = value(event, "Snapshot") ?: return@hook
            if (value(snapshot, "PrivateTest") != false) return@hook
            val winner = value(snapshot, "Winner") ?: return@hook
            if (value(winner, "Mode").toString() != "GUILD") return@hook
            val guild = value(winner, "Id") as? UUID ?: return@hook
            val eventId = value(snapshot, "EventId") as? UUID ?: return@hook
            val arena = value(snapshot, "ArenaId") as? String ?: return@hook
            val prior = streaks.optJSONObject(arena)
            val seen = prior?.optJSONArray("seen")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            if (eventId.toString() in seen) return@hook
            val count = if (prior?.optString("guild") == guild.toString()) prior.optInt("count") + 1 else 1
            streaks.put(arena, JSONObject().put("guild", guild.toString()).put("count", count.coerceAtMost(3))
                .put("seen", org.json.JSONArray((seen + eventId.toString()).toList())))
            val tmp = streakFile.resolveSibling(streakFile.fileName.toString() + ".tmp")
            Files.createDirectories(streakFile.parent)
            Files.writeString(tmp, streaks.toString())
            Files.move(tmp, streakFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            runtime.shared(guild, "KOTH_CAPTURE", receipt = "koth:$eventId")
            runtime.shared(guild, "KOTH_CONSECUTIVE_CAPTURE", amount = count.coerceAtMost(3), absolute = true, receipt = "streak:$eventId")
        }
    }

    private fun hook(ownerName: String, className: String, callback: (Event) -> Unit) {
        val owner = Bukkit.getPluginManager().getPlugin(ownerName)?.takeIf { it.isEnabled } ?: return
        if (className in bound) return
        runCatching {
            val type = Class.forName(className, false, owner.javaClass.classLoader).asSubclass(Event::class.java)
            plugin.server.pluginManager.registerEvent(type, this, EventPriority.MONITOR,
                EventExecutor { _, event ->
                    if (!Bukkit.isPrimaryThread() || !delivered.add(event)) return@EventExecutor
                    runCatching { callback(event) }.onFailure {
                        plugin.logger.warning("Guild achievement companion event unavailable: $className (${it.javaClass.simpleName})")
                    }
                }, plugin, true)
            bound += className
            plugin.logger.info("Guild achievement companion hook: $className")
        }.onFailure { plugin.logger.info("Guild achievement companion contract absent: $className") }
    }
    private fun value(target: Any, property: String): Any? = target.javaClass.getMethod("get$property").invoke(target)
}
