package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.rewards.PrestigeCompletion

/** Receives each newly committed prestige once; failures never affect settlement (REQ-153). */
fun interface GuildPrestigeNotifier {
    fun prestiged(completion: PrestigeCompletion)
}
