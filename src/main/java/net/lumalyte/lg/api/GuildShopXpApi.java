package net.lumalyte.lg.api;

import java.util.UUID;

/** Versioned server-internal Market contract. All values cross plugin classloaders as JDK types.
 * Calls are synchronous and durable; exceptions mean retry/unavailable, never eligibility=false.
 * prepare must precede payment. complete is only for a durably completed paid SELL purchase.
 * No personal, free, BUY or barter trade may call this API. Replaying an ID is idempotent.
 */
public interface GuildShopXpApi {
    int apiVersion();
    String prepare(UUID saleId, UUID owningGuildId, UUID buyerId, long occurredAtMillis);
    /** Terminal codes: AWARDED:n, OWN_GUILD, DISABLED, COOLDOWN, CAPPED, STALE_RUN. */
    String complete(UUID saleId);
}
