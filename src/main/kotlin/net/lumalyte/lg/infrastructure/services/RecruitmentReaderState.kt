package net.lumalyte.lg.infrastructure.services

/** Server-thread page ownership prevents replaced forms and repeated callbacks from reopening menus. */
internal class RecruitmentReaderState(private val valid: () -> Boolean) {
    private var revision = 0
    private var consumed = false
    private var closed = false

    fun beginPage(): Int {
        consumed = false
        return ++revision
    }

    fun consume(page: Int): Boolean {
        if (closed || consumed || page != revision) return false
        if (!valid()) return false
        consumed = true
        return true
    }

    fun close() {
        closed = true
    }
}
