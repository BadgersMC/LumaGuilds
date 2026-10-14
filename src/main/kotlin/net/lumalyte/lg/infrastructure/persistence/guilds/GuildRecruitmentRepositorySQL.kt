package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildRecruitmentRepository
import net.lumalyte.lg.domain.recruitment.ApplicationStatus
import net.lumalyte.lg.domain.recruitment.GuildApplication
import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import net.lumalyte.lg.domain.recruitment.SubmissionDenial
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** Portable additive tables, with database applicant locks rather than per-server cache authority. */
internal class GuildRecruitmentRepositorySQL(private val storage: Storage<Database>) : GuildRecruitmentRepository {
    private val maria = storage.dialect == SqlDialect.MARIADB

    init {
        val engine = if (maria) " ENGINE=InnoDB" else ""
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_recruitment_applicants (player_id VARCHAR(36) PRIMARY KEY)$engine",
        )
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_recruitment_applications (
            id VARCHAR(36) PRIMARY KEY, guild_id VARCHAR(36) NOT NULL, applicant_id VARCHAR(36) NOT NULL,
            answers TEXT NOT NULL, created_at BIGINT NOT NULL, expires_at BIGINT NOT NULL,
            status VARCHAR(32) NOT NULL, reviewed_by VARCHAR(36) NULL, reviewed_at BIGINT NULL,
            decision_reason TEXT NOT NULL)$engine
            """.trimIndent(),
        )
        // Inline index syntax differs between SQLite and MariaDB; create only if absent via metadata.
        storage.connection.connection.use { connection ->
            for ((name, column) in listOf(
                "recruitment_applicant_idx" to "applicant_id",
                "recruitment_guild_idx" to "guild_id",
            )) {
                val exists =
                    connection.metaData.getIndexInfo(
                        connection.catalog,
                        null,
                        "guild_recruitment_applications",
                        false,
                        false,
                    ).use {
                            rows ->
                        var found = false
                        while (rows.next()) if (name.equals(rows.getString("INDEX_NAME"), true)) found = true
                        found
                    }
                if (!exists) {
                    connection.createStatement().use {
                        it.executeUpdate(
                            "CREATE INDEX $name ON guild_recruitment_applications ($column)",
                        )
                    }
                }
            }
        }
    }

    override fun submit(application: GuildApplication, policy: RecruitmentPolicy, at: Instant): SubmissionDenial? {
        require(
            application.status == ApplicationStatus.SUBMITTED &&
                application.reviewedBy == null && application.reviewedAt == null,
        )
        require(application.createdAt == at && application.expiresAt == at.plus(policy.applicationLifetime))
        return transaction { connection ->
            lockApplicant(connection, application.applicantId)
            val denial =
                policy.canSubmit(
                    application.guildId,
                    application.applicantId,
                    query(
                        connection,
                        "SELECT * FROM guild_recruitment_applications WHERE applicant_id = ?",
                        application.applicantId,
                    ),
                    at,
                )
            if (denial == null) {
                execute(
                    connection,
                    """
                    INSERT INTO guild_recruitment_applications
                    (id, guild_id, applicant_id, answers, created_at, expires_at,
                     status, reviewed_by, reviewed_at, decision_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    application.id, application.guildId, application.applicantId,
                    application.answers, application.createdAt.toEpochMilli(), application.expiresAt.toEpochMilli(),
                    application.status.name, null, null, "",
                )
            }
            denial
        }
    }

    override fun get(id: UUID): GuildApplication? =
        read(
            "SELECT * FROM guild_recruitment_applications WHERE id = ?",
            id,
        ).firstOrNull()

    override fun forApplicant(applicant: UUID): List<GuildApplication> =
        read(
            "SELECT * FROM guild_recruitment_applications WHERE applicant_id = ? ORDER BY created_at, id",
            applicant,
        )

    override fun forGuild(guild: UUID): List<GuildApplication> =
        read(
            "SELECT * FROM guild_recruitment_applications WHERE guild_id = ? ORDER BY created_at, id",
            guild,
        )

    override fun transition(expected: GuildApplication, updated: GuildApplication): Boolean {
        require(
            expected.id == updated.id && expected.guildId == updated.guildId &&
                expected.applicantId == updated.applicantId &&
                expected.createdAt == updated.createdAt && expected.answers == updated.answers,
        )
        require(expected.status != updated.status)
        return transaction { connection ->
            lockApplicant(connection, expected.applicantId)
            val current =
                query(
                    connection,
                    "SELECT * FROM guild_recruitment_applications WHERE id = ?",
                    expected.id,
                ).firstOrNull()
            if (current !=
                expected.copy(
                    createdAt = millis(expected.createdAt), expiresAt = millis(expected.expiresAt),
                    reviewedAt = expected.reviewedAt?.let(::millis),
                )
            ) {
                return@transaction false
            }
            if (updated.status == ApplicationStatus.ADMISSION_PENDING &&
                query(
                    connection, "SELECT * FROM guild_recruitment_applications WHERE applicant_id = ?",
                    expected.applicantId,
                )
                    .any { it.id != expected.id && it.status == ApplicationStatus.ADMISSION_PENDING }
            ) {
                return@transaction false
            }
            execute(
                connection,
                """
                UPDATE guild_recruitment_applications SET expires_at = ?, status = ?,
                reviewed_by = ?, reviewed_at = ?, decision_reason = ? WHERE id = ?
                """.trimIndent(),
                updated.expiresAt.toEpochMilli(), updated.status.name, updated.reviewedBy,
                updated.reviewedAt?.toEpochMilli(), updated.decisionReason, updated.id,
            ) == 1
        }
    }

    private fun read(sql: String, id: UUID): List<GuildApplication> =
        storage.connection.connection.use { query(it, sql, id) }

    private fun millis(at: Instant): Instant = Instant.ofEpochMilli(at.toEpochMilli())

    private fun query(connection: Connection, sql: String, id: UUID): List<GuildApplication> =
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, id.toString())
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(map(rows)) } }
        }

    private fun map(rows: ResultSet): GuildApplication {
        val reviewedAt = rows.getLong("reviewed_at").let { if (rows.wasNull()) null else Instant.ofEpochMilli(it) }
        return GuildApplication(
            UUID.fromString(rows.getString("id")), UUID.fromString(rows.getString("guild_id")),
            UUID.fromString(rows.getString("applicant_id")), rows.getString("answers"),
            Instant.ofEpochMilli(rows.getLong("created_at")), Instant.ofEpochMilli(rows.getLong("expires_at")),
            ApplicationStatus.valueOf(rows.getString("status")), rows.getString("reviewed_by")?.let(UUID::fromString),
            reviewedAt, rows.getString("decision_reason"),
        )
    }

    private fun lockApplicant(connection: Connection, player: UUID) {
        val suffix =
            if (maria) {
                "ON DUPLICATE KEY UPDATE player_id = VALUES(player_id)"
            } else {
                "ON CONFLICT(player_id) DO NOTHING"
            }
        execute(connection, "INSERT INTO guild_recruitment_applicants (player_id) VALUES (?) $suffix", player)
        execute(connection, "UPDATE guild_recruitment_applicants SET player_id = player_id WHERE player_id = ?", player)
    }

    private fun execute(connection: Connection, sql: String, vararg values: Any?): Int =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed {
                    index,
                    value,
                ->
                statement.setObject(index + 1, if (value is UUID) value.toString() else value)
            }
            statement.executeUpdate()
        }

    @Suppress(
        "TooGenericExceptionCaught",
    ) // Transaction boundary rolls back every failure and preserves rollback errors.
    private fun <T> transaction(operation: (Connection) -> T): T =
        storage.connection.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = operation(connection)
                connection.commit()
                result
            } catch (failure: Throwable) {
                try {
                    connection.rollback()
                } catch (rollback: Throwable) {
                    failure.addSuppressed(rollback)
                }
                // Never restore autoCommit after a failed rollback, which could commit partial writes.
                throw failure
            }
        }
}
