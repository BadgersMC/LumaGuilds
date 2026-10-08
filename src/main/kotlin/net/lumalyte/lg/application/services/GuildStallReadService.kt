package net.lumalyte.lg.application.services

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** Optional Market read boundary; absence is never interpreted as no ownership. */
internal interface GuildStallReadService {
    /** Reads authoritative stall data; an unavailable companion is never an empty ownership result. */
    fun read(guildId: UUID, viewerId: UUID): CompletableFuture<StallReadResult>
}

/** Explicit availability separates empty ownership from an integration failure. */
internal sealed interface StallReadResult {
    data object Unavailable : StallReadResult

    data class Available(
        val stalls: List<GuildStallInfo>
    ) : StallReadResult
}

/** Immutable presentation data supplied by Market rather than inferred from ranks. */
internal data class GuildStallInfo(
    val id: String,
    val region: String,
    val world: String,
    val state: String,
    val rent: Long,
    val intervalSeconds: Long,
    val nextRentAt: Instant?,
    val graceEndsAt: Instant?,
    val coordinates: String?,
    val members: List<GuildStallMemberInfo>
)

/** Current guild members and the actions permitted by Market's guild access rules. */
internal data class GuildStallMemberInfo(
    val playerId: UUID,
    val permissions: Set<GuildStallPermission>
)

/** These capabilities describe guild authority; global staff bypasses are separate. */
internal enum class GuildStallPermission { MANAGE_SHOPS, ACCESS_SHOP_CHESTS, EDIT_SHOP_STOCK, MODIFY_SHOP_PRICES }
