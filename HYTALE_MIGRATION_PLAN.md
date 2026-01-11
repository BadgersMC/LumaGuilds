# LumaGuilds - Hytale Migration Plan

## Executive Summary

LumaGuilds is architected using onion/port-and-adapter architecture, which provides excellent separation between business logic and platform-specific code. This enables a clean migration path to Hytale.

**🚨 UPDATE 2026-01-11: HYTALE API INTEL CONFIRMED**

**Critical Findings:**
- ✅ **Hytale server is Java-based** (not C++) - Direct compatibility with our Kotlin codebase
- ✅ **"Shared Source" model** - Read access to server code for API understanding
- ✅ **ECS architecture** (Flecs) - Aligns perfectly with our data-centric domain design
- ✅ **JSON-driven configs** - Easy migration from YAML

**REVISED Estimated Effort Distribution:**
- **70-80% reusable** - Domain, application, database layers (no changes needed)
- **20-30% platform-specific** - Infrastructure implementations, ECS adapters (needs Hytale versions)

**REVISED Timeline:** 3-5 months (reduced from 4.5-6.5 months)

**See `HYTALE_API_INTEL.md` for detailed analysis.**

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    INTERACTION LAYER                        │
│  Commands, Event Listeners, Menus (Bukkit/Hytale specific) │
│                     [NEEDS REPLACEMENT]                     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                  INFRASTRUCTURE LAYER                       │
│    Service Implementations, Adapters, Repository Impls      │
│         [PARTIALLY REPLACE - Service impls only]            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   APPLICATION LAYER                         │
│      Use Cases, Service Interfaces, Repository Ports        │
│                        [KEEP 100%]                          │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     DOMAIN LAYER                            │
│         Entities, Value Objects, Business Rules             │
│                        [KEEP 100%]                          │
└─────────────────────────────────────────────────────────────┘
```

## Migration Plan

### Phase 1: Preparation & Branch Setup

**Objective:** Create parallel development structure without disrupting Bukkit version

**Tasks:**
1. **Create feature branch** `feature/hytale-migration` from `main`
2. **Version strategy decision:**
   - Option A: Maintain separate versions (v2.0.0-bukkit, v2.0.0-hytale)
   - Option B: Unified build with platform detection
   - **Recommendation:** Separate artifacts initially, merge if Hytale supports Bukkit plugin API
3. **Update build configuration**
   - Add build variants for Bukkit vs Hytale
   - Set up conditional compilation if needed
4. **Document Hytale API unknowns** - Create research tasks for Hytale API exploration

**Duration:** 1-2 weeks (after Hytale modding API release)

---

### Phase 2: Infrastructure Layer Replacement

**Objective:** Implement Hytale versions of service interfaces

#### 2.1 Core Service Implementations

**Replace these Bukkit implementations with Hytale equivalents:**

| Bukkit Class | Interface | Hytale Class | Priority | Complexity |
|--------------|-----------|--------------|----------|------------|
| `GuildServiceBukkit` | `GuildService` | `GuildServiceHytale` | High | Medium |
| `VisualisationServiceBukkit` | `VisualisationService` | `VisualisationServiceHytale` | High | Medium |
| `SchedulerServiceBukkit` | `SchedulerService` | `SchedulerServiceHytale` | High | Low |
| `ConfigServiceBukkit` | `ConfigService` | `ConfigServiceHytale` | High | Low |
| `WorldManipulationServiceBukkit` | `WorldManipulationService` | `WorldManipulationServiceHytale` | High | Medium |
| `PlayerLocaleServiceBukkit` | `PlayerLocaleService` | `PlayerLocaleServiceHytale` | Medium | Low |
| `PlayerMetadataServiceBukkit` | `PlayerMetadataService` | `PlayerMetadataServiceHytale` | Medium | Medium |
| `ToolItemServiceBukkit` | `ToolItemService` | `ToolItemServiceHytale` | Medium | Medium |
| `MapRendererServiceBukkit` | `MapRendererService` | `MapRendererServiceHytale` | Low | High |
| `TeleportationServiceBukkit` | `TeleportationService` | `TeleportationServiceHytale` | Medium | Low |

**Structure:**
```
src/main/kotlin/net/lumalyte/lg/infrastructure/
├── bukkit/              # Existing Bukkit implementations
│   ├── services/
│   ├── adapters/
│   └── listeners/
└── hytale/              # NEW - Hytale implementations
    ├── services/
    ├── adapters/
    └── listeners/
