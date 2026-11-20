package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-188: one webhook embed per newly committed prestige. */
class PrestigeDiscordPostTest {
    private val guild = UUID.randomUUID()
    private val completion = PrestigeCompletion(
        transactionId = UUID.randomUUID(), guildId = guild, actorId = UUID.randomUUID(), tier = 4,
        retainedRewardId = "bank-1", appliedAt = Instant.parse("2026-11-09T12:00:00Z"), firstToReachTier = true,
    )
    private val settings = PrestigeDiscordSettings(enabled = true, webhookUrl = "https://discord.test/hook", username = "Enthusia")
    private val text = PrestigeDiscordText(
        title = "{guild} reached Prestige {tier}",
        actor = "Prestiged by {player}",
        kept = "Kept: {reward}",
        first = "First guild to reach Prestige {tier}!",
        footer = "Enthusia SMP",
    )
    private val sent = mutableListOf<Pair<String, String>>()
    private fun post(s: PrestigeDiscordSettings = settings, send: (String, String) -> Unit = { u, b -> sent += u to b }) =
        PrestigeDiscordPost({ s }, { text }, { "Vibe" }, { "Fain" }, { "Bank Extension 1" }, send)

    @Test
    fun `posts one embed with the guild, tier, actor, reward and first-to-reach line`() {
        post().prestiged(completion)
        assertEquals(1, sent.size)
        val (url, body) = sent.single()
        assertEquals("https://discord.test/hook", url)
        assertTrue(""""title":"Vibe reached Prestige IV"""" in body, body)
        assertTrue("Prestiged by Fain" in body, body)
        assertTrue("Kept: Bank Extension 1" in body, body)
        assertTrue("First guild to reach Prestige IV!" in body, body)
        assertTrue(""""timestamp":"2026-11-09T12:00:00Z"""" in body, body)
        assertTrue(""""username":"Enthusia"""" in body, body)
    }

    @Test
    fun `no first-to-reach line for later guilds`() {
        post().prestiged(completion.copy(firstToReachTier = false))
        assertFalse("First guild" in sent.single().second)
    }

    @Test
    fun `nothing is sent when disabled or without a URL`() {
        post(settings.copy(enabled = false)).prestiged(completion)
        post(settings.copy(webhookUrl = " ")).prestiged(completion)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a failing request never escapes`() {
        post(send = { _, _ -> error("discord down") }).prestiged(completion)
    }

    @Test
    fun `names are escaped for JSON`() {
        PrestigeDiscordPost({ settings }, { text }, { "Quote\"Guild" }, { "Fain" }, { "Bank" }) { u, b -> sent += u to b }
            .prestiged(completion)
        assertTrue("""Quote\"Guild reached""" in sent.single().second, sent.single().second)
    }
}
