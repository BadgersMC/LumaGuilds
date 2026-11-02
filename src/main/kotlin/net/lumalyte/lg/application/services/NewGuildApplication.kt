package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.recruitment.GuildApplication
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal fun newGuildApplication(
    player: UUID,
    guild: UUID,
    answers: String,
    at: Instant,
    lifetime: Duration,
): GuildApplication = GuildApplication(UUID.randomUUID(), guild, player, answers.trim(), at, at.plus(lifetime))
