package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.chat.channel.ChannelMessageOptions

/** Copies the runtime record so RoseChat additions retain their original values. */
internal object RoseChatMessageOptions {
    private val components by lazy { ChannelMessageOptions::class.java.recordComponents }
    private val constructor by lazy {
        ChannelMessageOptions::class.java.getConstructor(*components.map { it.type }.toTypedArray())
    }

    fun withFormat(options: ChannelMessageOptions, format: String): ChannelMessageOptions {
        val values = components.map { component ->
            if (component.name == "format") format else component.accessor.invoke(options)
        }.toTypedArray()
        return constructor.newInstance(*values)
    }
}
