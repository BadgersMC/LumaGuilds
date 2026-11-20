package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.HallPedestal

/** REQ-275: the Hall of Prestige statue spots staff have placed. */
interface HallPedestalRepository {
    fun all(): List<HallPedestal>

    /** Places or moves the pedestal for its slot. */
    fun set(pedestal: HallPedestal)

    /** True if a pedestal was removed. */
    fun remove(slot: Int): Boolean
}
