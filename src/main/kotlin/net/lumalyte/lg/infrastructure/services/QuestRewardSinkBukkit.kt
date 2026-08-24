package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.QuestRewardSink
import net.lumalyte.lg.domain.entities.QuestItemReward
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.UUID

class QuestRewardSinkBukkit(private val progressionService: ProgressionService) : QuestRewardSink {
    override fun awardExperience(guildId: UUID, amount: Int) {
        if (amount > 0) progressionService.awardExperience(guildId, amount, ExperienceSource.WEEKLY_ACTIVITY)
    }

    override fun awardItems(actorId: UUID, rewards: List<QuestItemReward>) {
        val player = Bukkit.getPlayer(actorId) ?: return
        rewards.forEach { reward ->
            val item = NexoItemProvider.getItemStackOrFallback(reward.itemId) {
                ItemStack.of(Material.CHEST)
            }.apply { amount = reward.amount.coerceIn(1, maxStackSize) }
            player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }
    }
}
