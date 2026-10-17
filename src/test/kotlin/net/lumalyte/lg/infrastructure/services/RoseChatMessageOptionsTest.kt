package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.chat.channel.ChannelMessageOptions
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

/** Runs against both the original and extended RoseChat runtime records. */
internal class RoseChatMessageOptionsTest {
    /** A format update must preserve every other field, including new bypass flags. */
    @Test
    @Suppress("SpreadOperator") // The regression exercises the runtime canonical constructor.
    fun preserveRuntimeOptions() {
        val components = ChannelMessageOptions::class.java.recordComponents
        val values =
            components.map { component ->
                when (component.type) {
                    Boolean::class.javaPrimitiveType -> true
                    String::class.java -> "original-${component.name}"
                    UUID::class.java -> UUID.randomUUID()
                    else -> null
                }
            }.toTypedArray<Any?>()
        val original =
            ChannelMessageOptions::class.java
                .getConstructor(*components.map { it.type }.toTypedArray())
                .newInstance(*values)
        val changed = RoseChatMessageOptions.withFormat(original, "new-format")
        components.forEach { component ->
            assertEquals(
                if (component.name == "format") "new-format" else component.accessor.invoke(original),
                component.accessor.invoke(changed),
                component.name,
            )
        }
        assertEquals("original-format", original.format())
    }
}
