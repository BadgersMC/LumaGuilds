package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.RankClaimPermissionProfileRepository
import net.lumalyte.lg.infrastructure.persistence.migrations.RankClaimPermissionProfileSchema
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RankClaimPermissionProfileRepositorySQL(
    private val storage: Storage<Database>,
) : RankClaimPermissionProfileRepository {
    private val profiles = ConcurrentHashMap<UUID, String>()

    init {
        try {
            storage.connection.connection.use { connection ->
                RankClaimPermissionProfileSchema.create(
                    connection,
                    mariaDb = storage.dialect == SqlDialect.MARIADB,
                )
            }
            // Defensive cleanup for databases that may have briefly run an earlier
            // pre-FK development build of this table.
            storage.connection.executeUpdate(
                "DELETE FROM rank_claim_permission_profiles WHERE rank_id NOT IN (SELECT id FROM ranks)"
            )
            storage.connection.getResults(
                "SELECT rank_id, profile_name FROM rank_claim_permission_profiles"
            ).forEach { row ->
                profiles[UUID.fromString(row.getString("rank_id"))] = row.getString("profile_name")
            }
        } catch (error: SQLException) {
            throw DatabaseOperationException("Failed to initialize rank claim-permission profiles", error)
        }
    }

    override fun get(rankId: UUID): String? = profiles[rankId]

    @Synchronized
    override fun getOrCreate(rankId: UUID, legacyProfileName: String): String {
        profiles[rankId]?.let { return it }

        val profileName = legacyProfileName.trim()
        require(profileName.isNotEmpty()) { "Claim-permission profile name cannot be blank" }

        try {
            val inserted = storage.connection.executeUpdate(
                "INSERT INTO rank_claim_permission_profiles (rank_id, profile_name) VALUES (?, ?)",
                rankId.toString(),
                profileName,
            )
            check(inserted == 1) { "Failed to persist claim-permission profile for rank $rankId" }
        } catch (error: SQLException) {
            // Another process may have inserted the immutable rank-id mapping first.
            val existing = storage.connection.getFirstRow(
                "SELECT profile_name FROM rank_claim_permission_profiles WHERE rank_id = ?",
                rankId.toString(),
            )?.getString("profile_name")
            if (existing != null) {
                profiles[rankId] = existing
                return existing
            }
            throw DatabaseOperationException(
                "Failed to persist claim-permission profile for rank $rankId",
                error,
            )
        }

        profiles[rankId] = profileName
        return profileName
    }

    @Synchronized
    override fun remove(rankId: UUID): Boolean {
        return try {
            val changed = storage.connection.executeUpdate(
                "DELETE FROM rank_claim_permission_profiles WHERE rank_id = ?",
                rankId.toString(),
            )
            profiles.remove(rankId)
            changed > 0
        } catch (error: SQLException) {
            throw DatabaseOperationException(
                "Failed to remove claim-permission profile for rank $rankId",
                error,
            )
        }
    }
}
