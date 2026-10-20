// Existing public MemberService/MemberRepository ports expose these result types.
// Preview value equality binds confirmation to the complete state the operator reviewed.
@file:Suppress("LibraryEntitiesShouldNotBePublic", "ForbiddenPublicDataClass")

package net.lumalyte.lg.application.results

import net.lumalyte.lg.domain.entities.Member
import java.util.UUID

/** An authoritative, unchanged-state requirement for recovering one existing guild member. */
data class OwnerRecoveryPreview(
    /** Existing guild whose ownership is being recovered. */
    val guildId: UUID,
    /** Guild name read from storage for the administrator's confirmation. */
    val guildName: String,
    /** Existing membership, including the previous rank and persisted join timestamp. */
    val member: Member,
    /** Previous rank name read from storage for the administrator's confirmation. */
    val currentRankName: String,
    /** The guild's sole rank with priority zero. */
    val ownerRankId: UUID,
    /** Owner rank name read from storage for the administrator's confirmation. */
    val ownerRankName: String,
)

/** Fail-closed recovery outcomes; storage failures do not establish whether a commit succeeded. */
enum class OwnerRecoveryRejection {
    /** No stored guild matches the requested UUID. */
    GUILD_NOT_FOUND,
    /** The target UUID has no existing membership in this guild. */
    MEMBER_NOT_FOUND,
    /** The guild's ranks are malformed or do not contain exactly one Owner rank. */
    INVALID_OWNER_RANKS,
    /** A membership is malformed or refers to a missing or foreign-guild rank. */
    INVALID_MEMBER_RANKS,
    /** A current member already holds the guild's Owner rank. */
    ALREADY_OWNED,
    /** The stored membership, guild name, or relevant rank changed after the preview. */
    STALE_PREVIEW,
    /** Storage could not confirm the outcome; administrators must inspect current ownership. */
    STORAGE_FAILURE,
    /** This repository adapter does not implement authoritative ownership recovery. */
    UNSUPPORTED_REPOSITORY,
}

/** Result of reading a recoverable ownerless guild directly from persistent storage. */
sealed interface OwnerRecoveryPreviewResult {
    /**
     * Valid ownerless state that can be presented for an administrator's confirmation.
     *
     * @property preview The stored state that a subsequent confirmation must still match.
     */
    data class Ready(val preview: OwnerRecoveryPreview) : OwnerRecoveryPreviewResult

    /**
     * A preview could not establish valid, recoverable ownerless state.
     *
     * @property reason The validation or storage condition that prevented a preview.
     */
    data class Rejected(val reason: OwnerRecoveryRejection) : OwnerRecoveryPreviewResult
}

/** A recovered result is published only after the ownership write has committed. */
sealed interface OwnerRecoveryResult {
    /**
     * The existing member's Owner rank was committed using this validated preview.
     *
     * @property preview The unchanged state validated immediately before the committed rank update.
     */
    data class Recovered(val preview: OwnerRecoveryPreview) : OwnerRecoveryResult

    /**
     * Recovery did not confirm a successful commit.
     *
     * @property reason The validation or storage condition that prevented confirmed success.
     */
    data class Rejected(val reason: OwnerRecoveryRejection) : OwnerRecoveryResult
}
