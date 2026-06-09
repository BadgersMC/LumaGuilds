# Audit Report: application/results + application/persistence

**Batch:** audit-app-results-ports
**Scope:** 82 files — 24 persistence ports, 58 result DTOs
**Date:** 2026-00-09

---

## Findings

### [SEV: high] application/persistence/GuildVaultRepository.kt:3 — Bukkit ItemStack leaked into application layer

**What:** `GuildVaultRepository` (a persistence port in the application layer) imports `org.bukkit.inventory.ItemStack` and uses it in method signatures (`saveVaultInventory`, `getVaultInventory`, `saveVaultItem`, `getVaultItem`). This is a direct framework dependency crossing the hexagonal boundary into the application core.

**Why it matters:** The persistence port is the contract that infrastructure implements. By embedding `ItemStack` in the port, every consumer — test doubles, alternative storage backends, and the domain services that call the port — is forced to depend on Bukkit. This defeats the purpose of the port/adapter split: you cannot unit-test vault logic without a Bukkit server, and you cannot swap to a non-Bukkit storage (e.g., REST, flat-file) without changing the port. It also makes the interface unresolvable at compile time outside a Paper/Spigot environment.

**Suggested fix:** Introduce a domain/value-layer representation (e.g., `VaultItem` data class with material, amount, name, lore) in `domain/values/`, map to/from `ItemStack` in the infrastructure adapter (`GuildVaultRepositoryImpl`), and change the port to use the domain type. The mapping is the adapter's job.

**Confidence:** high

---

### [SEV: med] application/results/common/TextValidationErrorResult.kt:4 — maxCharacters typed as String instead of Int

**What:** `ExceededCharacterLimit` holds `maxCharacters: String`. Character limits are inherently integer values. The callers (`DescriptionCommand.kt:49`, `RenameCommand.kt:54`) use it in string interpolation (`arrayOf(name.count().toString(), firstError.maxCharacters)`), which works but loses type safety.

**Why it matters:** A String field for a numeric boundary invites subtle bugs — e.g., a future refactor could pass `"unlimited"` or `"N/A"` into the field and the compiler wouldn't catch it. The `.count()` comparison that precedes this is done in `Int`; storing the limit as `String` forces an implicit contract that "this string is always a parseable integer". Every consumer must trust that invariant.

**Suggested fix:** Change `maxCharacters: String` to `maxCharacters: Int`. Update callers to use `firstError.maxCharacters.toString()` at the interpolation boundary.

**Confidence:** high

---

### [SEV: med] application/results/claim/permission/GrantAllClaimWidePermissionsResult.kt:5 — inconsistent error variant naming

**What:** The `ClaimNotFound` variant is absent; instead the file defines `ClaimWideNotFound`. Every other permission result in the same package (`GrantClaimWidePermissionResult`, `RevokeClaimWidePermissionResult`, `RevokeAllClaimWidePermissionsResult`) uses `ClaimNotFound`. This is the sole exception.

**Why it matters:** Callers doing `when (result)` across the permission result family must handle two names for the same semantic case, or risk a missing branch. It breaks the polymorphic contract — a common handler for "permission results" cannot uniformly match `ClaimNotFound`.

**Suggested fix:** Rename `ClaimWideNotFound` to `ClaimNotFound` to match the rest of the permission result family.

**Confidence:** high

---

### [SEV: low] application/persistence/ClaimRepository.kt:40 — getByTeam uses stale "team" terminology

**What:** `getByTeam(teamId: UUID)` uses the word "team" while the entity is named `Guild` everywhere else (`GuildRepository.getByPlayer`, `getByGuild`, etc.). The doc also says "team/guild" — the canonical name is `Guild`.

**Why it matters:** Naming inconsistency creates confusion for new contributors and makes API discovery harder. If someone searches for "guild claim lookup", this method won't surface. It's minor but contributes to a fragmented mental model.

**Suggested fix:** Rename to `getByGuild(guildId: UUID)` and deprecate the old name, or simply rename if no external callers depend on the exact signature.

**Confidence:** med

---

### [SEV: low] application/persistence/PartitionRepository.kt:66 — Javadoc mismatch on remove()

**What:** `@param partition The id of the partition to remove` — the parameter name is `partitionId`, not `partition`. The doc should read `@param partitionId`.

