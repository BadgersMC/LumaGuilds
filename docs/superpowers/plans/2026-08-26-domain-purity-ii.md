# Domain Purity II Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `domain/**` framework-free while preserving LumaGuilds' synchronous public Bukkit events and vault behavior.

**Architecture:** Public Bukkit events move one-to-one from `domain.events` to `api.events`; producers and listeners keep using Bukkit's synchronous event bus. The Bukkit-backed vault cache subsystem moves from domain/application into `infrastructure.vault`, while the framework-free `VaultBackupService` remains an application port. A file-level architecture contract enforces the forbidden imports documented in `docs/implementation.md`.

**Tech Stack:** Kotlin 2.0.0, JDK 21, Paper 1.21.11, Bukkit event API, Koin 4.0.2, JUnit Jupiter, kotlin-test, MockK, Gradle 8.5.

**Spec:** `docs/superpowers/specs/2026-08-26-domain-purity-ii-design.md`

## Global Constraints

- Implement REQ-045 and only LG-1001; do not add an application event bus or domain event hierarchy.
- Preserve all 17 event class names, constructor parameter order/types/nullability, payload properties, synchronous timing, non-cancellable behavior, and per-class `HandlerList` behavior.
- The accepted breaking change is package-only: `net.lumalyte.lg.domain.events.*` becomes `net.lumalyte.lg.api.events.*`.
- Preserve vault cache, dirty/retry, deletion-buffer, atomic gold, viewer timing, serialization, and synchronization behavior.
- Keep `VaultBackupService` and `VaultBackup` in `application.services`; remove only their unused framework/domain imports.
- The forbidden domain prefixes are exactly `org.bukkit`, `org.koin`, `co.aikar`, and `net.kyori`.
- Follow SPEAR in order: spec, failing test, minimum implementation, architecture check, refinement.
- Use `apply_patch` for text edits; preserve unrelated worktree changes.

---

### Task 1: Executable forbidden-import contract

**Files:**

- Modify: `docs/tasks.md:310-316`
- Modify: `docs/implementation.md:15-23`
- Modify: `src/test/kotlin/net/lumalyte/lg/architecture/LayerRulesTest.kt`

**Interfaces:**

- Consumes: `src/main/kotlin/net/lumalyte/lg/domain` as the production domain source root.
- Produces: `forbiddenDomainPrefixes: Set<String>` in `LayerRulesTest` and two tests that enforce source imports plus documentation parity.

- [ ] **Step 1: Mark LG-1001 in progress**

Change the task marker in `docs/tasks.md` from `[ ]` to `[~]` before implementation.

- [ ] **Step 2: Add the failing import and documentation contract tests**

Add these members to `LayerRulesTest`:

```kotlin
private val forbiddenDomainPrefixes = setOf(
    "org.bukkit",
    "org.koin",
    "co.aikar",
    "net.kyori"
)

@Test
fun `domain source imports no forbidden framework packages`() {
    val domainRoot = Path.of("src/main/kotlin/net/lumalyte/lg/domain")
    val violations = Files.walk(domainRoot).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .flatMap { path ->
                Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter { it.startsWith("import ") }
                    .map { path to it.removePrefix("import ").substringBefore(" as ") }
            }
            .filter { (_, imported) ->
                forbiddenDomainPrefixes.any { prefix ->
                    imported == prefix || imported.startsWith("$prefix.")
                }
            }
            .map { (path, imported) -> "$path imports $imported" }
            .sorted()
            .toList()
    }

    assertTrue(violations.isEmpty(), violations.joinToString("\n"))
}

@Test
fun `implementation guide lists the executable forbidden prefixes`() {
    val guide = Files.readString(Path.of("docs/implementation.md"))
    val documented = Regex("(?ms)forbidden:\\s*\\n((?:\\s+- [^\\n]+\\n?)+)")
        .find(guide)
        ?.groupValues
        ?.get(1)
        ?.lineSequence()
        ?.map { it.trim().removePrefix("- ") }
        ?.filter(String::isNotBlank)
        ?.toSet()

    assertEquals(forbiddenDomainPrefixes, documented)
}
```

