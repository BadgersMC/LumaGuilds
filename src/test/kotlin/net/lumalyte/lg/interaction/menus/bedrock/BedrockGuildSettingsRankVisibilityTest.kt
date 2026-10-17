package net.lumalyte.lg.interaction.menus.bedrock

import kotlin.test.Test
import kotlin.test.assertEquals

class BedrockGuildSettingsRankVisibilityTest {
    @Test
    fun `unchanged stale form never restores an older visibility value`() {
        assertEquals(
            RankVisibilityUpdate.NONE,
            rankVisibilityUpdate(rendered = true, submitted = true, persisted = false),
        )
    }

    @Test
    fun `changed form writes only when persistence still matches rendered state`() {
        assertEquals(
            RankVisibilityUpdate.WRITE,
            rankVisibilityUpdate(rendered = true, submitted = false, persisted = true),
        )
        assertEquals(
            RankVisibilityUpdate.ALREADY_APPLIED,
            rankVisibilityUpdate(rendered = true, submitted = false, persisted = false),
        )
    }
}