```

#### 2.2 Type Adapters

**Create Hytale equivalents:**

| Current | Purpose | Hytale Equivalent |
|---------|---------|-------------------|
| `BukkitLocationAdapter` | Position ↔ Bukkit Location | `HytaleLocationAdapter` |
| `BukkitItemStackAdapter` | ItemStack conversion | `HytaleItemAdapter` |

**Extension functions to create:**
```kotlin
// Domain Position types to Hytale types
fun Position2D.toHytaleLocation(world: HytaleWorld): HytaleLocation
fun Position3D.toHytaleLocation(world: HytaleWorld): HytaleLocation
fun HytaleLocation.toPosition2D(): Position2D
fun HytaleLocation.toPosition3D(): Position3D
```

#### 2.3 Database Layer

**✅ KEEP AS-IS** - No changes needed!

- All repository implementations (`*RepositorySQLite`, `*RepositoryMariaDB`)
- Storage abstractions (SQLite, MariaDB, VirtualThread variants)
- Database migrations
- IDB/HikariCP integration

**Reasoning:** Database layer is already platform-agnostic, uses only JDBC

---

### Phase 3: Interaction Layer Replacement

**Objective:** Adapt user-facing layer to Hytale APIs

#### 3.1 Commands

**Current:** ACF (Aikar's Command Framework) - Bukkit-specific
**Replace with:** Hytale command API (TBD based on Hytale modding API)

**Commands to port:**
- `GuildCommand` - Main guild management (12 subcommands)
- `ClaimCommand` - Claim management (6 subcommands)
- `PartyCommand` - Party system
- `AllyCommand` - Alliance management
- `WarCommand` - War system
- `ApolloDebugCommand` - Debug utilities (adapt or remove)

**Pattern:**
```kotlin
// Before (Bukkit + ACF)
@CommandAlias("guild")
class GuildCommand @Inject constructor(
    private val guildService: GuildService
) : BaseCommand() {
    @Subcommand("create")
    fun onCreate(player: Player, name: String) {
        guildService.createGuild(name, player.uniqueId)
    }
}

// After (Hytale - API TBD)
@HytaleCommand("guild")
class GuildCommand @Inject constructor(
    private val guildService: GuildService // Same service!
) {
    @Subcommand("create")
    fun onCreate(player: HytalePlayer, name: String) {
        guildService.createGuild(name, player.uuid) // Same call!
    }
}
```

**Key Point:** Business logic in `guildService` is unchanged!

#### 3.2 Event Listeners

**Bukkit → Hytale event system**

**Listeners to port:**

| Category | Bukkit Listener | Hytale Listener | Priority |
|----------|----------------|-----------------|----------|
| Claims | `PlayerClaimProtectionListener` | `HytaleClaimProtectionListener` | High |
| Guild | `GuildChannelCreationListener` | `HytaleGuildChannelListener` | Medium |
| Social | `PartyChatListener` | `HytalePartyChatListener` | Medium |
| Economy | `VaultInventoryListener` | `HytaleVaultInventoryListener` | Medium |
| Progression | `ProgressionEventListener` | `HytaleProgressionEventListener` | Low |
| Integrations | `ApolloEventListener` | (Remove or adapt for Hytale client mods) | Low |

**Pattern:**
```kotlin
// Before (Bukkit)
class PlayerClaimProtectionListener @Inject constructor(
    private val claimService: ClaimService
) : Listener {
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val position = event.block.location.toPosition3D()
        if (!claimService.canModifyBlock(event.player.uniqueId, position)) {
            event.isCancelled = true
        }
    }
}

