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
        if (actor != owner || unavailable(page)) return false
        consumed = true
        return withinLifetime(now) && valid()
    }

    private fun unavailable(page: Int): Boolean = closed || consumed || page != revision

    private fun withinLifetime(now: Long): Boolean = now >= openedAt && now - openedAt < lifetimeMillis

    fun close() {
        closed = true
    }

    /** Closing never authorizes a business action and remains usable after page expiry. */
    fun dismiss(actor: UUID, page: Int): Boolean {
        if (actor != owner || closed || page != revision || !valid()) return false
        closed = true
        return true
    }
}
