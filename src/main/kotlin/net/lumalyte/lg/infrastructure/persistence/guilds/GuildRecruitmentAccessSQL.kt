package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.json.JSONObject
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/** The legacy toggle and published listing share one database commit. */
internal class GuildRecruitmentAccessSQL(private val storage: Storage<Database>) {
    init {
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_recruitment_profiles " +
                "(guild_id VARCHAR(36) PRIMARY KEY, profile TEXT NOT NULL)",
        )
    }

    /** Unrelated settings writes must not restore an old screen's access flag. */
    fun publishedAccess(guild: UUID): Boolean? {
        val row =
            storage.connection.getFirstRow(
                "SELECT profile FROM guild_recruitment_profiles WHERE guild_id = ?", guild.toString(),
            ) ?: return null
        return JSONObject(row.getString("profile")).getString("mode") == RecruitmentMode.JOIN_IMMEDIATELY.name
    }

    fun save(
        guild: UUID,
        mode: RecruitmentMode,
        profile: String,
    ) {
        storage.recruitmentTransaction { connection ->
            check(exists(connection, guild)) { "This guild is no longer available" }
            writeAccess(connection, guild, mode == RecruitmentMode.JOIN_IMMEDIATELY)
            val merged = JSONObject(profile)
            readProfile(connection, guild)?.let { current ->
                PRESENTATION_KEYS.forEach { key -> if (current.has(key)) merged.put(key, current.getBoolean(key)) }
            }
            val statement = profileStatement(connection)
            statement.use {
                it.setString(1, guild.toString())
                it.setString(2, merged.toString())
                it.executeUpdate()
            }
        }
    }

    fun savePresentation(
        guild: UUID,
        fallback: String,
        fields: Set<String>,
    ) {
        require(fields.all { it in PRESENTATION_KEYS })
        storage.recruitmentTransaction { connection ->
            check(exists(connection, guild)) { "This guild is no longer available" }
            val proposed = JSONObject(fallback)
            val current = readProfile(connection, guild) ?: proposed
            fields.forEach { current.put(it, proposed.getBoolean(it)) }
            profileStatement(connection).use {
                it.setString(1, guild.toString())
                it.setString(2, current.toString())
                it.executeUpdate()
            }
        }
    }

    private fun readProfile(
        connection: Connection,
        guild: UUID,
    ): JSONObject? =
        connection.prepareStatement("SELECT profile FROM guild_recruitment_profiles WHERE guild_id = ?").use {
            it.setString(1, guild.toString())
            it.executeQuery().use { rows -> if (rows.next()) JSONObject(rows.getString(1)) else null }
        }

    private companion object {
        val PRESENTATION_KEYS = listOf("publicListing", "joinAnnouncements", "showBanner", "questReminders")
    }

    private fun profileStatement(connection: Connection): java.sql.PreparedStatement {
        return if (storage.dialect == SqlDialect.MARIADB) {
            connection.prepareStatement(
                "INSERT INTO guild_recruitment_profiles (guild_id, profile) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE profile = VALUES(profile)",
            )
        } else {
            connection.prepareStatement(
                "INSERT INTO guild_recruitment_profiles (guild_id, profile) VALUES (?, ?) " +
                    "ON CONFLICT(guild_id) DO UPDATE SET profile = excluded.profile",
            )
        }
    }

    fun setOpen(
        guild: UUID,
        open: Boolean,
    ): Boolean {
        return storage.recruitmentTransaction { connection ->
            if (exists(connection, guild)) {
                writeAccess(connection, guild, open)
                updateMethod(connection, guild, open)
                true
            } else {
                false
            }
        }
    }

    private fun updateMethod(
        connection: Connection,
        guild: UUID,
        open: Boolean,
    ) {
        val profile =
            connection.prepareStatement(
                "SELECT profile FROM guild_recruitment_profiles WHERE guild_id = ?",
            ).use { statement ->
                statement.setString(1, guild.toString())
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        if (profile != null) {
            val json =
                JSONObject(profile)
                    .put("mode", if (open) RecruitmentMode.JOIN_IMMEDIATELY.name else RecruitmentMode.CLOSED.name)
                    .put("updatedAt", Instant.now().toString())
            connection.prepareStatement(
                "UPDATE guild_recruitment_profiles SET profile = ? WHERE guild_id = ?",
            ).use { statement ->
                statement.setString(1, json.toString())
                statement.setString(2, guild.toString())
                statement.executeUpdate()
            }
        }
    }

    private fun exists(
        connection: Connection,
        guild: UUID,
    ): Boolean {
        val statement =
            if (storage.dialect == SqlDialect.MARIADB) {
                connection.prepareStatement("SELECT id FROM guilds WHERE id = ? FOR UPDATE")
            } else {
                connection.prepareStatement("SELECT id FROM guilds WHERE id = ?")
            }
        return statement.use {
            it.setString(1, guild.toString())
            it.executeQuery().use { rows -> rows.next() }
        }
    }

    private fun writeAccess(
        connection: Connection,
        guild: UUID,
        open: Boolean,
    ) {
        connection.prepareStatement("UPDATE guilds SET is_open = ? WHERE id = ?").use {
            it.setInt(1, if (open) 1 else 0)
            it.setString(2, guild.toString())
            it.executeUpdate()
        }
    }
}
