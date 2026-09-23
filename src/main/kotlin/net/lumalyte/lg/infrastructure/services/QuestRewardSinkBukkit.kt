package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.application.services.QuestRewardSink
import net.lumalyte.lg.domain.entities.QuestItemReward
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID

class QuestRewardSinkBukkit(
    private val progressionService: ProgressionService,
    plugin: Plugin,
) : QuestRewardSink {
    private val rewardTransactionKey = NamespacedKey(plugin, "quest_reward_tx")
    private val rewardItemKey = NamespacedKey(plugin, "quest_reward_item")

    override fun awardExperience(guildId: UUID, amount: Int, transactionId: UUID): Boolean =
        amount <= 0 || progressionService.awardUncappedSystemExperienceOnce(
            guildId,
            amount,
            ExperienceSource.WEEKLY_ACTIVITY,
            transactionId,
        )

    override fun awardItems(
        actorId: UUID,
        rewards: List<QuestItemReward>,
        transactionId: UUID,
    ): Boolean {
        if (rewards.isEmpty()) return true
        val player = Bukkit.getPlayer(actorId) ?: return false
        val expected = rewards.groupingBy { it.itemId }.fold(0) { total, reward ->
            total + reward.amount.coerceAtLeast(0)
        }

        val pendingStacks = mutableListOf<ItemStack>()
        for ((itemId, requiredAmount) in expected) {
            var missing = requiredAmount - deliveredAmount(
                player.inventory.storageContents,
                transactionId,
                itemId,
            )
            if (missing <= 0) continue

            val template = NexoItemProvider.getItemStackOrFallback(itemId) {
                ItemStack.of(Material.CHEST)
            }
            while (missing > 0) {
                val item = template.clone().apply {
                    amount = missing.coerceAtMost(maxStackSize)
                    editMeta { meta ->
                        meta.persistentDataContainer.set(
                            rewardTransactionKey,
                            PersistentDataType.STRING,
                            transactionId.toString(),
                        )
                        meta.persistentDataContainer.set(
                            rewardItemKey,
                            PersistentDataType.STRING,
                            itemId,
                        )
                    }
                }
                pendingStacks += item
                missing -= item.amount
            }
        }

        // Do not intentionally create a partial payout. Because reward stacks carry
        // unique transaction metadata, conservatively require one empty storage slot
        // per pending stack instead of relying on untagged stack capacity.
        val emptySlots = player.inventory.storageContents.count { it == null || it.type.isAir }
        if (emptySlots < pendingStacks.size) return false

        pendingStacks.forEach { item ->
            if (player.inventory.addItem(item).isNotEmpty()) return false
        }
        return true
    }

    override fun finalizeItems(actorId: UUID, transactionId: UUID) {
        val player = Bukkit.getPlayer(actorId) ?: return
        player.inventory.storageContents.filterNotNull().forEach { item ->
            val data = item.itemMeta.persistentDataContainer
            if (data.get(rewardTransactionKey, PersistentDataType.STRING) != transactionId.toString()) {
                return@forEach
            }
            item.editMeta { meta ->
                meta.persistentDataContainer.remove(rewardTransactionKey)
                meta.persistentDataContainer.remove(rewardItemKey)
            }
        }
    }

    private fun deliveredAmount(
        contents: Array<ItemStack?>,
        transactionId: UUID,
        itemId: String,
    ): Int = contents.filterNotNull().sumOf { item ->
        val data = item.itemMeta.persistentDataContainer
        if (data.get(rewardTransactionKey, PersistentDataType.STRING) == transactionId.toString() &&
            data.get(rewardItemKey, PersistentDataType.STRING) == itemId
        ) item.amount else 0
    }
}
