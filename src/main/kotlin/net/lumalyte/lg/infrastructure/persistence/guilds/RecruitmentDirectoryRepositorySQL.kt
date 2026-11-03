package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val DISCORD_INVITE_KEY = "discord"

/** Additive listing and preference tables; SQL failures propagate instead of reporting a successful save. */
internal class RecruitmentDirectoryRepositorySQL(
    private val storage: Storage<Database>,
    private val guilds: net.lumalyte.lg.application.persistence.GuildRepository? = null,
) : RecruitmentDirectoryRepository {
    private val maria = storage.dialect == SqlDialect.MARIADB
    private val access by lazy { GuildRecruitmentAccessSQL(storage) }

    init {
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_quest_reminder_receipts (" +
                "player_id VARCHAR(36) NOT NULL, guild_id VARCHAR(36) NOT NULL, week_id VARCHAR(64) NOT NULL, " +
                "PRIMARY KEY (player_id, guild_id, week_id))",
        )
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_recruitment_profiles (guild_id VARCHAR(36) PRIMARY " +
                "KEY, profile TEXT NOT NULL)",
        )
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_recruitment_preferences " +
                "(player_id VARCHAR(36) PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 1, prompted_at BIGINT NULL)",
        )
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_recruitment_notifications " +
                "(player_id VARCHAR(36) PRIMARY KEY, signature VARCHAR(64) NOT NULL)",
        )
    }

    override fun consumeQuestReminder(
        player: UUID,
        guild: UUID,
        week: String,
    ): Boolean {
        require(week.isNotBlank() && week.length <= 64)
        val sql =
            if (maria) {
                "INSERT IGNORE INTO guild_quest_reminder_receipts (player_id, guild_id, week_id) VALUES (?, ?, ?)"
            } else {
                "INSERT INTO guild_quest_reminder_receipts (player_id, guild_id, week_id) VALUES (?, ?, ?) " +
                    "ON CONFLICT(player_id, guild_id, week_id) DO NOTHING"
            }
        return storage.connection.executeUpdate(sql, player.toString(), guild.toString(), week) == 1
    }

    /** Reads plain profile fields; JSON content never authorizes commands or rich links. */
    override fun profile(guild: UUID): RecruitmentProfile? {
        val row =
            storage.connection.getFirstRow(
                "SELECT profile FROM guild_recruitment_profiles WHERE guild_id = ?", guild.toString(),
            ) ?: return null
        val json = JSONObject(row.getString("profile"))
        val interests = json.getJSONArray("interests")
        return RecruitmentProfile(
            guildId = guild,
            mode = RecruitmentMode.valueOf(json.getString("mode")),
            pitch = json.getString("pitch"),
            requirements = json.getString("requirements"),
            interests = (0 until interests.length()).map { interests.getString(it) }.toSet(),
            preferredTimes = json.getString("times"),
            language = json.getString("language"),
            beginnerFriendly = json.getBoolean("beginner"),
            discordInvite = if (json.isNull(DISCORD_INVITE_KEY)) null else json.getString(DISCORD_INVITE_KEY),
            discordInstructions = json.getString("instructions"),
            publicListing = json.optBoolean("publicListing", true),
            joinAnnouncements = json.optBoolean("joinAnnouncements", true),
            showBanner = json.optBoolean("showBanner", true),
            questReminders = json.optBoolean("questReminders", false),
        )
    }

    /** Stores only recruitment fields and preserves unrelated guild settings. */
    override fun save(profile: RecruitmentProfile) {
        val json = profileJson(profile)
        access.save(profile.guildId, profile.mode, json.toString())
        guilds?.refreshCachedAccess(profile.guildId)
    }

    override fun savePresentation(
        profile: RecruitmentProfile,
        fields: Set<String>,
    ) {
        access.savePresentation(profile.guildId, profileJson(profile).toString(), fields)
    }

    private fun profileJson(profile: RecruitmentProfile): JSONObject =
        JSONObject()
            .put("updatedAt", Instant.now().toString())
            .put("mode", profile.mode.name)
            .put("pitch", profile.pitch)
            .put("requirements", profile.requirements)
            .put("interests", JSONArray(profile.interests.toList()))
            .put("times", profile.preferredTimes)
            .put("language", profile.language)
            .put("beginner", profile.beginnerFriendly)
            .put(
                DISCORD_INVITE_KEY,
                profile.discordInvite ?: JSONObject.NULL,
            )
            .put("instructions", profile.discordInstructions)
            .put("publicListing", profile.publicListing)
            .put("joinAnnouncements", profile.joinAnnouncements)
            .put("showBanner", profile.showBanner)
            .put("questReminders", profile.questReminders)

    override fun updatedAt(guild: UUID): Instant? {
        val row =
            storage.connection.getFirstRow(
                "SELECT profile FROM guild_recruitment_profiles WHERE guild_id = ?", guild.toString(),
            ) ?: return null
        val value = JSONObject(row.getString("profile")).optString("updatedAt", "")
        return value.takeIf(String::isNotBlank)?.let(Instant::parse)
    }

    /** Enables or disables personal discovery prompts without resetting prompt history. */
    override fun setReminders(
        player: UUID,
        enabled: Boolean,
    ) {
        val suffix =
            if (maria) {
                "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)"
            } else {
                "ON CONFLICT(player_id) DO UPDATE SET enabled = excluded.enabled"
            }
        storage.connection.executeUpdate(
            "INSERT INTO guild_recruitment_preferences (player_id, enabled) VALUES (?, ?) $suffix",
            player.toString(),
            if (enabled) 1 else 0,
        )
    }

    /** New players can receive the one-time prompt; optional repeating prompts are globally disabled by default. */
    override fun remindersEnabled(player: UUID): Boolean {
        return storage.connection.getFirstRow(
            "SELECT enabled FROM guild_recruitment_preferences WHERE player_id = ?", player.toString(),
        )?.getInt("enabled") != 0
    }

    /** One conditional update makes reconnects and competing schedulers unable to replay a prompt. */
    override fun consumePrompt(
        player: UUID,
        at: Instant,
        repeatAfter: Duration?,
    ): Boolean {
        require(repeatAfter == null || repeatAfter > Duration.ZERO)
        insertPromptPreference(player)
        val due = if (repeatAfter == null) "prompted_at IS NULL" else "(prompted_at IS NULL OR prompted_at <= ?)"
        val query =
            "UPDATE guild_recruitment_preferences SET prompted_at = ? " +
                "WHERE player_id = ? AND enabled = 1 AND $due"
        return if (repeatAfter == null) {
            storage.connection.executeUpdate(query, at.toEpochMilli(), player.toString()) == 1
        } else {
            storage.connection.executeUpdate(
                query, at.toEpochMilli(), player.toString(), at.minus(repeatAfter).toEpochMilli(),
            ) == 1
        }
    }

    private fun insertPromptPreference(player: UUID) {
        val suffix =
            if (maria) {
                "ON DUPLICATE KEY UPDATE player_id = VALUES(player_id)"
            } else {
                "ON CONFLICT(player_id) DO NOTHING"
            }
        storage.connection.executeUpdate(
            "INSERT INTO guild_recruitment_preferences (player_id) VALUES (?) $suffix",
            player.toString(),
        )
    }

    /** A digest is delivered once until its private application or inbox state changes. */
    override fun consumeNotification(
        player: UUID,
        signature: String,
    ): Boolean {
        require(signature.matches(Regex("[0-9a-f]{64}")))
        val suffix =
            if (maria) {
                "ON DUPLICATE KEY UPDATE player_id = VALUES(player_id)"
            } else {
                "ON CONFLICT(player_id) DO NOTHING"
            }
        storage.connection.executeUpdate(
            "INSERT INTO guild_recruitment_notifications (player_id, signature) VALUES (?, '') $suffix",
            player.toString(),
        )
        return storage.connection.executeUpdate(
            "UPDATE guild_recruitment_notifications SET signature = ? WHERE player_id = ? AND signature <> ?",
            signature, player.toString(), signature,
        ) == 1
    }
}
