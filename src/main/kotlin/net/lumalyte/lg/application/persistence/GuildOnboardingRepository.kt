package net.lumalyte.lg.application.persistence

import java.util.UUID

interface GuildOnboardingRepository {
    fun register(player: UUID, guild: UUID): Boolean
    fun consume(player: UUID, guild: UUID): Boolean
    fun dismiss(player: UUID, guild: UUID): Boolean
}
