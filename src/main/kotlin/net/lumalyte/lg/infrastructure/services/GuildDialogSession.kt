package net.lumalyte.lg.infrastructure.services

import java.util.UUID

/** Server-thread ownership for one presentation; invalidity terminalizes the page. */
internal class GuildDialogSession(
    private val owner: UUID,
    private val lifetimeMillis: Long,
    private val valid: () -> Boolean,
) {
    private var revision = 0
    private var openedAt = 0L
    private var consumed = true
    private var closed = false

    fun beginPage(now: Long): Int {
        openedAt = now
        consumed = false
        return ++revision
    }

    fun consume(actor: UUID, page: Int, now: Long): Boolean {
        if (closed || consumed || actor != owner || page != revision) return false
        consumed = true
        return now >= openedAt && now - openedAt < lifetimeMillis && valid()
    }

    fun close() {
        closed = true
    }
}