Import `java.nio.file.Files`, `java.nio.file.Path`, `kotlin.test.assertEquals`, and `kotlin.test.assertTrue`.

- [ ] **Step 3: Run the tests and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "net.lumalyte.lg.architecture.LayerRulesTest" --no-daemon
```

Expected: FAIL listing the current Bukkit imports under `domain/events` and `domain/entities`, and/or failure because the documented list is empty.

- [ ] **Step 4: Populate the documented forbidden list**

Replace `forbidden: []` in `docs/implementation.md` with:

```yaml
forbidden:
  - org.bukkit
  - org.koin
  - co.aikar
  - net.kyori
```

Update the adjacent status text to state that the list is executable in `LayerRulesTest`; retain the note that the production violations are removed by LG-1001.

- [ ] **Step 5: Re-run and confirm the expected remaining RED state**

Run the Task 1 test command again.

Expected: the documentation-parity test passes, while the source-import test still fails on the 20 audited production files. Do not weaken the test.

- [ ] **Step 6: Commit the enforcement checkpoint**

```powershell
git add docs/tasks.md docs/implementation.md src/test/kotlin/net/lumalyte/lg/architecture/LayerRulesTest.kt
git commit -m "test(architecture): enforce domain purity"
```

The commit intentionally contains a failing architecture test; Tasks 2 and 3 make it green on the same feature branch.

---

### Task 2: Public Bukkit event API migration

**Files:**

- Move: all 17 files from `src/main/kotlin/net/lumalyte/lg/domain/events/` to `src/main/kotlin/net/lumalyte/lg/api/events/`
- Create: `src/test/kotlin/net/lumalyte/lg/api/events/GuildEventApiContractTest.kt`
- Modify: all production/test files importing `net.lumalyte.lg.domain.events.*`

**Interfaces:**

- Consumes: the 17 existing event constructors and payloads documented in the design spec's migration table.
- Produces: the same 17 simple class names under `net.lumalyte.lg.api.events`, each extending `org.bukkit.event.Event` and exposing its existing static/shared `HandlerList`.

- [ ] **Step 1: Add a compile-failing API package contract test**

Create `GuildEventApiContractTest.kt` in package `net.lumalyte.lg.api.events`. Import `org.bukkit.event.Cancellable`, `org.bukkit.event.Event`, `org.bukkit.event.HandlerList`, JUnit `Test`, and kotlin-test assertions. Define the complete class list:

```kotlin
private val eventTypes = listOf(
    GuildBankDepositEvent::class.java,
    GuildBannerChangedEvent::class.java,
    GuildBannerSetEvent::class.java,
    GuildCreatedEvent::class.java,
    GuildDisbandedEvent::class.java,
    GuildHomeSetEvent::class.java,
    GuildLeaderboardRankChangeEvent::class.java,
    GuildLevelUpEvent::class.java,
    GuildMemberJoinEvent::class.java,
    GuildMemberRemovedEvent::class.java,
    GuildOwnershipTransferEvent::class.java,
    GuildRelationChangeEvent::class.java,
    GuildTrackingChangedEvent::class.java,
    GuildVaultPlacedEvent::class.java,
    GuildWarDeclaredEvent::class.java,
    GuildWarEndEvent::class.java,
    GuildWarKillEvent::class.java
)

@Test
fun `all public guild events remain synchronous Bukkit events`() {
    eventTypes.forEach { eventType ->
        assertTrue(Event::class.java.isAssignableFrom(eventType), eventType.name)
        assertFalse(Cancellable::class.java.isAssignableFrom(eventType), eventType.name)
    }
}

