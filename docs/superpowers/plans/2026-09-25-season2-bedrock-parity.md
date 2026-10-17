# Season 2 Bedrock Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: execute task-by-task with SPEAR discipline and preserve unrelated worktree changes.

**Goal:** Bring the dedicated Floodgate/Cumulus Bedrock experience to Chapter 2 functional parity with the approved Java Season 2 surface without duplicating domain/application logic.

**Source audit:** `docs/season2-bedrock-parity-audit-2026-09-25.md` (BPAR-001..BPAR-020).

**Requirements:** REQ-110..REQ-120 plus existing REQ-023, REQ-039, REQ-074..REQ-079, REQ-093, REQ-101, REQ-104..REQ-109.

**Architecture:** Bedrock remains an interaction adapter. Current domain/application services remain authoritative for quests, prestige, progression, guild settings, bank, statistics, homes, members/ranks, LFG, parties, relations and wars. Cumulus forms may present information differently from Java ChestGUI, but authorization, mutation semantics, canonical data, failure states and available actions must match.

## Global constraints

- Follow SPEAR: **Spec -> Probe -> Engine -> Arch -> Refine**.
- No production changes. Work only in the isolated Season 2 checkout/staging environment.
- Do not fork business logic into Bedrock forms; consume existing shared services/read models.
- Never invent a believable zero/default when the backing data is unavailable.
- Every mutable form checks authorization before rendering the action and again before persistence/service execution.
- Bedrock callbacks that touch Bukkit/plugin state return to the server thread.
- Supported Bedrock flows stay in Cumulus forms; do not directly construct Java inventory menus.
- Java and Bedrock runtime acceptance are separate release gates.
- Preserve current working Bedrock war/diplomacy/list/info/home/payment behavior unless a failing contract proves a parity defect.
## SPEAR acceptance model

### Spec

The audit and REQ-110..REQ-120 define parity by **behavior and data authority**, not pixel/layout identity. A Bedrock form passes when it exposes the same supported action/data state, uses the same authorization and mutation service, reports the same success/failure meaning, and never substitutes stale/fictional state.

### Probe

For each task, add the smallest focused failing contract before implementation. Prefer behavior/service-facing tests over source-string checks. Source-wiring contracts are acceptable only where Floodgate/Cumulus objects cannot be instantiated safely in unit tests.

Required first probes:
- Dashboard required-destination coverage, including Quests.
- No direct Bedrock -> Java guild-menu construction.
- Unauthorized bank/settings/rank mutation rejection.
- No fabricated Bedrock statistics constants.
- Prestige and Quest factory routing produces dedicated Bedrock forms.

### Engine

Implement the minimum form-layer change necessary to satisfy each probe. Reuse existing services and immutable quotes/requests. Do not redesign the underlying Chapter 2 economy, progression, quest, war or persistence contracts.

### Arch

Run layer rules and review that changes remain in interaction plus narrowly required shared helpers. Any new reusable policy belongs in application/domain only if both Java and Bedrock genuinely need it; interaction convenience code must not leak Cumulus/Floodgate into stable layers.

### Refine

Run focused tests, full `test shadowJar`, localization/resource contracts, `git diff --check`, and static analysis. Record exact evidence in `docs/tasks.md`. Runtime Bedrock sign-off waits for a compatible Geyser/Floodgate staging boot and real Bedrock client walkthrough.

---

### Task 1: Parity contracts, authorization, and navigation isolation

**Task:** LG-1801
**Files:** MenuFactory, BedrockMenuNavigator, BaseBedrockMenu, Bedrock confirmation/menu routing, new Bedrock parity tests.

- [ ] Add failing coverage for the ten Season 2 dashboard domains and dedicated Bedrock routing.
- [ ] Add a failing scan/contract proving Bedrock guild flows do not directly construct Java guild menus.
- [ ] Add negative authorization tests for bank-management/settings/rank mutations.
- [ ] Replace direct Java-menu success/back fallbacks with platform-aware MenuFactory/Bedrock routes.
- [ ] Finish the navigator state/back-stack primitives required by multi-step forms.
- [ ] Run focused tests and layer rules GREEN.
### Task 2: Weekly Guild Quests

**Task:** LG-1802
**Files:** create `BedrockGuildQuestsMenu.kt`; modify MenuFactory/control panel/localization; tests.

- [ ] Probe: Bedrock Quest routing currently returns unavailable.
- [ ] Build a Cumulus quest browser from existing QuestService read/claim APIs.
- [ ] Render all six active quests, human-readable definition, guild progress, claim/reward state, reset time and supported leaderboard/full-set state.
- [ ] Claim through the existing idempotent service only.
- [ ] Preserve explicit unavailable/no-active-week states.
- [ ] Reopen LG-1607 only until this dedicated flow is complete.

### Task 3: Chapter 2 Progression and Prestige

**Task:** LG-1803
**Files:** BedrockGuildProgressionInfoMenu, new Bedrock prestige confirmation/selection form, localization/tests.

- [x] Probe dedicated Prestige routing and current six-prestige state.
- [x] Expose disabled/unavailable/not-level-100/max/ready states from GuildPrestigeService.
- [x] Show prestige count, max 6, next fee and eligible retained rewards.
- [x] Quote and confirm using the same immutable PrestigeQuote/idempotent service as Java.
- [x] Keep failed/uncertain retry on the same quote where required.
- [x] Remove or quarantine contradictory legacy PerkType presentation from the current Chapter 2 route.

### Task 4: Settings parity

**Task:** LG-1804
**Files:** BedrockGuildSettingsMenu and optional focused theme/access forms; localization/tests.

