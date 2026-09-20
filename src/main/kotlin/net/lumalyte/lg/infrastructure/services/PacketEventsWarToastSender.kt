package net.lumalyte.lg.infrastructure.services

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.advancements.Advancement
import com.github.retrooper.packetevents.protocol.advancements.AdvancementDisplay
import com.github.retrooper.packetevents.protocol.advancements.AdvancementHolder
import com.github.retrooper.packetevents.protocol.advancements.AdvancementProgress
import com.github.retrooper.packetevents.protocol.advancements.AdvancementType
import com.github.retrooper.packetevents.resources.ResourceLocation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAdvancements
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID

enum class WarToastFrame {
    TASK,
    GOAL,
    CHALLENGE,
}

interface WarToastSender {
    fun show(
        player: Player,
        notificationId: UUID,
        title: Component,
        description: Component,
        icon: ItemStack,
        frame: WarToastFrame,
    ): Boolean
}

class PacketEventsWarToastSender(
    private val plugin: Plugin,
) : WarToastSender {
    override fun show(
        player: Player,
        notificationId: UUID,
        title: Component,
        description: Component,
        icon: ItemStack,
        frame: WarToastFrame,
    ): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            val key = ResourceLocation(
                "lumaguilds",
                "war_toast/${notificationId.toString().replace("-", "")}",
            )
            val display = AdvancementDisplay(
                title,
                description,
                SpigotConversionUtil.fromBukkitItemStack(icon),
                AdvancementType.valueOf(frame.name),
                null,
                true,
                true,
                0f,
                0f,
            )
            val advancement = Advancement(
                null,
                display,
                listOf(listOf(CRITERION)),
                false,
            )
            val holder = AdvancementHolder(key, advancement)
            val progress = AdvancementProgress(
                mapOf(
                    CRITERION to AdvancementProgress.CriterionProgress(
                        System.currentTimeMillis()
                    )
                )
            )

            PacketEvents.getAPI().playerManager.sendPacket(
                player,
                WrapperPlayServerUpdateAdvancements(
                    false,
                    listOf(holder),
                    emptySet(),
                    mapOf(key to progress),
                    true,
                ),
            )
            PacketEvents.getAPI().playerManager.sendPacket(
                player,
                WrapperPlayServerUpdateAdvancements(
                    false,
                    emptyList(),
                    setOf(key),
                    emptyMap(),
                    true,
                ),
            )
            true
        }.getOrDefault(false)
    }

    private fun isAvailable(): Boolean {
        if (plugin.server.pluginManager.getPlugin("packetevents")?.isEnabled != true) {
            return false
        }
        return runCatching {
            PacketEvents.getAPI().isLoaded && PacketEvents.getAPI().isInitialized
        }.getOrDefault(false)
    }

    companion object {
        private const val CRITERION = "shown"
    }
}