// After (Hytale - API TBD)
class HytaleClaimProtectionListener @Inject constructor(
    private val claimService: ClaimService // Same service!
) : HytaleEventListener {
    @Subscribe
    fun onBlockBreak(event: HytaleBlockBreakEvent) {
        val position = event.block.position.toPosition3D()
        if (!claimService.canModifyBlock(event.player.uuid, position)) {
            event.cancel()
        }
    }
}
```

#### 3.3 Menus (Inventory GUIs)

**Current:** InventoryFramework - Bukkit chest GUIs
**Replace with:** Hytale UI system (API TBD)

**Menus to port:**
- Guild management menus (`GuildMainMenu`, `GuildMembersMenu`, etc.)
- Claim selection menus
- Rank management
- Vault inventory
- Party menus
- War progression UI

**Strategy:**
- Wait for Hytale UI API documentation
- Assess if Hytale supports custom UI elements beyond inventory
- Consider native Hytale UI if more advanced than Bukkit chests
- Possibly upgrade UX with Hytale's UI capabilities

---

### Phase 4: Dependency Migration

**Objective:** Replace or remove Bukkit-specific dependencies

#### 4.1 Keep (Platform-Agnostic)

✅ **No changes needed:**
- Kotlin stdlib
- Koin (DI framework)
- HikariCP (connection pooling)
- MariaDB/SQLite JDBC drivers
- Kotlinx Coroutines
- SLF4J logging
- IDB (database library)
- JSON parsing

#### 4.2 Replace

| Bukkit Dependency | Purpose | Hytale Alternative | Action |
|-------------------|---------|-------------------|--------|
| `paper-api` | Server API | Hytale Modding API | Replace |
| `acf-paper` | Commands | Hytale command API | Replace |
| `inventoryframework` | Inventory GUIs | Hytale UI API | Replace |
| `Vault` | Economy/Permissions | Hytale economy API (if exists) | Replace or remove |
| `PlaceholderAPI` | Placeholder expansion | Hytale equivalent or custom | Replace or remove |
| `apollo-api` | Lunar Client integration | Hytale client mod API | Adapt or remove |
| `Floodgate` | Bedrock support | Hytale cross-platform (if applicable) | Evaluate |

#### 4.3 Build Configuration

**Update `build.gradle.kts`:**

```kotlin
// Option 1: Build variants
val platformType = project.findProperty("platform") ?: "bukkit"

dependencies {
    when (platformType) {
        "bukkit" -> {
            compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
            implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
            // ... other Bukkit deps
        }
        "hytale" -> {
            compileOnly("com.hytale:hytale-api:x.x.x") // TBD
            // ... other Hytale deps
        }
    }
}

// Option 2: Separate modules (cleaner)
// Create subprojects: lumaguilds-bukkit, lumaguilds-hytale, lumaguilds-core
```

---

### Phase 5: Dependency Injection Update

**Objective:** Bind Hytale implementations in Koin

**Current:** `di/Modules.kt` (686 lines)

**Strategy:**
```kotlin
// Create platform-specific module loaders

// bukkit/BukkitModules.kt
fun bukkitCoreModule() = module {
    single<GuildService> { GuildServiceBukkit(...) }
    single<VisualisationService> { VisualisationServiceBukkit(...) }
    // ... all Bukkit bindings
}

// hytale/HytaleModules.kt
fun hytaleCoreModule() = module {
    single<GuildService> { GuildServiceHytale(...) }
    single<VisualisationService> { VisualisationServiceHytale(...) }
    // ... all Hytale bindings
}

// Main plugin initialization
fun loadModules(platform: Platform) {
    when (platform) {
        Platform.BUKKIT -> startKoin { modules(bukkitCoreModule(), ...) }
        Platform.HYTALE -> startKoin { modules(hytaleCoreModule(), ...) }
    }
}
```

---

### Phase 6: Main Plugin Class

**Objective:** Create Hytale plugin lifecycle handler

**Current:** `LumaGuilds.kt` extends `JavaPlugin` (Bukkit)

**Create:** `LumaGuildsHytale.kt` with Hytale plugin lifecycle

```kotlin
// bukkit/LumaGuilds.kt
class LumaGuilds : JavaPlugin() {
    override fun onEnable() {
        loadModules(Platform.BUKKIT)
        // Register Bukkit commands
        // Register Bukkit listeners
    }
}

