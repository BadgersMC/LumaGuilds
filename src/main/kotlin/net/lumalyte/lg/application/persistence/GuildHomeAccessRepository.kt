package net.lumalyte.lg.application.persistence

import java.util.UUID

/**
 * Saved-home coordinates and usable home entitlements are deliberately separate.
 * A missing guild policy means the Chapter 1 migration has NOT been audited yet:
 * existing locations remain untouched until a verified, per-guild reconciliation.
 */
enum class GuildHomeAccessState {
    UNRECONCILED,
    ACTIVE_BASE,
    ACTIVE_PRESTIGE,
    ACTIVE_OUTPOST,
    LOCKED_PREMATURE,
    REVIEW_REQUIRED;

    val accessible: Boolean get() = this == UNRECONCILED ||
        this == ACTIVE_BASE || this == ACTIVE_PRESTIGE || this == ACTIVE_OUTPOST
    val locked: Boolean get() = !accessible
}

data class VerifiedGuildHomeGrant(
    val state: GuildHomeAccessState,
    val sourceKey: String?,
)

/** Read-only preview: backup refs are candidates, not proof of individual home provenance. */
data class GuildHomeAuditPreview(
    val guildId: UUID,
    val ready: Boolean,
    val namesAndStates: Map<String, GuildHomeAccessState>,
    val verifiedBackupReferences: List<String>,
    val unresolvedNames: Set<String>,
    val unavailable: Boolean = false,
)

/** No method in this repository is permitted to modify a saved guild-home location. */
interface GuildHomeAccessRepository {
    fun state(guildId: UUID, homeName: String): GuildHomeAccessState
    fun isManaged(guildId: UUID): Boolean

    /** Inspect historical mapping without editing any location or access state. */
    fun previewReconciliation(guildId: UUID): GuildHomeAuditPreview

    /** Safe for new guilds with zero historical locations; never guesses legacy attribution. */
    fun prepareEmptyGuild(guildId: UUID): Boolean

    /** Checks the FREE base/prestige slots, separately from valid paid Outpost grants. */
    fun canGrantNewHome(guildId: UUID, totalCapacity: Int): Boolean
    /** A newly purchased Outpost is also allowed to back a NEW saved location. */
    fun willUsePaidOutpostForNewHome(guildId: UUID, totalCapacity: Int): Boolean
    fun grantNewHome(guildId: UUID, homeName: String, totalCapacity: Int): Boolean
    fun removeGrant(guildId: UUID, homeName: String): Boolean
    fun removeAllGrants(guildId: UUID): Boolean

    /** Audited migration: exact saved-name set must match; never silently picks a first six. */
    fun reconcileVerified(
        guildId: UUID,
        grants: Map<String, VerifiedGuildHomeGrant>,
        provenance: String,
        reviewer: UUID,
    ): Boolean

    /** No gold movement. Consumes a previously paid, unassigned Outpost acquisition. */
    fun assignPaidOutpost(guildId: UUID, transactionId: UUID, homeName: String): Boolean
    fun assignAnyPaidOutpost(guildId: UUID, homeName: String): Boolean
}
