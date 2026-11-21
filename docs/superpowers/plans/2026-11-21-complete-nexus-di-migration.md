# Complete KoinComponent → Nexus DI migration (draft PR #254)

Date: 2026-11-21. Branch `feature/nexus-migration-bootstrap`. Verified starting head
`acf5898c99f4e21596a9c9ba367bee695025e658` (matches GitHub draft PR #254).
Requirements: REQ-NEXUS-DI-1 … REQ-NEXUS-DI-5 (`docs/requirements.md`). Task: `NEXUS-DI-COMPLETE` (`docs/tasks.md`).
Runtime control directory (outside the checkout): `D:/BadgersMC-Dev/LumaGuilds-Nexus-swarm-20261121`
(`STATUS.md`, worker logs, manifests, wiring patches, final `REPORT.md`).

## Goal

Remove every production `KoinComponent`, `by inject()` and global Koin lookup (`GlobalContext`, `getKoin()`)
outside the transitional composition roots, using explicit constructor injection and typed lazy
dependency bundles. Behavior-preserving only (NEXUS-MIGRATION.md release gates). The live lifecycle stays
Koin/ACF-owned; the Nexus foundation stays inert; no dual live contexts.

## Baseline inventory at `acf5898c` (reproducible scan)

Scan: `grep -rn KoinComponent src/{main,test}/kotlin`, lines classified as import / comment / declaration / other.

| Kind | production | test |
|---|---:|---:|
| `KoinComponent` declarations (`: KoinComponent`, `, KoinComponent`, `object … : KoinComponent`) | 140 lines in 139 files | 0 |
| `import org.koin.core.component.KoinComponent` | 178 | 7 |
| Comment mentions (`NexusIntegrationBindings.kt:54`, `NexusVaultBindings.kt:58`) | 2 | 0 |
| Other (test assertions that a class is *not* a KoinComponent) | 0 | 10 |
| **All `KoinComponent` tokens** | **320 in 182 files** | 17 in 7 files |
| `by inject()` delegates | 883 lines in 208 files | – |
| `GlobalContext` / `getKoin()` lookups outside `di/Modules.kt` | MenuFactory 24, LumaGuilds 3, LumaGuildsCommand 4, GuildSettingsMenu 2, GuildChronicleMenu 2, GuildDashboard 1, GuildProgressionMenu 1, BedrockGuildSettingsMenu 1, GuildCommand 1, ProgressionEventListener 1, GuildTeamListener 1 | – |

26 Bedrock forms use `by inject()` without naming `KoinComponent` (they inherit it from `BaseBedrockMenu`), so the
conversion set is the union of all Koin users: **211 production files**; 207 non-root files after excluding
`LumaGuilds.kt`, `MenuFactory.kt` and the two `di/nexus` comment files.
Baseline Java 25 `clean test` at `acf5898c`: **2,695 tests, 0 failures, 0 errors, 106 skipped** (510 XML files).

## Design (follows ClaimPlayerMenu / BedrockMenuDependencies / MenuFactory providers)

1. **`MenuServices`** (`interaction/menus/MenuServices.kt`): one explicit class whose 98 constructor properties are
   `Lazy<T>` for the exact types the menus injected (generated from the inventory). `by inject()` becomes
   `by services.<name>` – identical lazy first-use timing, so claim-only services stay unresolved while claims
   are disabled and no new startup cycles appear. Optional lookups keep `…OrNull` lazies. No generic lookup.
2. Composition roots: `di/LegacyMenuServices.kt` (`Scope.legacyMenuServices(factory)`) and
   `di/nexus/NexusMenuServices.kt` (`NexusContext.nexusMenuServices(factory)`), bound through the new
   `MenuFactory(menuServicesProvider = …)`. `MenuFactory.menuServices` fails closed when unbound.
   Legacy Koin now also binds `bedrockDependencyProvider` (same singletons the base-class fallback resolved).
3. Java menus: `services: MenuServices` constructor parameter, passed to child menus they construct.
   Bedrock forms: required `BedrockMenuDependencies`, base accessors `bedrockDependencies` and `services`.
4. Commands/listeners/services/placeholders: explicit constructor parameters (`Lazy<T>` / typed providers where the
   former delegate deferred resolution or the service is optional), constructed by `LumaGuilds.kt` / `Modules.kt`
   and the matching inert Nexus registrations.
5. After all subclasses pass explicit dependencies, the `BaseBedrockMenu` and `ClaimCommand` compatibility
   fallbacks and their `KoinComponent` supertypes are removed by the integrator.

## Ownership and disjoint manifests

Integrator-owned shared roots (workers return patches, never commit them): `LumaGuilds.kt`, `di/Modules.kt`,
`di/LegacyMenuServices.kt`, `di/nexus/*.kt`, `interaction/menus/MenuFactory.kt`, `MenuServices.kt`,
`bedrock/BaseBedrockMenu.kt`, `bedrock/BedrockMenuDependencies.kt`.

| Worker | Branch | Files | LOC |
|---|---|---:|---:|
| java-a | `swarm/nexus-complete-java-20261121` | 39 Java guild menus (Alliance… GuildMode, Progression/Prestige/RewardPurchase/Statistics) | 14,513 |
| java-b | `swarm/nexus-complete-java-b-20261121` | 32 Java guild menus (Party… WarObjectives, RecruitmentController) + 11 management + 2 misc | 12,822 |
| bedrock | `swarm/nexus-complete-bedrock-20261121` | 69 Bedrock forms incl. `BedrockFormUtils` (not `BaseBedrockMenu`) | 13,569 |
| listeners | `swarm/nexus-complete-listeners-20261121` | 28 ACF commands, 15 interaction listeners, 6 infrastructure listeners, 3 services, 1 placeholder | 12,362 |

Exact file lists: `D:/BadgersMC-Dev/LumaGuilds-Nexus-swarm-20261121/workers/<worker>.manifest` (206 files, no
overlap; union + 4 shared roots = 211 inventory files). Known cross-partition construction edges, resolved by the
integrator: `GuildCommand → GuildStrikePenaltyMenu` (listeners→java-b), `VaultInventoryListener → GoldWithdrawMenu`
(listeners→java-a), `BedrockGuildRecruitmentMenu → RecruitmentController` (bedrock→java-b).

## Process per tranche

1. Worker: failing regression first (worker-specific test filename), minimum conversion, focused Java 25 tests,
   commit only manifest files and new tests; export shared-root edits to `workers/<w>/wiring*.patch`.
2. Integrator: inspect diff, merge worker commits, apply wiring patches, resolve cross-partition call sites,
   focused tests, then full Java 25 `clean test` with XML totals (no source moves during a run).
3. Fast-forward push to draft PR #254 only when green; poll exact-head GitHub CI including the MariaDB step.
4. Recount the inventory and record it below.

## Evidence log

- Foundation (`MenuServices`, both roots, MenuFactory global lookups removed): see tranche table.

| Tranche | Commit | KoinComponent decl / import / comment | `by inject()` | Full suite | CI |
|---|---|---|---|---|---|
| baseline | `acf5898c` | 140 / 178 / 2 | 883 | 2,695 pass, 106 skip | (existing) |
