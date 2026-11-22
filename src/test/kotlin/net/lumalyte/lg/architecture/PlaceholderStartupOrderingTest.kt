package net.lumalyte.lg.architecture

import net.lumalyte.lg.LumaGuilds
import org.junit.jupiter.api.Test
import java.lang.classfile.ClassFile
import java.lang.classfile.instruction.InvokeInstruction
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-2/3: optional integration factories must not run before their container exists.
 * Checks the compiled entry point without booting a server or relying on a container from a prior enable.
 * Real Paper/PlaceholderAPI registration remains a separate integration acceptance gate.
 */
class PlaceholderStartupOrderingTest {
    @Test
    fun `first startup registers placeholders only after starting its dependency container`() {
        val bytes = checkNotNull(LumaGuilds::class.java.getResourceAsStream("LumaGuilds.class")) {
            "Compiled LumaGuilds entry point must be available"
        }.use { it.readBytes() }
        val startup = ClassFile.of().parse(bytes).methods().single { it.methodName().stringValue() == "onEnable" }
        val calls = startup.code().orElseThrow().filterIsInstance<InvokeInstruction>()
            .map { it.name().stringValue() }
        val containerStarted = calls.indexOf("startKoin")
        val placeholdersRegistered = calls.indexOf("initialisePlaceholderAPI")

        assertTrue(containerStarted >= 0, "The active container must be started by the entry point")
        assertEquals(1, calls.count { it == "initialisePlaceholderAPI" }, "Register the optional expansion once")
        assertTrue(placeholdersRegistered > containerStarted,
            "First-start PlaceholderAPI registration resolves dependencies and must follow container startup")
    }
}
