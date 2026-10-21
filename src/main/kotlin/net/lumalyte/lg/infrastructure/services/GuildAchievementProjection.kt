package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.achievements.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/** Optional provider bridge: never owns or resets another plugin's advancement namespace. */
internal class GuildAchievementProjection(private val plugin: Plugin, private val nodes: List<GuildAchievement>) {
    private var provider: Any? = null
    private var service: Class<*>? = null
    val available: Boolean get() = provider != null

    fun register() {
        runCatching {
            val type = Bukkit.getServicesManager().knownServices.firstOrNull {
                it.name == "io.github.badgersmc.advancements.pilot.ProjectionService"
            } ?: return
            @Suppress("UNCHECKED_CAST")
            val instance = Bukkit.getServicesManager().getRegistration(type as Class<Any>)?.provider ?: return
            val nodeType = type.classLoader.loadClass("io.github.badgersmc.advancements.pilot.ProjectionService\$Node")
            val ctor = nodeType.getConstructor(String::class.java, String::class.java, String::class.java,
                List::class.java, Material::class.java, String::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            val definitions = nodes.map { n -> ctor.newInstance(n.id, n.parent, n.title,
                n.description + if (n.personalXp > 0) listOf("Reward: ${n.personalXp} personal Minecraft XP") else emptyList(),
                Material.matchMaterial(n.icon) ?: Material.SHIELD, n.frame, n.x, n.y) }
            type.getMethod("registerTree", Plugin::class.java, String::class.java, ItemStack::class.java, List::class.java)
                .invoke(instance, plugin, NAMESPACE, ItemStack.of(Material.WHITE_BANNER), definitions)
            provider = instance
            service = type
            plugin.logger.info("Guild Mastery projection registered with EnthusiaAdvancements")
        }.onFailure { plugin.logger.warning("Guild Mastery projection unavailable; dialog progress remains authoritative: ${it.javaClass.simpleName}") }
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
        val instance = provider ?: return
        runCatching { service!!.getMethod("removeTree", Plugin::class.java, String::class.java).invoke(instance, plugin, NAMESPACE) }
        provider = null
    }

    private fun disable(error: Throwable) {
        plugin.logger.warning("Guild Mastery projection stopped; progress and personal reward tracking remain active: ${error.javaClass.simpleName}")
        provider = null
    }
    companion object { const val NAMESPACE = "lumaguilds_mastery" }
}
