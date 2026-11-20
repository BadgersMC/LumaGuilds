package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestTargetRarity
import net.lumalyte.lg.domain.values.QuestAction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-176: crafting amounts a guild can make in a week (production asked for 210 diamond hoes). */
class QuestAmountPolicyCraftTest {
    @Test
    fun `stackable crafts ask for 64 to 800`() {
        assertEquals(QuestAmountRange(64, 800), QuestAmountPolicy.range(QuestAction.CRAFT_ITEMS, QuestTargetRarity.COMMON))
    }

    @Test
    fun `tools and armor ask for 8 to 64`() {
        assertEquals(QuestAmountRange(8, 64), QuestAmountPolicy.range(QuestAction.CRAFT_ITEMS, QuestTargetRarity.UNCOMMON))
    }
}
