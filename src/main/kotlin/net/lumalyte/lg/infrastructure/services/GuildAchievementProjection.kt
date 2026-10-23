package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.achievements.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent

/** Optional provider bridge: never owns or resets another plugin's advancement namespace. */
internal class GuildAchievementProjection(private val plugin: Plugin, private val nodes: List<GuildAchievement>) : Listener {
    private var provider: Any? = null
    private var service: Class<*>? = null
    private var providerOwner: Plugin? = null
    private var listening = false
    val available: Boolean get() = provider != null

    fun register() {
        if (!listening) {
            plugin.server.pluginManager.registerEvents(this, plugin)
            listening = true
        }
        runCatching {
            val type = Bukkit.getServicesManager().knownServices.firstOrNull {
                it.name == SERVICE_NAME
            } ?: return
            @Suppress("UNCHECKED_CAST")
            val registration = Bukkit.getServicesManager().getRegistration(type as Class<Any>) ?: return
            if (!registration.plugin.isEnabled) return
            val instance = registration.provider
            if (provider === instance) return
            close()
            val nodeType = type.classLoader.loadClass("io.github.badgersmc.advancements.pilot.ProjectionService\$Node")
            val ctor = nodeType.getConstructor(String::class.java, String::class.java, String::class.java,
                List::class.java, Material::class.java, String::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            val definitions = nodes.map { n -> ctor.newInstance(n.id, n.parent, n.title,
                n.description + if (n.personalXp > 0) listOf("Reward: ${n.personalXp} personal Minecraft XP") else emptyList(),
                Material.matchMaterial(n.icon) ?: Material.SHIELD, n.frame, n.x, n.y) }
            type.getMethod("registerTree", Plugin::class.java, String::class.java, ItemStack::class.java, List::class.java)
                .invoke(instance, plugin, NAMESPACE, net.lumalyte.lg.utils.NexoItemProvider.getItemStackOrFallback("lg_banner") {
                    ItemStack.of(Material.WHITE_BANNER)
                }, definitions)
            provider = instance
            service = type
            providerOwner = registration.plugin
            plugin.logger.info("Guild Mastery projection registered with EnthusiaAdvancements")
        }.onFailure { plugin.logger.warning("Guild Mastery projection unavailable; dialog progress remains authoritative: ${it.javaClass.simpleName}") }
    }

    @EventHandler fun registered(event: ServiceRegisterEvent) {
        if (event.provider.service.name == SERVICE_NAME) register()
    }

    @EventHandler fun unregistered(event: ServiceUnregisterEvent) {
        if (event.provider.provider !== provider) return
        close()
        register()
    }

    @EventHandler fun disabled(event: PluginDisableEvent) {
        if (event.plugin === providerOwner) close()
    }

    fun project(player: Player, record: AchievementRecord) {
        val instance = provider ?: return
        runCatching {
            if (service!!.getMethod("ready", Player::class.java).invoke(instance, player) != true) return
            val progress = nodes.associate { n -> n.id to (((record.progress[n.id] ?: 0).toLong() * 1000L / n.goal).coerceIn(0,1000).toInt()) }
            service!!.getMethod("project", Plugin::class.java, String::class.java, Player::class.java, Map::class.java)
                .invoke(instance, plugin, NAMESPACE, player, progress)
        }.onFailure { disable(it) }
    }

    fun celebrate(player: Player, id: String) {
        val instance = provider ?: return
        runCatching { service!!.getMethod("celebrate", Plugin::class.java, String::class.java, Player::class.java, String::class.java)
            .invoke(instance, plugin, NAMESPACE, player, id) }.onFailure { disable(it) }
    }

    fun close() {
        val instance = provider
        val type = service
        provider = null
        service = null
        providerOwner = null
        if (instance != null && type != null) runCatching {
            type.getMethod("removeTree", Plugin::class.java, String::class.java).invoke(instance, plugin, NAMESPACE)
        }
    }

    private fun disable(error: Throwable) {
        plugin.logger.warning("Guild Mastery projection stopped; progress and personal reward tracking remain active: ${error.javaClass.simpleName}")
        close()
    }
    companion object {
        const val NAMESPACE = "lumaguilds_mastery"
        private const val SERVICE_NAME = "io.github.badgersmc.advancements.pilot.ProjectionService"
    }
}
