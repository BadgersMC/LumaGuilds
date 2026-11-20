package net.lumalyte.lg.interaction.commands

import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Subcommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** REQ-221: /pc is one entry point with tab completion for its words and the party name. */
internal class PartyChatCommandContractTest {
    @Test
    fun oneDefaultHandlerWithCompletion() {
        val methods = PartyChatCommand::class.java.declaredMethods
        assertTrue(methods.none { it.isAnnotationPresent(Subcommand::class.java) }, "words are parsed by PartyChatIntent")
        val default = methods.single { it.isAnnotationPresent(Default::class.java) }
        assertEquals("on|off|switch|mute|help @guildparty", default.getAnnotation(CommandCompletion::class.java).value)
        val main = File("src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt").readText()
        assertTrue(main.contains("registerAsyncCompletion(\"guildparty\")"))
    }
}
