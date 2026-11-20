package net.lumalyte.lg.interaction.commands

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REQ-189: every /lumaguilds argument suggests values or a hint. */
class LumaGuildsCompletionsTest {
    private val granted = mutableSetOf("op", "lumaguilds.admin.xp", "lumaguilds.admin.prestige", "lumaguilds.admin.owner.recover", "bellclaims.admin")
    private val completions = LumaGuildsCompletions(
        allowed = { it in granted },
        guildNames = { listOf("Vibe", "WeOnTop") },
        prestige = { guild ->
            if (guild == "Vibe") LumaGuildsCompletions.PrestigeFacts(count = 3, rewardChoices = listOf("bank-1", "fee-2"), tiers = listOf(1, 2, 3))
            else null
        },
    )
    private fun complete(vararg args: String) = completions.complete(args.toList())

    @Test
    fun `first argument lists the subcommands the sender may use`() {
        assertTrue(listOf("prestige", "xp", "owner", "chapter").all { it in complete("") })
        granted.clear()
        assertEquals(listOf("help"), complete(""))
    }

    @Test
    fun `prestige walks through subcommand, guild, value and confirm`() {
        assertEquals(listOf("info", "set", "grant", "rerun", "preview"), complete("prestige", ""))
        assertEquals(listOf("Vibe", "WeOnTop"), complete("prestige", "set", ""))
        assertEquals(listOf("0", "1", "2"), complete("prestige", "set", "Vibe", ""))
        assertEquals(listOf("confirm"), complete("prestige", "set", "Vibe", "1", ""))
        assertEquals(listOf("bank-1", "fee-2"), complete("prestige", "grant", "Vibe", ""))
        assertEquals(listOf("confirm"), complete("prestige", "grant", "Vibe", "bank-1", ""))
        assertEquals(listOf("1", "2", "3", "confirm"), complete("prestige", "rerun", "Vibe", ""))
        assertEquals(emptyList(), complete("prestige", "info", "Vibe", ""))
    }

    @Test
    fun `free values get a hint that disappears once typing starts`() {
        assertEquals(listOf("<count>"), complete("prestige", "set", "Unknown", ""))
        assertEquals(listOf("<amount>"), complete("xp", "give", "Vibe", ""))
        assertEquals(emptyList(), complete("xp", "give", "Vibe", "5"))
        assertEquals(listOf("<chapter-id>"), complete("chapter", "status", ""))
        assertEquals(listOf("<ISO-8601-time>"), complete("chapter", "postpone", "c1", ""))
        assertEquals(listOf("<guild-uuid>"), complete("owner", "recover", ""))
    }

    @Test
    fun `typed text filters suggestions case-insensitively`() {
        assertEquals(listOf("WeOnTop"), complete("prestige", "info", "we"))
        assertEquals(listOf("grant"), complete("prestige", "GR"))
    }

    @Test
    fun `no permission, no suggestions`() {
        granted.remove("lumaguilds.admin.prestige")
        assertEquals(emptyList(), complete("prestige", ""))
    }
}