@Test
fun `every public guild event exposes one shared handler list`() {
    eventTypes.forEach { eventType ->
        val staticHandlers = eventType.getMethod("getHandlerList").invoke(null)
        val instanceHandlers = eventType.getMethod("getHandlers")
        assertIs<HandlerList>(staticHandlers)
        assertEquals(HandlerList::class.java, instanceHandlers.returnType)
    }
}
```

Add this constructor-signature map and assertions:

```kotlin
private val constructorSignatures = mapOf(
    GuildBankDepositEvent::class.java to listOf(UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType),
    GuildBannerChangedEvent::class.java to listOf(UUID::class.java, Boolean::class.javaPrimitiveType),
    GuildBannerSetEvent::class.java to listOf(UUID::class.java, UUID::class.java),
    GuildCreatedEvent::class.java to listOf(Guild::class.java, UUID::class.java),
    GuildDisbandedEvent::class.java to listOf(Guild::class.java, Set::class.java, UUID::class.java),
    GuildHomeSetEvent::class.java to listOf(UUID::class.java, UUID::class.java),
    GuildLeaderboardRankChangeEvent::class.java to listOf(
        UUID::class.java,
        ExtendedLeaderboardType::class.java,
        LeaderboardPeriod::class.java,
        Int::class.javaObjectType,
        Int::class.javaPrimitiveType
    ),
    GuildLevelUpEvent::class.java to listOf(UUID::class.java, Int::class.javaPrimitiveType),
    GuildMemberJoinEvent::class.java to listOf(UUID::class.java, UUID::class.java),
    GuildMemberRemovedEvent::class.java to listOf(
        UUID::class.java,
        UUID::class.java,
        UUID::class.java,
        Boolean::class.javaPrimitiveType
    ),
    GuildOwnershipTransferEvent::class.java to listOf(UUID::class.java, UUID::class.java, UUID::class.java),
    GuildRelationChangeEvent::class.java to listOf(
        UUID::class.java,
        UUID::class.java,
        RelationType::class.java,
        Relation::class.java
    ),
    GuildTrackingChangedEvent::class.java to listOf(UUID::class.java, Boolean::class.javaPrimitiveType),
    GuildVaultPlacedEvent::class.java to listOf(UUID::class.java, UUID::class.java),
    GuildWarDeclaredEvent::class.java to listOf(UUID::class.java, UUID::class.java, UUID::class.java),
    GuildWarEndEvent::class.java to List(5) { UUID::class.java },
    GuildWarKillEvent::class.java to List(5) { UUID::class.java }
)

@Test
fun `public guild event constructor signatures remain compatible`() {
    constructorSignatures.forEach { (eventType, expected) ->
        assertEquals(expected, eventType.constructors.single().parameterTypes.toList(), eventType.name)
    }
}

@Test
fun `public guild events remain synchronous`() {
    val eventRoot = Path.of("src/main/kotlin/net/lumalyte/lg/api/events")
    Files.walk(eventRoot).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.toString().endsWith("Event.kt") }
            .forEach { path -> assertFalse(Files.readString(path).contains("Event(true)"), path.toString()) }
    }
}
```

Import the five referenced domain types, `java.nio.file.Files`, `java.nio.file.Path`, and `java.util.UUID`.

- [ ] **Step 2: Run the event contract and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "net.lumalyte.lg.api.events.GuildEventApiContractTest" --no-daemon
```

Expected: test compilation fails because `net.lumalyte.lg.api.events` does not yet contain the 17 classes.

- [ ] **Step 3: Move event files without changing behavior**

For each event in the spec migration table:

1. Create the same filename under `src/main/kotlin/net/lumalyte/lg/api/events/`.
2. Change only the package declaration to `net.lumalyte.lg.api.events`.
3. Preserve constructor, properties, KDoc, superclass, and handler list.
4. Delete the original `domain/events` file.

Do not consolidate handler lists or introduce a common event superclass.

- [ ] **Step 4: Update every producer and listener import**

Replace `net.lumalyte.lg.domain.events` with `net.lumalyte.lg.api.events` in these consumers:

```text
infrastructure/listeners/GuildChannelCreationListener.kt
infrastructure/listeners/GuildDisbandedListener.kt
infrastructure/listeners/GuildEmojiGrantListener.kt
infrastructure/listeners/ProgressionEventListener.kt
infrastructure/listeners/QuestProgressListener.kt
infrastructure/listeners/RoseChatCleanupListener.kt
infrastructure/listeners/WarKillTrackingListener.kt
infrastructure/listeners/apollo/GuildNotificationListener.kt
infrastructure/listeners/apollo/GuildRichPresenceListener.kt
infrastructure/listeners/apollo/GuildTeamListener.kt
infrastructure/services/GuildBannerServiceBukkit.kt
infrastructure/services/GuildServiceBukkit.kt
infrastructure/services/GuildVaultServiceBukkit.kt
infrastructure/services/LeaderboardServiceBukkit.kt
infrastructure/services/MemberServiceBukkit.kt
infrastructure/services/ProgressionServiceBukkit.kt
infrastructure/services/RelationServiceBukkit.kt
infrastructure/services/WarServiceBukkit.kt
```

