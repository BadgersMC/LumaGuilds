package net.lumalyte.lg.api;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Authoritative, backend-local SQLite invite blocks. Registered separately from {@link GuildInviteBlocks}.
 * All values crossing plugin classloaders are JDK types. Both operations may perform synchronous database I/O;
 * callers should dispatch them off the main thread. The implementation does not enqueue work or retries.
 *
 * <p>Consumers pin the registered provider and their established {@code backend:<server-id>} scope. This API does
 * not establish a server identifier, a shared-database identity, or ownership of manual block changes.
 * The provider permanently rejects durable operations after plugin disable, including through captured references.
 * A new enable creates a new provider; an old provider never reopens.
 */
public interface GuildInviteBlocksV2 {
    /** Returns 2 for this contract. */
    int apiVersion();

    /** Returns {@code BACKEND_LOCAL_SQLITE}. Shared-database providers do not register this capability. */
    String storageScopeKind();

    /**
     * Reads committed database state and reconciles this provider's cache for the pair before returning.
     * Failure is unavailable/unknown, never an authoritative {@code false}.
     */
    boolean isBlockedDurable(UUID ownerId, UUID targetId);

    /**
     * Sets the requested database state, including when the cache already appears to match.
     * Normal completion contains exactly {@code null}: the requested state is committed, this provider's cache
     * is reconciled, and no work belonging to this invocation remains queued or able to apply later.
     * Exceptional completion, a thrown exception, or caller cancellation/timeout is UNKNOWN, not proof of rollback.
     * In particular, an error after a commit attempt can mean the write committed. Consumers retain unresolved
     * obligations and must not use a cache read or a caller-created timeout as a terminal acknowledgement.
     *
     * <p>Covered pending-invitation removal is synchronous best-effort work. Its failure is diagnosed separately
     * and cannot undo an already committed block. This method does not transfer ownership of manual changes,
     * reconcile another provider's cache, or fence an earlier invocation with an UNKNOWN outcome.
     */
    CompletionStage<Void> setBlockedDurable(UUID ownerId, UUID targetId, boolean blocked);
}
