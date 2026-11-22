package net.lumalyte.lg.architecture

import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.di.runtime.GuildRuntimeLifecycle
import net.lumalyte.lg.di.runtime.PaperGuildRuntime
import org.junit.jupiter.api.Test
import java.lang.classfile.ClassFile
import java.lang.classfile.instruction.InvokeInstruction
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verify first-start ordering through the compiled production entry point and its lifecycle owner. */
class PlaceholderStartupOrderingTest {
    private fun calls(type: Class<*>, method: String): List<String> {
        val bytes = type.getResourceAsStream("${type.simpleName}.class")!!.use { it.readBytes() }
        val implementation = ClassFile.of().parse(bytes).methods().single {
            it.methodName().stringValue() == method && !it.flags().has(java.lang.reflect.AccessFlag.BRIDGE)
        }
        return implementation.code().orElseThrow().filterIsInstance<InvokeInstruction>()
            .map { it.name().stringValue() }
    }

    @Test
    fun `first startup registers placeholders only after activating the dependency graph`() {
        val entry = calls(LumaGuilds::class.java, "onEnable")
        assertEquals(1, entry.count { it == "start" })
        val start = calls(GuildRuntimeLifecycle::class.java, "start")
        assertEquals(1, start.count { it == "activate" })
        assertEquals(1, start.count { it == "registerIntegrations" })
        assertTrue(start.indexOf("activate") < start.indexOf("registerIntegrations"))
        assertEquals(1, calls(PaperGuildRuntime::class.java, "registerIntegrations")
            .count { it == "registerPlaceholderExpansion" })
    }
}
