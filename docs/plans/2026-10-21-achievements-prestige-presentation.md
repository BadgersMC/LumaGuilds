# Achievement tree and prestige presentation

## Scope and requirements

- AP-001: WHEN a guild member browses achievements THE SYSTEM SHALL render a vanilla-inspired connected milestone tree with selectable category and node controls, actual provider progress or explicitly unavailable tracking, and earning, recipient and reward details. The viewer SHALL NOT fabricate progress or execute rewards.
- AP-002: WHEN a member browses Chronicle rewards THE SYSTEM SHALL distinguish prestige-retained rewards from home capacity permanently acquired by purchase, and describe the current level, guild-bank price, acquisition and retention rules.
- AP-003: WHEN a member opens prestige information THE SYSTEM SHALL show the configured limits and fee, permanent home bonus, required level, existing retained rewards and reset/preservation rules even before prestige is available. Review and confirmation SHALL use existing quote, authorization and transaction paths.
- AP-004: WHILE a dialog is stale, the actor is no longer a member or navigation has moved THE SYSTEM SHALL reject its callbacks through the existing session guard. Viewing SHALL NOT change balances, XP, reward ownership or prestige.
- AP-005: BEFORE shared TEST activation THE SYSTEM SHALL have an explicit matching resource handoff, pinned artifact and rollback evidence. Another coordinator's active claim SHALL prevent deployment or client input.

## Provenance and state

Authoritative main fetched 2026-10-21: `283d75f1e1016d415aa5afdfac840252f0391846`.
New isolated clone/branch starts there and fast-forwards the existing reviewed integration source `ea7cb90ded27ac2e8141eb624d1d3fa4f927b2d3`, preserving prior fixes. No shared checkout edited.

Browser designs are approved references, not proof of native Paper layout. The native tree requires a bounded row/column layout rather than claiming arbitrary browser panning works in dialog widgets. Advancement tracking/reward implementation is a separate pending user scope choice; the display-only TEST provider is not sufficient evidence of working progress.

Project-local EARS/state helpers are absent (`tools/spear` and tracked SPEAR scripts not found). This requirement/task/evidence note records the workflow rather than claiming validation tools passed.

## Tasks and evidence

- [ ] Prove prestige acquisition/retention presentation distinctions and quote guards.
- [ ] Implement prestige information and detailed earning descriptions.
- [ ] Implement provider-backed tree presentation within confirmed scope.
- [ ] Focused regressions, architecture check, complete build and exact-head review.
- [ ] Reviewable source PR and immutable local TEST artifact, not a production build.
- [ ] Acknowledged TEST window, backups, deployment, startup and native interactions.

Roundtable request 749 waits for Holiday owner release; no TEST/client claim is held. Build capacity must also be coordinated before native Gradle execution.

## Implementation checkpoint

The user explicitly selected full achievement tracking and rewards. The new domain engine bounds counters, handles absolute playtime/rank/level observations and deduplicates supplied event receipts. The platform adapter persists personal progress, claimed reward IDs, receipt IDs and tracked goals in player PDC. Personal XP and these fields are serialized by the same Minecraft player-data save; this is not a database transaction or proof of crash consistency. A verified unchanged XP failure leaves an unpaid completion for recovery; ambiguous XP outcomes retain claimed IDs for administrator review. No guild-XP/bank service is invoked by achievement completion.

Definitions include all 30 nodes from the observed Guild Mastery configuration, with the stale level-30 maximum corrected to level 100, and the Exile description aligned with the existing kick trigger. Total configured personal XP across all unique nodes is 3,600. Definitions are not economically configurable in this patch; the copied catalog preserves the observed reward values.

Core listeners use MONITOR and ignore cancelled activity. Creative/Spectator activity is excluded from collective world counters. Mature crops are checked. Boss recipients are online guild members in the same world; Dragon additionally requires three members in the End. Per-player achievement records survive guild changes and prestige; online-only collective credit is stated explicitly. Existing legacy advancement records are not silently imported or cleared; TEST migration/reward-history reconciliation remains an acceptance gate before enabling new rewards for existing completed records.

Optional projection registers only `lumaguilds_mastery` through the observed provider API, preserving other namespace owners. The full vanilla view supports panning; dialog branches use bounded native rows and pages. Provider absence keeps dialog tracking available. No claim that the HTML canvas can be pasted into a native dialog.

Outstanding contracts: Market's retained post-transaction event lacks a durable transaction ID; only same-instance duplicate dispatch is suppressed for that API. No current committed guild-stall acquisition event has been verified for Prime Real Estate. Roundtable request 757 asks the Market owner for immutable supported contracts; do not mark those integration gates passed. KOTH optional lifecycle adapter excludes private test fixtures, retains arena streak state and uses event IDs; native companion compatibility and replay/crash recovery remain unproved.

No historical RED run is claimed: local native execution is held by shared capacity/owner coordination. Added tests are pending execution. Python JSON and YAML parse checks and Git whitespace validation passed. No TEST mutation, build or deployment has happened.

## First hosted proof and refinement

Head eb51eab9 compiled on hosted Java 25 and discovered 2,220 test cases: six failures and 68 skips. This is real failure evidence, not a passing build. Failures: Koin graph eager runtime construction (2 profiles), locale dynamic/dead reference baselines (2), new player fixture completion and new prestige fixture (2). Refinements defer resource/PDC construction until runtime use, retain literal locale keys without changing baselines, use a valid one-selection/one-prestige fixture, and explicitly stub the spy player's online state. Fresh CI must verify these fixes.

The weak Market notification hook is removed. The existing GuildShopXpApi v2 complete path already supplies an immutable sale UUID after durable paid guild SELL settlement. An optional callback publishes that same receipt to personal achievement tracking for AWARDED, CAPPED, COOLDOWN and DISABLED outcomes. OWN_GUILD, ABORTED, STALE_RUN and CHAPTER_FROZEN do not count. Callback failures never change the returned economic API result. No prepare/payment/award semantics changed. Barter/BUY/personal/free trades remain excluded under that existing API contract; they are not represented as supported achievement transactions.

Progress-only updates are cached and checkpointed each minute and on quit/disable; completions and tracked-goal writes save immediately. An ambiguous personal XP delivery now persists a review-required quarantine marker and never auto-replays. Data corruption is preserved and fails closed.

Read-only TEST UAAPI SQLite snapshot: SHA256 940985e9716a40c515eff406af7440b7ff7f9225a69ede9e5eb776d4a83fcdfa, only enthusia (602 rows) and holidays (30 rows); no guild namespace progress or unredeemed guild reward rows. This clears legacy guild duplicate-reward concern for this snapshot only; recheck immediately before activation. No identities were exported and no table was modified.

GitHub PR241 is stacked against PR240's exact integration head. The hosted workflow checks out the PR head explicitly and retains a clearly marked unmerged TEST shadow JAR plus source/hash manifests only after tests pass; failed test diagnostics are retained separately. No local native build was started below the capacity gate.
