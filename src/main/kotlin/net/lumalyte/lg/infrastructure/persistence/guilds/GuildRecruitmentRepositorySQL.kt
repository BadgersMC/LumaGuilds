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
import java.time.Instant
import java.util.UUID

private enum class RecruitmentSelect { ID, APPLICANT, APPLICANT_ORDERED, GUILD_ORDERED }

private fun Connection.recruitmentStatement(query: RecruitmentSelect): java.sql.PreparedStatement {
    return when (query) {
        RecruitmentSelect.ID -> prepareStatement("SELECT * FROM guild_recruitment_applications WHERE id = ?")
        RecruitmentSelect.APPLICANT ->
            prepareStatement(
                "SELECT * FROM guild_recruitment_applications WHERE applicant_id = ?",
            )
        RecruitmentSelect.APPLICANT_ORDERED ->
            prepareStatement(
                "SELECT * FROM guild_recruitment_applications WHERE applicant_id = ? ORDER BY created_at, id",
            )
        RecruitmentSelect.GUILD_ORDERED ->
            prepareStatement(
                "SELECT * FROM guild_recruitment_applications WHERE guild_id = ? ORDER BY created_at, id",
            )
    }
}

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
            """
                .trimIndent(),
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
                    )
                        .use {
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
        return storage.recruitmentTransaction { connection ->
            lockApplicant(connection, application.applicantId)
            val denial =
                policy.canSubmit(
                    application.guildId,
                    application.applicantId,
                    query(
                        connection,
                        RecruitmentSelect.APPLICANT,
                        application.applicantId,
                    ),
                    at,
                )
            if (denial == null) {
                insertApplication(connection, application)
            }
            denial
        }
    }

    private fun insertApplication(connection: Connection, application: GuildApplication) {
        executeRecruitmentWrite(
            connection.prepareStatement(
                "INSERT INTO guild_recruitment_applications " +
                    "(id, guild_id, applicant_id, answers, created_at, expires_at, " +
                    "status, reviewed_by, reviewed_at, decision_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ),
            application.id, application.guildId, application.applicantId,
            application.answers, application.createdAt.toEpochMilli(), application.expiresAt.toEpochMilli(),
            application.status.name, null, null, "",
        )
    }

    override fun get(id: UUID): GuildApplication? {
        return read(
            RecruitmentSelect.ID,
            id,
        )
            .firstOrNull()
    }

    override fun forApplicant(applicant: UUID): List<GuildApplication> {
        return read(
            RecruitmentSelect.APPLICANT_ORDERED,
            applicant,
        )
    }

    override fun forGuild(guild: UUID): List<GuildApplication> {
        return read(
            RecruitmentSelect.GUILD_ORDERED,
            guild,
        )
    }

    override fun transition(expected: GuildApplication, updated: GuildApplication): Boolean {
        require(
            expected.id == updated.id && expected.guildId == updated.guildId &&
                expected.applicantId == updated.applicantId &&
                expected.createdAt == updated.createdAt && expected.answers == updated.answers,
        )
        require(expected.status != updated.status)
        return storage.recruitmentTransaction { connection ->
            lockApplicant(connection, expected.applicantId)
            val current = query(connection, RecruitmentSelect.ID, expected.id).firstOrNull()
            current == expected.persistedTimestamps() &&
                reservationAvailable(connection, expected, updated) && writeTransition(connection, updated) == 1
        }
    }

    private fun reservationAvailable(
        connection: Connection,
        expected: GuildApplication,
        updated: GuildApplication,
    ): Boolean {
        return updated.status != ApplicationStatus.ADMISSION_PENDING ||
            query(connection, RecruitmentSelect.APPLICANT, expected.applicantId)
                .none { it.id != expected.id && it.status == ApplicationStatus.ADMISSION_PENDING }
    }

    private fun writeTransition(connection: Connection, updated: GuildApplication): Int {
        return executeRecruitmentWrite(
            connection.prepareStatement(
                "UPDATE guild_recruitment_applications SET expires_at = ?, status = ?, " +
                    "reviewed_by = ?, reviewed_at = ?, decision_reason = ? WHERE id = ?",
            ),
            updated.expiresAt.toEpochMilli(),
            updated.status.name,
            updated.reviewedBy,
            updated.reviewedAt?.toEpochMilli(),
            updated.decisionReason,
            updated.id,
        )
    }

    private fun read(query: RecruitmentSelect, id: UUID): List<GuildApplication> =
        storage.connection.connection.use {
            query(it, query, id)
        }

    private fun query(connection: Connection, query: RecruitmentSelect, id: UUID): List<GuildApplication> {
        return connection.recruitmentStatement(query).use { statement ->
            statement.setString(1, id.toString())
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(
                        mapRecruitmentApplication(rows),
                    )
                }
            }
        }
    }

    private fun lockApplicant(connection: Connection, player: UUID) {
        val insert =
            if (maria) {
                connection.prepareStatement(
                    "INSERT INTO guild_recruitment_applicants (player_id) VALUES (?) " +
                        "ON DUPLICATE KEY UPDATE player_id = VALUES(player_id)",
                )
            } else {
                connection.prepareStatement(
                    "INSERT INTO guild_recruitment_applicants (player_id) VALUES (?) ON CONFLICT(player_id) DO NOTHING",
                )
            }
        executeRecruitmentWrite(insert, player)
        executeRecruitmentWrite(
            connection.prepareStatement(
                "UPDATE guild_recruitment_applicants SET player_id = player_id WHERE player_id = ?",
            ),
            player,
        )
    }
}
