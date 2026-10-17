# Staff strike feed review

This review records the sanitized rebuild of the Staff strike feed after the public LumaGuilds asset-history purge. The branch is based on the current post-#222 `main` and intentionally carries no `resourcepack/` files or private Nexo assets.

The integration compiles against the exact merged EnthusiaStaff lifecycle API commit `8539bb8c77d7ecaf083546dda7ca8ccbfa8064e6` (Staff PR #408). The API jar used for local verification is built from that source and remains an ignored local dependency; it is not committed or bundled into LumaGuilds.

REQ-130 requires historical attribution to fail for retry when membership storage is unavailable rather than silently falling back to the player's current guild. Native EnthusiaStaff sanctions never use current-guild fallback. Imported LiteBans sanctions may use the configured fallback only after a successful historical membership read. A failed read propagates to the feed page handler, leaving the scan retryable instead of advancing the cursor with a false attribution.

The provider projector is separated from scheduler/cursor orchestration. Reconciliation retains provider lifecycle identities, expiration state, legacy backfill controls and idempotent strike updates. The feed prepares and applies pages asynchronously and guards against overlapping sweeps.

## 8 October sanitized validation

The original #209 fork branch is not mergeable because it is stacked on the pre-purge #208 history and still exposes the proprietary resource pack. This rebuild replays only the Staff-strike commits on sanitized `main`; the old final merge commit that reintroduced #208 was deliberately excluded.

Focused Staff feed, strike persistence and optional LiteBans tests pass against the exact Staff API plus the normal local compile dependencies. Hosted CI and code-quality checks remain separate merge gates for the replacement PR. No production deployment or live sanction acceptance is claimed by this review record.
