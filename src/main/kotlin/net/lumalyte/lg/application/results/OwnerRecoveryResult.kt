package net.lumalyte.lg.application.results

import net.lumalyte.lg.domain.entities.Member
import java.util.UUID

/** An authoritative, unchanged-state requirement for recovering one existing guild member. */
data class OwnerRecoveryPreview(
    val guildId: UUID,
    val guildName: String,
    val member: Member,
    val currentRankName: String,
    val ownerRankId: UUID,
    val ownerRankName: String,
)

/** Fail-closed recovery outcomes; storage failures do not establish whether a commit succeeded. */
enum class OwnerRecoveryRejection {
    GUILD_NOT_FOUND,
    MEMBER_NOT_FOUND,
    INVALID_OWNER_RANKS,
    INVALID_MEMBER_RANKS,
    ALREADY_OWNED,
    STALE_PREVIEW,
    STORAGE_FAILURE,
    UNSUPPORTED_REPOSITORY,
}

/** Result of reading a recoverable ownerless guild directly from persistent storage. */
sealed interface OwnerRecoveryPreviewResult {
    data class Ready(val preview: OwnerRecoveryPreview) : OwnerRecoveryPreviewResult

    data class Rejected(val reason: OwnerRecoveryRejection) : OwnerRecoveryPreviewResult
}

/** A recovered result is published only after the ownership write has committed. */
sealed interface OwnerRecoveryResult {
    data class Recovered(val preview: OwnerRecoveryPreview) : OwnerRecoveryResult

    data class Rejected(val reason: OwnerRecoveryRejection) : OwnerRecoveryResult
}