Update any tests returned by:

```powershell
git grep -n "net.lumalyte.lg.domain.events" -- src/main src/test
```

The command must return no matches after the migration.

- [ ] **Step 5: Verify event API GREEN and unchanged dispatch sites**

Run:

```powershell
.\gradlew.bat test --tests "net.lumalyte.lg.api.events.GuildEventApiContractTest" --no-daemon
git grep -n "callEvent(" -- src/main/kotlin/net/lumalyte/lg/infrastructure
```

Expected: event contract passes; existing guild event dispatch calls remain synchronous and at their original operation sites.

- [ ] **Step 6: Run architecture test and record expected remaining violations**

Run:

```powershell
.\gradlew.bat test --tests "net.lumalyte.lg.architecture.LayerRulesTest" --no-daemon
```

Expected: event violations are gone; only the three Bukkit-backed vault state files remain under domain.

- [ ] **Step 7: Commit the event API migration**

```powershell
git add src/main/kotlin src/test/kotlin/net/lumalyte/lg/api
git commit -m "refactor(api)!: relocate guild Bukkit events" -m "BREAKING CHANGE: event imports move from net.lumalyte.lg.domain.events to net.lumalyte.lg.api.events. Constructors and runtime behavior are unchanged."
```

---

### Task 3: Infrastructure-owned vault subsystem

**Files:**

- Move: `src/main/kotlin/net/lumalyte/lg/domain/entities/VaultInventory.kt` to `src/main/kotlin/net/lumalyte/lg/infrastructure/vault/VaultInventory.kt`
- Move: `src/main/kotlin/net/lumalyte/lg/domain/entities/ViewerSession.kt` to `src/main/kotlin/net/lumalyte/lg/infrastructure/vault/ViewerSession.kt`
- Move: `src/main/kotlin/net/lumalyte/lg/domain/entities/WriteBuffer.kt` to `src/main/kotlin/net/lumalyte/lg/infrastructure/vault/WriteBuffer.kt`
- Move: `src/main/kotlin/net/lumalyte/lg/application/services/VaultInventoryManager.kt` to `src/main/kotlin/net/lumalyte/lg/infrastructure/vault/VaultInventoryManager.kt`
- Move: `src/main/kotlin/net/lumalyte/lg/application/services/VaultAutoSaveService.kt` to `src/main/kotlin/net/lumalyte/lg/infrastructure/vault/VaultAutoSaveService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/VaultBackupService.kt`
- Modify: vault consumers and `src/main/kotlin/net/lumalyte/lg/di/Modules.kt`
- Move tests: `VaultInventoryManagerSharedInventoryTest.kt`, `VaultInventorySyncTest.kt` to matching `infrastructure/vault` test package
- Create: `src/test/kotlin/net/lumalyte/lg/architecture/VaultOwnershipContractTest.kt`

**Interfaces:**

- Consumes: existing constructors and public methods of the five moved classes.
- Produces: identical types under `net.lumalyte.lg.infrastructure.vault`; `VaultBackupService` remains at `net.lumalyte.lg.application.services.VaultBackupService`.

- [ ] **Step 1: Add the failing ownership contract**

Create `VaultOwnershipContractTest.kt`:

