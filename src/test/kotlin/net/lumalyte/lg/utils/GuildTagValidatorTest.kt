package net.lumalyte.lg.utils

import net.lumalyte.lg.config.NameFilterConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GuildTagValidatorTest {

    @Test
    fun `interactive tag returns typed validation failure`() {
        val result = GuildTagValidator.validationFailure(
            "<click:run_command:'/op me'>Click",
            NameFilterConfig(enabled = false),
        )

        assertEquals(GuildTagValidator.Failure.InteractiveTag("click"), result)
    }

    @Test
    fun `blocked visible content returns typed validation failure`() {
        val result = GuildTagValidator.validationFailure(
            "blocked",
            NameFilterConfig(enabled = true, blockedPatterns = listOf("blocked")),
        )

        assertEquals(GuildTagValidator.Failure.InappropriateContent, result)
    }
}