- [x] Add persisted current-state controls for guild open/closed, Lunar tracking and GUI theme.
- [x] Use the same Java permission requirements and GuildService mutations.
- [x] Preserve existing description/mode behavior and route tag/banner/emoji/homes coherently.
- [x] Unauthorized users see read-only/denied state and cannot mutate via crafted response.
### Task 5: Truthful Bedrock Statistics

**Task:** LG-1805
**Files:** BedrockGuildStatisticsMenu, shared presentation helpers only where justified, localization/tests.

- [x] Probe and remove hardcoded 0/800 XP, always-Active status, join-time-as-activity, and 0/0/1 territory placeholders.
- [x] Consume current kill/war/member/bank/invitation/leaderboard services used by Java.
- [x] Expose supported kill, war, member/performance, top-killer/contributor/inviter, K/D, periodic, rivalry and achievement states.
- [x] Hide unsupported surfaces or render explicit unavailable state; empty data must remain distinguishable.
- [x] No CSV/export control.

### Task 6: Bank safety and navigation shell

**Task:** LG-1806
**Files:** BedrockGuildBankMenu, BedrockBankSettingsEditor, automation/budget/security forms, localization/tests.

- [x] Probe unauthorized scheduled-deposit/settings mutation.
- [x] Gate all bank-management controls with the same management authority as Java.
- [x] Add coherent Bedrock navigation to History, Statistics, Contributions, Automation, Budget and Security.
- [x] Preserve BankService canonical deposit/withdraw and ambiguous payout-review behavior.
- [x] Recheck authorization at mutation time.

### Task 7: Bank drill-down parity

**Task:** LG-1807
**Files:** Bedrock history/statistics/contributions/automation/budget/security forms; tests.

- [x] Add bounded history paging plus type/member/date/search filters where supported by the existing service/repository contract.
- [x] Remove arbitrary top-10-only truncation when bounded paging is available.
- [x] Render real automation next-run/status and persisted settings.
- [x] Keep read-only status visibly read-only; no coming-soon action controls.
### Task 8: Homes, members, and ranks

**Task:** LG-1808
**Files:** new Bedrock HomeAccess/AllyHomeAccess forms; member/rank forms; tests.

- [x] Implement per-home rank whitelist and inbound ally-home access using existing services.
- [x] Preserve current paid home activation and payment-review semantics.
- [x] Add selected-member action flow covering the currently supported Java moderation actions.
- [x] Converge Bedrock rank creation/editing onto one permission model.
- [x] Preserve existing rank priority/identity during ordinary edits; reorder only through an explicit action.
- [x] Preserve rank-prefix and current permission semantics.

### Task 9: Party and LFG parity

**Task:** LG-1809
**Files:** Bedrock party forms, new Bedrock LFG browser, MenuFactory/localization/tests.

- [x] Replace the hardcoded unavailable party-permissions path with the current service-backed settings flow or an honest non-interactive hidden state.
- [x] Add a dedicated Bedrock LFG browser using the same bounded/service-backed discovery contract.
- [x] Keep browser -> join requirements -> join/result -> return entirely inside Bedrock forms.
- [x] Preserve paid admission/journal semantics through LfgService.

### Task 10: Warfare/diplomacy parity polish

**Task:** LG-1810
**Files:** Bedrock war declaration/management/peace/relations forms; tests.

- [x] Preserve existing durable WarService-backed behavior.
- [x] Compare every currently supported ObjectiveType/declaration option with Java and add missing configuration such as time-survival where supported.
- [x] Verify wager, declaration accept/reject/cancel, kill progress, history/stats and peace flows use authoritative persisted state.
- [x] Keep diplomacy request/alliance/truce/enemy behavior service-backed; fix only proven parity defects.
### Task 11: Localization and platform truthfulness

**Task:** LG-1811
**Files:** Bedrock forms/locales and locale contracts.

- [x] Remove hardcoded player-facing fallback text in audited Bedrock flows.
- [x] Remove stale coming-soon/unavailable text for features implemented by this tranche.
- [x] Ensure dynamic strings and proper names remain readable as plain Bedrock form text.
- [x] Keep Discord invite URLs visible as plain text per existing description contract.

### Task 12: Full verification and real Bedrock acceptance

**Task:** LG-1812
**Files:** tests/docs/staging only.

- [x] Run focused Bedrock parity contracts.
- [x] Run architecture/localization/resource contracts.
- [x] Run full `gradlew.bat test shadowJar --no-daemon`; record exact JUnit totals.
- [x] Run `git diff --check` and static analysis.
- [x] Provision local staging with compatible Geyser/Floodgate/Cumulus; do not alter production.
- [ ] Real Bedrock walkthrough: dashboard, quests/claim, reward purchase/prestige, settings/denials, bank, homes/access, members/ranks, party/LFG, diplomacy/war, statistics, close/back/timeout/reconnect. Execution checklist: `docs/season2-client-acceptance-2026-09-26.md`.
- [ ] Report Java and Bedrock sign-off independently.

## Dependency order

LG-1801 -> {LG-1802, LG-1803, LG-1804, LG-1805, LG-1806}
LG-1806 -> LG-1807
LG-1801 -> LG-1808 -> LG-1809
LG-1801 -> LG-1810
LG-1802..LG-1810 -> LG-1811 -> LG-1812

## Release gate

Chapter 2 Bedrock parity is not complete until all P0 BPAR findings are closed, every retained visible action is functional/authorized, fabricated statistics are gone, the full suite is green, and a real Bedrock client completes LG-1812 against staging. Static/source parity alone is insufficient.
