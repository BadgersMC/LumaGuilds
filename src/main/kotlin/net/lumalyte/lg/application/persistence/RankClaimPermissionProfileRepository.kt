package net.lumalyte.lg.application.persistence

import java.util.UUID

/**
 * Stable claim-permission profile identity for a guild rank.
 *
 * The profile key intentionally survives display-name changes. Existing
 * deployments seed it from the legacy name-keyed configuration once, then the
 * rank UUID owns that association.
 */
interface RankClaimPermissionProfileRepository {
    fun get(rankId: UUID): String?
    fun getOrCreate(rankId: UUID, legacyProfileName: String): String
    fun remove(rankId: UUID): Boolean
}