```kotlin
package net.lumalyte.lg.architecture

import net.lumalyte.lg.infrastructure.vault.VaultAutoSaveService
import net.lumalyte.lg.infrastructure.vault.VaultInventory
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.infrastructure.vault.ViewerSession
import net.lumalyte.lg.infrastructure.vault.WriteBuffer
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse

class VaultOwnershipContractTest {
    @Test
    fun `Bukkit vault implementation types live in infrastructure`() {
        listOf(
            VaultInventory::class,
            ViewerSession::class,
            WriteBuffer::class,
            VaultInventoryManager::class,
            VaultAutoSaveService::class
        )
    }

    @Test
    fun `old vault implementation source paths remain absent`() {
        val oldPaths = listOf(
            "src/main/kotlin/net/lumalyte/lg/domain/entities/VaultInventory.kt",
            "src/main/kotlin/net/lumalyte/lg/domain/entities/ViewerSession.kt",
            "src/main/kotlin/net/lumalyte/lg/domain/entities/WriteBuffer.kt",
            "src/main/kotlin/net/lumalyte/lg/application/services/VaultInventoryManager.kt",
            "src/main/kotlin/net/lumalyte/lg/application/services/VaultAutoSaveService.kt"
        )
        oldPaths.forEach { assertFalse(Files.exists(Path.of(it)), it) }
    }
}
```

