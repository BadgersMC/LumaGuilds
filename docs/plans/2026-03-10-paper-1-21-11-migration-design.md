# Paper 1.21.11 Migration Design

**Date:** 2026-03-10
**Author:** BadgersMC
**Target:** Enthusia server (single deployment, open-source for transparency)
**Scope:** Full modernisation — dep bump + deprecation cleanup + new API adoption

---

## Context

LumaGuilds currently compiles against `paper-api:1.21.8-R0.1-SNAPSHOT` and tests against `1.21.10-R0.1-SNAPSHOT`. The Enthusia server is moving to Paper 1.21.11, which introduces a substantial deprecated-for-removal list and new APIs (DataComponent system, Registry-based attribute lookups, MenuType, Key-based AttributeModifier). The third-party library surface (InventoryFramework, Geyser, Floodgate) must also be updated as these have 1.21.11-compatible releases.

---

## Approach

Three sequential commits on a feature branch off `main`, each independently buildable:

1. **Phase 1 — Dependency bump** — update all library versions, fix compile errors, keep tests green
2. **Phase 2 — Deprecation cleanup** — remove all deprecated API usages
3. **Phase 3 — New API adoption** — opt into DataComponent, Registry, Key-based patterns

---

## Phase 1: Dependency Bump

### build.gradle.kts

| Dependency | Current | Target |
|---|---|---|
| `io.papermc.paper:paper-api` (compileOnly) | `1.21.8-R0.1-SNAPSHOT` | `1.21.11-R0.1-SNAPSHOT` |
| `io.papermc.paper:paper-api` (testImplementation) | `1.21.10-R0.1-SNAPSHOT` | `1.21.11-R0.1-SNAPSHOT` |
| `org.mockbukkit.mockbukkit:mockbukkit-v1.21` | `4.98.0` | latest from Maven Central targeting 1.21.11 |
| `com.github.stefvanschie.inventoryframework:IF` | `0.11.3` | `0.11.6` |
| `org.geysermc.geyser:api` | `2.7.0-SNAPSHOT` | latest snapshot |
| `org.geysermc.floodgate:api` | `2.2.4-SNAPSHOT` | latest snapshot |
| `org.geysermc.cumulus:cumulus` | `2.0.0-SNAPSHOT` | latest snapshot |

### plugin.yml

```yaml
api-version: '1.21.4'   # was '1.21'
```

Using `1.21.4` is the correct string for all 1.21.x targets from 1.21.4 onwards. Paper will refuse to load the plugin on any server older than 1.21.4 — intentional for Enthusia.

### Plugin version

`1.1.1` → `2.0.0` — major bump reflecting the hard MC version requirement change.

### Compile gate

Run `./gradlew compileKotlin test` after bumping. Resolve any errors introduced by IF 0.11.6 API changes before committing Phase 1.

---

## Phase 2: Deprecation Cleanup

Sweep all files under `src/main/kotlin/` for the following patterns.

### InventoryView title API (`since 1.21.1`, `forRemoval`)

| Deprecated | Replacement |
|---|---|
| `getTitle(): String` | `title(): Component` |
| `getOriginalTitle(): String` | remove — title mutation is unsupported |
| `setTitle(String)` | remove — IF 0.11.6 handles titles via its own GUI constructors |

### AttributeInstance UUID-based modifiers

| Deprecated | Replacement |
|---|---|
| `getModifier(UUID)` | `getModifier(Key)` |
| `removeModifier(UUID)` | `removeModifier(Key)` |
| `AttributeModifier(UUID, String, double, ...)` | `AttributeModifier(Key, double, Operation, EquipmentSlotGroup)` |

### ItemStack deprecated methods

| Deprecated | Replacement |
|---|---|
| `ItemStack(Material)` constructor | `ItemStack.of(Material)` static factory |
| `setType(Material)` | `withType(Material)` — returns new instance, callers must reassign |
| `getRarity()` | `itemMeta.hasRarity()` + `itemMeta.getRarity()` |
| `getData()` / `setData(MaterialData)` | DataComponent equivalent (Phase 3) |

### ItemMeta deprecated key collections

| Deprecated | Replacement |
|---|---|
| `getDestroyableKeys()` / `setDestroyableKeys()` | `ItemAdventurePredicate` API |
| `getPlaceableKeys()` / `setPlaceableKeys()` | `ItemAdventurePredicate` API |

### Server.spigot() (`since 1.21.4`, `forRemoval`)

Any `Bukkit.getServer().spigot()` calls → remove or replace with Paper-native equivalent.

---

## Phase 3: New API Adoption

### DataComponent item construction

Extend the existing `CustomModelDataComponent` pattern already in `ToolItemServiceBukkit` to all remaining `ItemMeta` mutation sites:

- Verify `.name()` / `.lore()` extension functions set data via `DataComponentTypes.ITEM_NAME` / `DataComponentTypes.LORE`; migrate explicitly if not.
- Replace `UnsafeValues.serializeItem` / `deserializeItem` (deprecated for removal) with `ItemStack.serializeAsBytes()` / `ItemStack.deserializeBytes()` in `GuildVaultRepositorySQLite` and any other persistence sites.

### Registry-based attribute access

Replace `Attribute.valueOf(String)` with:

```kotlin
Bukkit.getRegistry(Attribute::class.java)
    .get(NamespacedKey.minecraft("generic.max_health"))
```

### Key-based AttributeModifier construction

All new modifier instances use `net.kyori.adventure.key.Key`:

```kotlin
AttributeModifier(
    Key.key("lumaguilds", "modifier_name"),
    value,
    AttributeModifier.Operation.ADD_NUMBER,
    EquipmentSlotGroup.ANY
)
```

### IF 0.11.6 MenuType integration

IF 0.11.6 uses the new `MenuType` registry internally — no direct call-site changes needed beyond the lib upgrade in Phase 1. Verify all `ChestGui`, `AnvilGui`, and `MerchantGui` instantiations compile and behave correctly after the bump.

---

## Success Criteria

- `./gradlew shadowJar` produces a clean build with zero deprecation warnings on 1.21.11
- `./gradlew test` passes with MockBukkit targeting 1.21.11
- Plugin loads and all features function on a local 1.21.11 Paper test server
- Zero uses of deprecated-for-removal Paper APIs remain in source
