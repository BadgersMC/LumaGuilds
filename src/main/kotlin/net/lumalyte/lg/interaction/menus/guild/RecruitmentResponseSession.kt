package net.lumalyte.lg.interaction.menus.guild

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Each native form response belongs to one navigation state and can be consumed only once. */
internal class RecruitmentResponseSession(private val navigationGuard: () -> (() -> Boolean)) {
    private val generation = AtomicLong()

    fun begin(): () -> Boolean {
        val frame = generation.incrementAndGet()
        val navigation = navigationGuard()
        val consumed = AtomicBoolean()
        return { generation.get() == frame && navigation() && consumed.compareAndSet(false, true) }
    }
}
