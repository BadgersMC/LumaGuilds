# Domain Layer Audit — findings report

## Summary

Audited 54 files in `domain/{entities,events,exceptions,values}`.
Found 10 findings: 3 high, 4 medium, 3 low.
Layer boundary violations are the dominant issue.

---

### [SEV: high] domain/entities/Claim.kt:57 — Bukkit API leaked into domain layer

**What:** `Claim.resetBreakCount()` accepts `org.bukkit.plugin.Plugin` and calls `org.bukkit.Bukkit.getScheduler()`. The class also imports `kotlin.concurrent.thread` (unused) and `org.bukkit.*` types. Domain entities must not depend on any Bukkit/server framework types.

**Why it matters:** This is a hard hexagonal-architecture violation. The domain layer cannot be unit-tested without a Bukkit server mock, and the entity is tightly coupled to the server runtime. Any change to the Bukkit scheduler API will break domain code.

**Suggested fix:** Extract the scheduler concern into a domain port (e.g., `TaskScheduler` interface in `domain/ports`), inject it into the application service that calls `resetBreakCount`, and keep `Claim` as a pure data class with no scheduler dependency. The `resetBreakCount` method should either move to an application service or accept an abstraction.

**Confidence:** high

---

### [SEV: high] domain/entities/VaultInventory.kt:3 — Bukkit ItemStack in domain entity

**What:** `VaultInventory` imports `org.bukkit.inventory.ItemStack` throughout — the class stores `ConcurrentHashMap<Int, ItemStack?>`, and `WriteBuffer` (same package) also uses `ItemStack`. These are domain-layer files depending on Bukkit.

**Why it matters:** `ItemStack` is a server-implementation type. The domain layer cannot be reasoned about, serialized, or tested independently of the Bukkit runtime. Every caller must convert to/from Bukkit types at the boundary anyway, so the domain loses type safety.

**Suggested fix:** Replace `ItemStack` with a domain value type (e.g., `VaultItem` holding material, amount, nbt-hash). Add mapping extension functions at the infrastructure boundary.

**Confidence:** high

---

### [SEV: high] domain/entities/ViewerSession.kt:3 — Bukkit Inventory/Player leaked into domain entity

**What:** `ViewerSession` imports `org.bukkit.entity.Player` and `org.bukkit.inventory.Inventory`, storing a raw `Inventory` reference as a property.

**Why it matters:** Same hexagonal boundary violation. The domain entity now holds a live Bukkit inventory handle, tying lifecycle management to the server. If the player disconnects, the `Inventory` reference may become stale, causing undefined behavior.

**Suggested fix:** Store a domain representation of the inventory view (e.g., a value class with the inventory title, size, and item slots mapped to domain types). Translate at the infrastructure boundary.

**Confidence:** high

---

### [SEV: high] domain/entities/PlayerState.kt:3 — application layer import in domain entity

**What:** `PlayerState` imports `net.lumalyte.lg.application.services.scheduling.Task`. Domain must not import anything from `application/` or `infrastructure/`.

**Why it matters:** This creates a bidirectional dependency: domain → application. In hexagonal architecture, dependency must point inward only. This makes it impossible to compile or test the domain independently.

**Suggested fix:** Define a `ScheduledTask` interface (or `Cancellable`) in the domain layer, and have the application service implement it. `PlayerState` should depend only on the domain-defined interface.

**Confidence:** high

---

### [SEV: high] domain/entities/Progression.kt:3 — application layer imports in domain entity

**What:** `GuildProgression` imports `net.lumalyte.lg.application.services.ExperienceSource` and `net.lumalyte.lg.application.services.PerkType`. Both are application-layer enums leaked into domain.

**Why it matters:** `PerkType` and `ExperienceSource` define application-level concepts (what perk types exist, what gives XP). These should either be domain enums or defined in a shared kernel — but not imported from the application layer into domain.