- [ ] **Step 2: Run the ownership test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "net.lumalyte.lg.architecture.VaultOwnershipContractTest" --no-daemon
```

Expected: compilation fails because `net.lumalyte.lg.infrastructure.vault` does not yet expose the five types.

- [ ] **Step 3: Move the five classes and preserve their bodies**

Move each listed file to `infrastructure/vault` and change its package declaration to:

```kotlin
package net.lumalyte.lg.infrastructure.vault
```

Do not change method bodies, visibility, defaults, concurrency primitives, or persistence behavior during the move.

- [ ] **Step 4: Keep the application backup port framework-free**

In `VaultBackupService.kt`, remove the unused imports:

```kotlin
import net.lumalyte.lg.domain.entities.VaultInventory
import org.bukkit.inventory.ItemStack
```

Keep `VaultBackupService`, `VaultBackup`, their method signatures, and their package unchanged.

- [ ] **Step 5: Update DI and production consumers**

Replace old `VaultInventoryManager` and `VaultAutoSaveService` imports/FQCNs with `net.lumalyte.lg.infrastructure.vault.*` in:

```text
LumaGuilds.kt
di/Modules.kt
infrastructure/listeners/PlayerSessionListener.kt
infrastructure/services/BankServiceBukkit.kt
infrastructure/services/GuildVaultServiceBukkit.kt
infrastructure/services/VaultBackupServiceBukkit.kt
interaction/inventory/VaultInventoryHolder.kt
interaction/listeners/VaultInventoryListener.kt
interaction/menus/guild/GoldDepositMenu.kt
interaction/menus/guild/GoldWithdrawMenu.kt
interaction/menus/guild/GuildBankMenu.kt
```

Because `VaultAutoSaveService` moves with the manager, no application class may import `net.lumalyte.lg.infrastructure.vault` after this step. Verify with:

```powershell
git grep -n "net.lumalyte.lg.infrastructure.vault" -- src/main/kotlin/net/lumalyte/lg/application
```

Expected: no matches.

- [ ] **Step 6: Move and update vault tests**

Move both manager tests into `src/test/kotlin/net/lumalyte/lg/infrastructure/vault/` and change their package to `net.lumalyte.lg.infrastructure.vault`:

```text
VaultInventoryManagerSharedInventoryTest.kt
VaultInventorySyncTest.kt
```

Preserve every existing assertion. Update `CommandLocalizationTest` only where the application backup port import is required; its import should remain unchanged.

- [ ] **Step 7: Run focused vault and ownership tests GREEN**

Run:

```powershell
.\gradlew.bat test --tests "net.lumalyte.lg.infrastructure.vault.*" --tests "net.lumalyte.lg.architecture.VaultOwnershipContractTest" --no-daemon
```

Expected: all moved vault behavior tests and the ownership contract pass.

- [ ] **Step 8: Run the complete architecture contract GREEN**

Run:

```powershell
.\gradlew.bat test --tests "net.lumalyte.lg.architecture.*" --no-daemon
```

Expected: zero forbidden domain imports, documentation parity passes, and all existing Konsist layer tests pass.

- [ ] **Step 9: Commit the vault ownership migration**

```powershell
git add src/main/kotlin src/test/kotlin
git commit -m "refactor(vault): move Bukkit state to infrastructure"
```

---

### Task 4: Documentation, task closeout, and full verification

**Files:**

- Modify: `docs/implementation.md`
- Modify: `docs/tasks.md`
- Modify: release-facing migration notes in `docs/implementation.md` under a new `Public Guild Event Migration` subsection

**Interfaces:**

- Consumes: the final `api.events` and `infrastructure.vault` packages.
- Produces: complete operator/developer migration documentation and LG-1001 evidence.

- [ ] **Step 1: Add the public event import migration table**

Copy the complete 17-row old→new package table from the approved spec into `docs/implementation.md`. State explicitly:

```text
Public guild events remain synchronous, non-cancellable Bukkit events with unchanged constructors and payload properties. External plugins must replace net.lumalyte.lg.domain.events imports with net.lumalyte.lg.api.events and recompile.
```

- [ ] **Step 2: Update the architecture status text**

Remove the old target-state warning and document that `LayerRulesTest` enforces both layer direction and the external forbidden-prefix list. Record the audited migration count as 17 events plus 3 former domain state files.

- [ ] **Step 3: Mark LG-1001 complete with evidence**

Change `[~]` to `[x]` in `docs/tasks.md` and fill Evidence with:

```text
LayerRulesTest enforces the documented forbidden prefixes; 17 Bukkit events moved one-to-one to api.events with API contract coverage; the Bukkit vault cache subsystem moved to infrastructure.vault while VaultBackupService remains a pure application port. Full architecture and repository test suites are GREEN.
```

Update Files to cite `api/events/*`, `infrastructure/vault/*`, `LayerRulesTest`, and `docs/implementation.md`.

- [ ] **Step 4: Verify no stale packages or forbidden imports remain**

Run:

```powershell
git grep -n "net.lumalyte.lg.domain.events" -- src/main src/test
git grep -n -E "^import (org\.bukkit|org\.koin|co\.aikar|net\.kyori)" -- src/main/kotlin/net/lumalyte/lg/domain
git grep -n "net.lumalyte.lg.application.services.VaultInventoryManager\|net.lumalyte.lg.application.services.VaultAutoSaveService" -- src/main src/test
```

Expected: all three commands return no matches.

- [ ] **Step 5: Run clean full verification**

Ensure ignored local development jars required by the build are present, then run:

```powershell
git diff --check
.\gradlew.bat clean test --no-daemon
npx --yes markdownlint-cli2@0.13.0 "docs/**/*.md" --config .markdownlint.jsonc
```

Expected: diff check clean, Gradle build successful with zero failed tests, and markdownlint reports zero errors.

- [ ] **Step 6: Request code review and resolve findings**

Review against REQ-045 and the approved spec. Required review focus:

```text
- public event payload/dispatch compatibility
- no cancellability or async behavior changes
- no application-to-infrastructure dependency
- vault concurrency/persistence behavior unchanged
- executable forbidden list cannot drift from docs
- complete external-plugin migration table
```

For each valid bug finding, add a failing regression test, verify RED, apply the narrow fix, and rerun focused tests before the full suite.

- [ ] **Step 7: Commit final documentation and closeout**

```powershell
git add docs/tasks.md docs/implementation.md
git commit -m "docs(architecture): close domain purity migration"
```

- [ ] **Step 8: Push and open PR-10**

```powershell
git push -u origin codex/pr10-domain-purity
$testCount = 0
Get-ChildItem build/test-results/test -Filter 'TEST-*.xml' | ForEach-Object {
    $xml = [xml](Get-Content $_.FullName)
    $testCount += [int]$xml.testsuite.tests
}
$body = "PR-10 implements REQ-045: 17 public Bukkit events move to api.events, the Bukkit vault cache moves to infrastructure.vault, and LayerRulesTest enforces the documented forbidden domain imports. See docs/implementation.md for the external-plugin import migration table. Verification: $testCount tests passed with zero failures."
gh pr create --repo BadgersMC/LumaGuilds --base main --head codex/pr10-domain-purity --title "PR-10: enforce Bukkit-free domain" --body $body
```

The final PR body summarizes the accepted event import break, vault ownership move, executable architecture contract, exact test count, and migration-table location.
