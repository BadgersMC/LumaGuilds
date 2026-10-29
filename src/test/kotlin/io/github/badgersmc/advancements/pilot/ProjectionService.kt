package io.github.badgersmc.advancements.pilot

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

/** Test-only ABI fixture. Production continues to discover the optional service reflectively. */
interface ProjectionService {
    data class Node(val id: String, val parent: String?, val title: String, val description: List<String>,
        val icon: Material, val frame: String, val x: Float, val y: Float)
    fun registerTree(owner: Plugin, namespace: String, icon: ItemStack, nodes: List<Node>)
    fun removeTree(owner: Plugin, namespace: String)
    fun ready(player: Player): Boolean
    fun project(owner: Plugin, namespace: String, player: Player, progress: Map<String, Int>)
    fun celebrate(owner: Plugin, namespace: String, player: Player, id: String)
}