**Suggested fix:** Move `PerkType` and `ExperienceSource` to `domain/values/` as domain enums. If application-specific behavior wraps them, the application can define its own richer types that delegate to the domain enums.

**Confidence:** high

---

### [SEV: high] domain/entities/GuildBanner.kt:3 — application layer import in domain entity

**What:** `GuildBanner` imports `net.lumalyte.lg.application.services.BannerDesignData` and uses it as the type for `designData`.

**Why it matters:** The domain entity depends on an application-service type. `BannerDesignData` may itself contain framework dependencies or business logic that doesn't belong in a domain value.

**Suggested fix:** Replace with a domain type (e.g., `BannerDesign` value class) that holds only the raw design data (colors, patterns). The application service can adapt its `BannerDesignData` to/from this domain type.

**Confidence:** high

---

### [SEV: high] domain/events/*.kt (13 files) — Bukkit Event/HandlerList in domain events

**What:** All 13 event files in `domain/events/` extend `org.bukkit.event.Event` and import `org.bukkit.event.HandlerList`. Examples: `GuildCreatedEvent`, `GuildDisbandedEvent`, `GuildMemberJoinEvent`, `GuildBankDepositEvent`, `GuildBannerSetEvent`, `GuildHomeSetEvent`, `GuildLeaderboardRankChangeEvent`, `GuildLevelUpEvent`, `GuildMemberRemovedEvent`, `GuildOwnershipTransferEvent`, `GuildRelationChangeEvent`, `GuildTrackingChangedEvent`, `GuildVaultPlacedEvent`, `GuildWarDeclaredEvent`, `GuildWarEndEvent`, `GuildWarKillEvent`.

**Why it matters:** Domain events are the backbone of inter-aggregate communication. By extending `org.bukkit.event.Event`, the domain is permanently coupled to the Bukkit event bus. These events cannot be dispatched via a different mechanism (in-process bus, Kafka, etc.) without pulling in the entire server. Unit tests require Bukkit mocks for every event assertion.

**Suggested fix:** Replace `org.bukkit.event.Event` inheritance with plain Kotlin classes (data classes or sealed classes). Define a domain event interface (`DomainEvent`) with timestamp/metadata. At the infrastructure layer, adopt a Bukkit-specific adapter that wraps domain events into Bukkit events for plugin dispatching.

**Confidence:** high

---

### [SEV: med] domain/entities/War.kt:57 — isExpired always returns false for started wars

**What:** `War.isExpired` delegates to `remainingDuration?.isNegative`, but `remainingDuration` returns `Duration.ZERO` (not a negative duration) when `now >= endTime`. Therefore `isExpired` is always `false` for wars past their end time.

**Why it matters:** The `WarServiceBukkit` calls `war.isExpired` to detect wars that should auto-terminate. Since this never returns `true`, wars never expire by time. They must be ended manually or by other logic. This is a silent logic bug — no crash, but the expiration feature is dead code.

**Suggested fix:** In `remainingDuration`, return the raw `Duration.between(now, endTime)` without the `else Duration.ZERO` branch, or change `isExpired` to check `remainingDuration?.isNegative == true || remainingDuration == Duration.ZERO`.

**Confidence:** high

---

### [SEV: med] domain/entities/BankTransaction.kt:22 — integer overflow in overflow guard

**What:** The check `require(amount + fee <= Int.MAX_VALUE)` on line 22 can itself overflow. If `amount = Int.MAX_VALUE - 1` and `fee = 2`, then `amount + fee` overflows to a negative number, which is `<= Int.MAX_VALUE`, so the check passes. Later, `totalAmount` getter (`amount + fee`) also overflows silently.

**Why it matters:** A crafted large `amount` + `fee` combination could produce a negative `totalAmount`, breaking downstream accounting logic (bank balance could increase on withdrawal, decrease on deposit).

**Suggested fix:** Use `require(amount <= Int.MAX_VALUE - fee) { "..." }` which cannot overflow since `fee >= 0`.

