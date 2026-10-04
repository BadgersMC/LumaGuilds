package net.lumalyte.lg.infrastructure.services

import dev.rosewood.rosechat.chat.channel.ChannelMessageOptions

/** Copies the runtime record so RoseChat additions retain their original values. */
internal object RoseChatMessageOptions {
    private val COMPONENTS by lazy { ChannelMessageOptions::class.java.recordComponents }
    private val CONSTRUCTOR by lazy {
        // Reflection needs the complete runtime parameter array to retain added options.
        @Suppress("SpreadOperator")
        ChannelMessageOptions::class.java.getConstructor(*COMPONENTS.map { it.type }.toTypedArray())
    }

    fun withFormat(options: ChannelMessageOptions, format: String): ChannelMessageOptions {
        val values =
            COMPONENTS.map { component ->
                if (component.name == "format") format else component.accessor.invoke(options)
            }.toTypedArray()
        // The variable-arity reflective constructor is intentional at this API boundary.
        @Suppress("SpreadOperator")
        return CONSTRUCTOR.newInstance(*values)
    }
}
