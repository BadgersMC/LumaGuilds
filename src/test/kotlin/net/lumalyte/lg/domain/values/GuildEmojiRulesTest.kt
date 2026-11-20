package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REQ-216 to REQ-218: which emojis a guild may pick. */
internal class GuildEmojiRulesTest {
    private val rules = GuildEmojiRules(
        allowed = listOf("sheesh", "skull", "vibe_logo"),
        guildOnly = mapOf("vibe" to "vibe_logo", "belovedones" to "belovedones_logo"),
    )

    @Test
    fun everyGuildGetsTheApprovedListAndNothingElse() {
        assertEquals(listOf("sheesh", "skull"), rules.choices("WeOnTop"))
        assertFalse(rules.canPick("donor_head_cabdb4e5", "WeOnTop", current = null))
        assertTrue(rules.canPick("skull", "WeOnTop", current = null))
    }

    @Test
    fun aGuildOnlyLogoGoesOnlyToItsGuildAndComesFirst() {
        assertEquals(listOf("vibe_logo", "sheesh", "skull"), rules.choices("Vibe"))
        assertEquals(listOf("belovedones_logo", "sheesh", "skull"), rules.choices(" belovedones "))
        assertFalse(rules.canPick("vibe_logo", "BelovedOnes", current = null), "even though it's on the approved list")
        assertTrue(rules.canPick("vibe_logo", "VIBE", current = null))
        assertEquals(listOf("sheesh", "skull"), rules.choices(null))
    }

    @Test
    fun aGuildKeepsAnEmojiThatIsNoLongerOffered() {
        assertTrue(rules.canPick("donor_head_cabdb4e5", "WeOnTop", current = "donor_head_cabdb4e5"))
        assertFalse(rules.canPick("donor_head_other", "WeOnTop", current = "donor_head_cabdb4e5"))
    }

    @Test
    fun theDefaultListIsTheChatEmojisWithoutHeadsOrLogos() {
        val defaults = GuildEmojiRules()
        assertEquals(34, defaults.allowed.size)
        assertTrue("sheesh" in defaults.allowed && "skull" in defaults.allowed)
        assertTrue(defaults.allowed.none { it.startsWith("donor_head_") || it.endsWith("_logo") })
    }
}