**Why it matters:** Misleading Javadoc. Low severity but contributes to documentation rot.

**Suggested fix:** Update the `@param` tag to match the parameter name.

**Confidence:** high

---

### [SEV: low] application/results/player/visualisation/GetVisualiserModeResult.kt:4 — visualiserMode as raw Int

**What:** `visualiserMode: Int` encodes a mode flag as an untyped integer. `ToggleVisualiserModeResult` uses the same pattern. No enum or sealed class constrains the valid values.

**Why it matters:** Consumers must know which integers are valid (0, 1, 2?). A wrong integer silently propagates. This is a standard primitive-obsession code smell in result DTOs that are supposed to be strongly typed.

**Suggested fix:** Introduce a `VisualiserMode` sealed class or enum in `domain/values/` and use it here.

**Confidence:** med

---

### [SEV: low] application/results/player/visualisation/ToggleVisualiserModeResult.kt:5 — cooldownTime unit ambiguity

**What:** `OnCooldown(val cooldownTime: Int)` — the unit (seconds? milliseconds? ticks?) is undocumented and ambiguous.

**Why it matters:** A cooldown of `30` could mean 30 seconds or half a second depending on convention. This is a communication bug between the service that calculates cooldown and the UI layer that displays it.

**Suggested fix:** Document the unit in KDoc, or use `java.time.Duration` / `kotlin.time.Duration`.

**Confidence:** med

---

### [SEV: low] application/results/claim/permission/GrantGuildMembersClaimPermissionsResult.kt:6-38 — over-documented trivial result

**What:** The entire file has KDoc on every variant explaining what the variant means (e.g., "The claim was not found."). This is the only result file in the batch with KDoc on individual variants; all 57 others rely on the type name alone.

**Why it matters:** Not a bug, but indicates an inconsistent documentation standard. If KDoc on variants is a project convention, it's missing from 57 files. If it's not, this file is noise.

**Suggested fix:** Either adopt KDoc-on-variants as a standard (and apply to all results), or remove the redundant KDoc from this file.

**Confidence:** low

---

## Test Coverage Gaps

| Untested file | Behavior that lacks test | Suggested test |
|---|---|---|
| `GuildVaultRepository` (all 13 methods) | No direct port-level contract test; no test for `addGoldBalance`/`subtractGoldBalance` returning `-1` on failure | `GuildVaultRepositoryTest` verifying port semantics via a fake impl; test negative-balance guard in `subtractGoldBalance` |
| `ProgressionRepository` (all 18 methods) | Zero test references in the entire codebase | Mock-based test for `incrementActivityMetric`, `getTopGuildsByMetric`, and `resetLeaderboardPeriod` edge cases |
| `TextValidationErrorResult.Companion` (factory) | No test verifying that `ExceededCharacterLimit.maxCharacters` is always a parseable integer | Round-trip factory test |
| `GrantGuildMembersClaimPermissionsResult` | No test covering `NotClaimOwner` / `ClaimNotGuildOwned` / `NoGuildMembers` branches | Branch coverage test via service-layer mock |
| All 58 result DTO sealed classes | No structural test (exhaustive `when` verification) | Compile-time exhaustiveness check or runtime sealed-subclass count test |
| `RemovePartitionResult` mixed naming | `Disconnected` vs `ExposedClaimAnchor` order is not validated | Test that `CanRemovePartitionResult` and `RemovePartitionResult` share the same variant names where semantics overlap |
| `PlayerStateRepository.remove(playerState)` | Takes the full object, not an ID — unlike every other repo | Verify that `remove` actually removes by player ID, not object equality |

---

## Layer Boundary Check

| File | Verdict |
|---|---|
| All 24 `application/persistence/*` ports (except `GuildVaultRepository`) | PASS — domain/value types only, no Bukkit/JDBC |
| `GuildVaultRepository.kt:3` | **FAIL** — `org.bukkit.inventory.ItemStack` leaked into port API (see finding #1) |
| All 58 `application/results/*` DTOs | PASS — only import from `application.results.*` and `domain.*`; no Bukkit/JDBC/frameworks |
| `ProgressionRepository.kt:4` | PASS — `ExperienceSource` is `application.services`, same layer |

**Verdict:** 1 layer-boundary failure out of 82 files (`GuildVaultRepository.kt`). The result DTOs are clean. The persistence ports are clean except for the one Bukkit leak.
