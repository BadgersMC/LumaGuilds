package net.lumalyte.lg.interaction.commands

import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Subcommand
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** REQ-189: every /g argument has a completion: values or a <hint>. */
class GuildCommandCompletionContractTest {
    @Test
    fun `every subcommand argument has a completion`() {
        val missing = GuildCommand::class.java.declaredMethods
            .filter { it.isAnnotationPresent(Subcommand::class.java) }
            .mapNotNull { method ->
                val arguments = method.parameterCount - 1 // the Player
                if (arguments == 0) return@mapNotNull null
                val tokens = method.getAnnotation(CommandCompletion::class.java)?.value
                    ?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }?.size ?: 0
                if (tokens >= arguments) null
                else "${method.getAnnotation(Subcommand::class.java).value} ($tokens of $arguments)"
            }
            .sorted()
        assertEquals(emptyList(), missing)
    }
}
