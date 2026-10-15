package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.GuildApplication
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

internal fun GuildApplication.persistedTimestamps(): GuildApplication {
    return copy(
        createdAt = Instant.ofEpochMilli(createdAt.toEpochMilli()),
        expiresAt = Instant.ofEpochMilli(expiresAt.toEpochMilli()),
        reviewedAt = reviewedAt?.let { Instant.ofEpochMilli(it.toEpochMilli()) },
    )
}

internal fun mapRecruitmentApplication(rows: ResultSet): GuildApplication {
    val reviewedAt = rows.getLong("reviewed_at").let { if (rows.wasNull()) null else Instant.ofEpochMilli(it) }
    return GuildApplication(
        UUID.fromString(rows.getString("id")), UUID.fromString(rows.getString("guild_id")),
        UUID.fromString(rows.getString("applicant_id")), rows.getString("answers"),
        Instant.ofEpochMilli(rows.getLong("created_at")), Instant.ofEpochMilli(rows.getLong("expires_at")),
        ApplicationStatus.valueOf(rows.getString("status")), rows.getString("reviewed_by")?.let(UUID::fromString),
        reviewedAt, rows.getString("decision_reason"),
    )
}
