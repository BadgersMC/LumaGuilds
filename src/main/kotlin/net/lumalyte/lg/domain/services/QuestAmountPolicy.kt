package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestTargetRarity
import net.lumalyte.lg.domain.values.QuestAction

data class QuestAmountRange(val minimum: Long, val maximum: Long)

object QuestAmountPolicy {
    fun range(action: QuestAction, rarity: QuestTargetRarity): QuestAmountRange = when (action) {
        QuestAction.MINE_BLOCKS -> when (rarity) {
            QuestTargetRarity.BULK -> QuestAmountRange(1_000, 25_000)
            QuestTargetRarity.COMMON -> QuestAmountRange(250, 5_000)
            QuestTargetRarity.UNCOMMON -> QuestAmountRange(100, 2_500)
            QuestTargetRarity.RARE -> QuestAmountRange(32, 750)
            QuestTargetRarity.PRECIOUS -> QuestAmountRange(1, 64)
        }
        QuestAction.PLACE_BLOCKS -> when (rarity) {
            QuestTargetRarity.BULK -> QuestAmountRange(500, 10_000)
            QuestTargetRarity.COMMON -> QuestAmountRange(128, 3_000)
            QuestTargetRarity.UNCOMMON -> QuestAmountRange(64, 1_000)
            QuestTargetRarity.RARE -> QuestAmountRange(8, 256)
            QuestTargetRarity.PRECIOUS -> QuestAmountRange(1, 32)
        }
        QuestAction.HARVEST_CROPS -> QuestAmountRange(250, 10_000)
        QuestAction.KILL_PLAYERS -> QuestAmountRange(10, 100)
        QuestAction.KILL_MOBS -> when (rarity) {
            QuestTargetRarity.PRECIOUS -> QuestAmountRange(1, 3)
            QuestTargetRarity.RARE -> QuestAmountRange(2, 20)
            else -> QuestAmountRange(25, 500)
        }
        QuestAction.CRAFT_ITEMS -> when (rarity) {
            QuestTargetRarity.PRECIOUS -> QuestAmountRange(1, 32)
            QuestTargetRarity.RARE -> QuestAmountRange(8, 128)
            QuestTargetRarity.UNCOMMON -> QuestAmountRange(32, 512)
            else -> QuestAmountRange(64, 2_000)
        }
        QuestAction.SMELT_ITEMS -> when (rarity) {
            QuestTargetRarity.PRECIOUS -> QuestAmountRange(8, 128)
            QuestTargetRarity.RARE -> QuestAmountRange(32, 512)
            else -> QuestAmountRange(64, 2_000)
        }
        QuestAction.FISH -> QuestAmountRange(25, 500)
        QuestAction.ENCHANT_ITEMS -> QuestAmountRange(8, 100)
        QuestAction.DEPOSIT_BANK -> QuestAmountRange(5_000, 100_000)
        QuestAction.WIN_WARS -> QuestAmountRange(1, 5)
    }
}