**Confidence:** high

---

### [SEV: med] domain/entities/Guild.kt:63 — emoji validation allows single-character names

**What:** The emoji format check `emojiValue.length > 2` only validates that the string starts and ends with `:` and is longer than 2 characters. A string like `":x:"` passes validation but is unlikely a valid Nexo placeholder. More importantly, `": :"` also passes, and there's no check against disallowed characters or format consistency.

**Why it matters:** Invalid emoji strings propagate to display layers, where they may render raw placeholder text to players or cause Nexo lookup failures. The validation gives a false sense of safety.

**Suggested fix:** Add a regex check like `:^[a-zA-Z0-9_]+$:` for the inner name, or at minimum require the inner content (between colons) to be non-blank and alphanumeric.

**Confidence:** med

---

### [SEV: med] domain/entities/WriteBuffer.kt:24 — pendingDeletions is not thread-safe

**What:** `WriteBuffer.pendingDeletions` is a `MutableSet<Int>` (`mutableSetOf()`), which is not thread-safe, while `pendingSlots` uses `ConcurrentHashMap`. Concurrent calls to `bufferSlotChange()` can race on `pendingDeletions`, corrupting the set.

**Why it matters:** `WriteBuffer` is designed for concurrent access (it's in a vault system where multiple players may modify slots simultaneously). A corrupted `pendingDeletions` set could cause slots to not be cleared in the DB, or to be double-removed, leading to data inconsistency.

**Suggested fix:** Change `pendingDeletions` to `ConcurrentHashMap.newKeySet()` or wrap access with synchronization.

**Confidence:** med

---

### [SEV: low] domain/entities/Claim.kt:6 — unused import kotlin.concurrent.thread

**What:** `import kotlin.concurrent.thread` is unused in `Claim.kt`. The comment on line 53 confirms the Bukkit scheduler is used instead of thread-based approaches.

**Why it matters:** Unused imports add noise and may confuse readers about the concurrency model. Static analysis tools flag them.

**Suggested fix:** Remove the import.

**Confidence:** high

---

### [SEV: low] domain/entities/Partition.kt:8 — wildcard import java.util.*

**What:** `import kotlin.collections.ArrayList` appears on line 8 and `import java.util.*` on line 7, but `ArrayList` is already available from `java.util.*`. The explicit `kotlin.collections.ArrayList` import is unused (the code uses `java.util.ArrayList`).

**Why it matters:** Minor style issue — the kotlin.collections import shadows the java.util one but is never used, and the wildcard import makes it unclear which `ArrayList` is actually used.

**Suggested fix:** Remove the `kotlin.collections.ArrayList` import; keep `java.util.*` or prefer explicit imports.

**Confidence:** med

---

### [SEV: low] domain/events/GuildLeaderboardRankChangeEvent.kt:30 — handlerList is public val

**What:** `GuildLeaderboardRankChangeEvent` declares `val handlerList = HandlerList()` (public) while all other events use `private val handlers = HandlerList()`. This exposes the handler list as mutable public state.

**Why it matters:** External code could replace or clear the handler list, silently breaking event dispatch for this event only. The inconsistency also suggests a copy-paste oversight.

**Suggested fix:** Change to `private val handlerList = HandlerList()` to match the pattern used by all sibling events.

**Confidence:** med

---

## Test Coverage Gaps

| Untested behavior | File | Test that should exist |
|---|---|---|
| Guild name boundary (empty, 1 char, 32 chars, 33 chars) | `Guild.kt:57` | `GuildTest` |
| Guild emoji format validation (valid/invalid formats) | `Guild.kt:63` | `GuildTest` |
| Guild tag MiniMessage safety / length | `Guild.kt` | `GuildTest` |
| Guild description length validation at boundary (100 chars) | `Guild.kt:71` | `GuildTest` |
| GuildHomes defaultHome, withHome, withoutHome, hasHomes | `GuildHomes` in `Guild.kt` | `GuildHomeTest` or new `GuildHomesTest` |
| Rank permission set validation (empty, full, single) | `Rank.kt` | `RankTest` |
| Member self-kill prevention | `Kill.kt:22` | `KillTest` |
| Kill isInterGuildKill / isIntraGuildKill edge cases | `Kill.kt:28-35` | `KillTest` |
| BankTransaction overflow guard at Int boundary | `BankTransaction.kt:22` | `BankTransactionTest` |
| BankTransaction companion factory methods | `BankTransaction.kt:32-51` | `BankTransactionTest` |
| MemberContribution contributionStatus transitions | `BankTransaction.kt:77-82` | `MemberContributionTest` |
| Party isActive with expiry, canPlayerJoin role restrictions | `Party.kt` | `PartyTest` |
| PartyRequest isValid/isExpired state machine | `PartyRequest.kt` | `PartyRequestTest` |
| Relation guild ordering invariant, involves, getOther, isActive | `Relation.kt` | `RelationTest` |
| PeaceAgreement isValid/remainingTime | `PeaceAgreement.kt` | `PeaceAgreementTest` |
| War isActive/isExpired/remainingDuration | `War.kt` | `WarTest` |
| WarWager isDraw/getRefundAmount/getWinningsAmount | `WarWager.kt` | `WarWagerTest` |
| GuildProgression canLevelUp/levelProgress/calculateExperienceForLevel | `Progression.kt` | `GuildProgressionTest` |
| WriteBuffer thread-safety of pendingDeletions | `WriteBuffer.kt` | `WriteBufferTest` |
| VaultInventory subtractGold CAS loop correctness | `VaultInventory.kt` | `VaultInventoryTest` |
| Area isAreaAdjacent with various spatial relationships | `Area.kt` | `AreaTest` |
| Area isAreaOverlap edge cases (touching, containing) | `Area.kt` | `AreaTest` |
| Partition Builder incomplete build throws | `Partition.kt:113-118` | `PartitionTest` |
| Partition Resizer getExtraBlockCount | `Partition.kt:135-140` | `PartitionTest` |
| Position/Position2D/Position3D getChunk correctness at boundaries | `Position.kt`, `Position2D.kt`, `Position3D.kt` | `PositionTest` |
| AuditRecord blank action rejection | `AuditRecord.kt:25` | `AuditRecordTest` |
| Leaderboard getTopEntries/getEntry/getRank | `Leaderboard.kt` | `LeaderboardTest` |

---

## Layer Boundary Check

**FAIL — 7 files with domain → application imports, 16 files with domain → Bukkit imports.**

Violations found:

| File | External import |
|---|---|
| `domain/entities/Claim.kt` | `org.bukkit.plugin.Plugin`, `org.bukkit.Bukkit`, `kotlin.concurrent.thread` |
| `domain/entities/VaultInventory.kt` | `org.bukkit.inventory.ItemStack` |
| `domain/entities/WriteBuffer.kt` | `org.bukkit.inventory.ItemStack` |
| `domain/entities/ViewerSession.kt` | `org.bukkit.entity.Player`, `org.bukkit.inventory.Inventory` |
| `domain/entities/PlayerState.kt` | `net.lumalyte.lg.application.services.scheduling.Task` |
| `domain/entities/Progression.kt` | `net.lumalyte.lg.application.services.ExperienceSource`, `PerkType` |
| `domain/entities/GuildBanner.kt` | `net.lumalyte.lg.application.services.BannerDesignData` |
| `domain/events/*.kt` (13 files) | `org.bukkit.event.Event`, `org.bukkit.event.HandlerList` |

The domain layer has **no imports from `infrastructure/`** — that direction is clean.
However, 7 entity/event files import from Bukkit (framework) and 2 import from `application/`,
violating the hexagonal architecture rule that domain must depend on nothing outward.
All 13 Bukkit event classes should be pure domain objects with Bukkit adapters at the infrastructure boundary.
