package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.chat.channel.ChannelMessageOptions
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class RoseChatMessageOptionsTest {
    @Test
    fun `format replacement preserves every runtime option including new bypass flags`() {
        val components = ChannelMessageOptions::class.java.recordComponents
        val values = components.map { component ->
            when (component.type) {
                Boolean::class.javaPrimitiveType -> true
                String::class.java -> "original-${component.name}"
                UUID::class.java -> UUID.randomUUID()
                else -> null
            }
        }.toTypedArray<Any?>()
        val original = ChannelMessageOptions::class.java
            .getConstructor(*components.map { it.type }.toTypedArray()).newInstance(*values)
        val changed = RoseChatMessageOptions.withFormat(original, "new-format")
        components.forEach { component ->
            assertEquals(
                if (component.name == "format") "new-format" else component.accessor.invoke(original),
                component.accessor.invoke(changed), component.name,
            )
        }
        assertEquals("original-format", original.format())
    }
}
