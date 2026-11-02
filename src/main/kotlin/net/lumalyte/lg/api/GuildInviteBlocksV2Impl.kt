package net.lumalyte.lg.api

import net.lumalyte.lg.application.services.DurableInviteBlockService
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Publishes the separate SQLite completion contract without changing the v1 wire surface. */
internal class GuildInviteBlocksV2Impl(private val blocks: DurableInviteBlockService) : GuildInviteBlocksV2 {
    override fun apiVersion(): Int = 2

    override fun storageScopeKind(): String = "BACKEND_LOCAL_SQLITE"

    override fun isBlockedDurable(ownerId: UUID, targetId: UUID): Boolean =
        blocks.isBlockedDurable(ownerId, targetId)

    override fun setBlockedDurable(ownerId: UUID, targetId: UUID, blocked: Boolean): CompletionStage<Void> =
        try {
            val result = blocks.setBlockedDurable(ownerId, targetId, blocked)
            if (result.succeeded) {
                CompletableFuture.completedStage<Void>(null)
            } else {
                CompletableFuture.failedStage(
                    IllegalStateException("Guild invite block completion is UNKNOWN", result.failure),
                )
            }
        } catch (failure: Exception) {
            CompletableFuture.failedStage(IllegalStateException("Guild invite block completion is UNKNOWN", failure))
        }
}
