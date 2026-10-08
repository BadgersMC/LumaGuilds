package net.lumalyte.lg.application.persistence

import java.util.UUID

/** Guild-wide reconnect preference; missing entries are disabled. */
internal interface GuildChatReconnectSettingsRepository {
    /** Returns the current durable preference. */
    fun resetOnJoin(guildId: UUID): Boolean

    /** Atomically changes the preference only when [expected] still matches. */
    fun compareAndSet(guildId: UUID, expected: Boolean, enabled: Boolean): Boolean
}