// hytale/LumaGuildsHytale.kt
@HytaleMod(id = "lumaguilds", version = "2.0.0")
class LumaGuildsHytale {
    @OnEnable
    fun onEnable() {
        loadModules(Platform.HYTALE)
        // Register Hytale commands
        // Register Hytale event listeners
    }
}
```

---

### Phase 7: Configuration & Resources

**Objective:** Adapt plugin metadata and configs

#### 7.1 Plugin Metadata

**Current:** `plugin.yml` (Bukkit)
**Create:** `hytale-mod.json` or equivalent (Hytale's format TBD)

**Maintain config structure:**
- Config files (`config.yml`, `locales/`) can likely stay YAML
- Only change plugin descriptor format

#### 7.2 Database Migrations

**✅ No changes needed** - Migrations are platform-agnostic

**Keep:**
- `resources/migrations/` - All SQL migrations
- Migration versioning system
- Schema is identical across platforms

---

### Phase 8: Testing Strategy

**Objective:** Ensure Hytale implementation works correctly

#### 8.1 Unit Tests

**✅ Existing tests mostly reusable!**

- Domain layer tests: 100% reusable (no platform dependencies)
- Application layer tests: 100% reusable (mock interfaces)
- Repository tests: 100% reusable (test against real DB)

**New tests needed:**
- Hytale service implementations
- Hytale adapters (Location, ItemStack conversions)

#### 8.2 Integration Tests

**Create Hytale-specific:**
- Command execution tests
- Event listener tests
- UI interaction tests (if Hytale supports automated testing)

#### 8.3 Test Server

**Setup:**
- Hytale test server with LumaGuilds installed
- Test all features manually
- Performance benchmarks (compare to Bukkit version)

---

### Phase 9: Feature Parity Checklist

**Objective:** Ensure all features work on Hytale

| Feature | Bukkit | Hytale | Notes |
|---------|--------|--------|-------|
| **Core Guilds** |
| Guild creation/deletion | ✅ | ⬜ | |
| Member management | ✅ | ⬜ | |
| Rank system | ✅ | ⬜ | |
| Permissions | ✅ | ⬜ | |
| **Claims** |
| Claim creation | ✅ | ⬜ | Optional module |
| Claim visualization | ✅ | ⬜ | May be better in Hytale! |
| Claim protection | ✅ | ⬜ | |
| Partitions | ✅ | ⬜ | |
| **Social** |
| Party system | ✅ | ⬜ | |
| Chat routing | ✅ | ⬜ | |
| Alliances | ✅ | ⬜ | |
| **Progression** |
| Guild XP | ✅ | ⬜ | |
| Wars | ✅ | ⬜ | |
| Combat tracking | ✅ | ⬜ | |
| **Economy** |
| Guild bank | ✅ | ⬜ | |
| Shop system | ✅ | ⬜ | |
| **Utilities** |
| Teleportation | ✅ | ⬜ | |
| Guild home | ✅ | ⬜ | |
| Vault storage | ✅ | ⬜ | |
| Map rendering | ✅ | ⬜ | |
| **Integrations** |
| PlaceholderAPI | ✅ | ⬜ | May not be available |
| Apollo/Lunar Client | ✅ | ⬜ | Adapt for Hytale mods |
| Vault (economy) | ✅ | ⬜ | Replace with Hytale API |
| Floodgate (Bedrock) | ✅ | ⬜ | Evaluate need |

---

## Migration Risks & Mitigation

### Risk 1: Hytale API Limitations
**Risk:** Hytale may not support all features Bukkit does
**Mitigation:**
- Wait for full Hytale modding API documentation before starting
- Identify missing capabilities early
- Design fallbacks or alternative approaches
- Communicate feature differences to users

### Risk 2: Hytale API Instability
**Risk:** Early Hytale modding API may have breaking changes
**Mitigation:**
- Maintain Bukkit version as stable release
- Mark Hytale version as beta initially
- Version lock Hytale API dependencies
- Prepare for API migration updates

### Risk 3: Performance Differences
**Risk:** Hytale may have different performance characteristics
**Mitigation:**
- Benchmark early and often
- Profile database queries on Hytale
- Test with large guilds (100+ members, 1000+ claims)
- Optimize hot paths

### Risk 4: Database Compatibility
**Risk:** Hytale server environment may restrict database access
**Mitigation:**
- Test SQLite mode first (file-based, no server needed)
- Verify JDBC drivers work in Hytale mod context
- Fallback to flat file storage if needed (not ideal)

### Risk 5: UI/UX Expectations
**Risk:** Players may expect modern UI, not Bukkit-style chests
**Mitigation:**
- Leverage Hytale's UI capabilities for improved UX
- Consider custom UI elements if supported
- Survey community for UI preferences

---

## Timeline & Milestones

**Assumes Hytale modding API is available and documented**

### Milestone 1: Research & Planning (2-4 weeks)
- Study Hytale modding API documentation
- Prototype basic plugin (hello world)
- Test dependency compatibility
- Finalize technical approach

### Milestone 2: Infrastructure Layer (4-6 weeks)
- Implement core service interfaces
- Create type adapters
- Test database integration
- Basic Hytale plugin loads

### Milestone 3: Interaction Layer (4-6 weeks)
- Port command handlers
- Port event listeners
- Create basic UI/menus
- Alpha testing with small feature set

### Milestone 4: Feature Parity (6-8 weeks)
- Port all features
- Integration testing
- Performance optimization
- Beta testing with community

### Milestone 5: Release (2-3 weeks)
- Final bug fixes
- Documentation updates
- Migration guide for server owners
- Public release

**Total Estimated Timeline:** 18-27 weeks (4.5-6.5 months)

**Note:** Timeline heavily dependent on Hytale API stability and feature completeness

---

## Success Criteria

**Migration is successful when:**

1. ✅ All core features work on Hytale
2. ✅ Database migrations preserve existing data
3. ✅ Performance is equal to or better than Bukkit version
4. ✅ No data loss during platform migration
5. ✅ Configuration files are backward compatible
6. ✅ Documentation is updated
7. ✅ Community testing is positive

---

## Versioning Strategy for Migration

**Recommended:** Bump to **v2.0.0** for Hytale release

**Reasoning:**
- Major architectural change (platform switch)
- Potential breaking changes in features/config
- Clear signal to users this is a major release

**Version Schema:**
- `v1.x.x` - Bukkit versions (continue maintenance)
- `v2.0.0-hytale-beta` - Hytale beta
- `v2.0.0` - Hytale stable release
- `v2.x.x` - Future Hytale versions

**Branch Strategy:**
- `main` - Bukkit stable releases
- `hytale-dev` - Hytale development
- `hytale-main` - Hytale stable releases (after v2.0.0)

---

## Reusable Components Summary

### 100% Reusable (No Changes)

**Domain Layer:**
- All entity classes (Guild, Claim, Member, Rank, Party, War, etc.)
- All value objects (Position, Area, ClaimPermission, Flag, etc.)
- All domain events
- All domain exceptions
- Business validation logic

**Application Layer:**
- All use case classes (CreateClaim, DeleteGuild, etc.)
- All service interfaces (GuildService, ClaimService, etc.)
- All repository interfaces
- Application-level DTOs

**Infrastructure - Database:**
- All repository implementations (SQLite, MariaDB)
- All database migrations
- Storage abstractions
- Connection pooling

**Configuration:**
- Config data classes
- Locale files (YAML)
- Config structure

**Estimated:** ~400-500 files (60-70% of codebase)

### Needs Hytale Implementation (30-40%)

**Infrastructure - Services:**
- ~10-15 service implementations
- ~5-10 adapter classes
- ~5-10 infrastructure listeners

**Interaction Layer:**
- ~5-8 command classes
- ~10-15 event listener classes
- ~20-30 menu/UI classes

**Main Plugin:**
- 1 plugin main class
- 1 plugin descriptor file

**Build Configuration:**
- 1 build.gradle.kts update
- Dependencies adjustment

**Estimated:** ~100-150 files (30-40% of codebase)

---

## Next Steps

1. **Wait for Hytale modding API release and documentation**
2. **Create tracking issue:** "Hytale Platform Migration"
3. **Set up monitoring** for Hytale modding API announcements
4. **Prototype exploration** when API becomes available
5. **Create feature branch** when ready to begin migration
6. **Community communication** about Hytale support plans

---

## Resources & Research Needed

**When Hytale releases modding API, research:**

- [ ] Hytale plugin/mod structure and lifecycle
- [ ] Hytale command registration system
- [ ] Hytale event system and available events
- [ ] Hytale UI/GUI capabilities
- [ ] Hytale world/block manipulation APIs
- [ ] Hytale player/entity APIs
- [ ] Hytale persistence/storage options
- [ ] Hytale task scheduler APIs
- [ ] Hytale economy APIs (if any)
- [ ] Hytale permission system
- [ ] Hytale client modding capabilities
- [ ] Hytale server configuration options

---

## Conclusion

LumaGuilds' clean architecture makes this migration **highly feasible**. The majority of business logic, database code, and application layer is platform-agnostic and requires zero changes.

The migration effort focuses on creating Hytale-specific implementations of existing interfaces, which is exactly what the port-and-adapter architecture was designed for.

**Key Advantages:**
- ✅ No rewrite needed - just new implementations
- ✅ Business logic is preserved and tested
- ✅ Database layer is platform-agnostic
- ✅ Modular design allows incremental migration
- ✅ Dependency injection makes swapping implementations trivial

**Recommendation:** Proceed with migration once Hytale modding API is stable and well-documented.
