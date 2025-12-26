# Service Dependency Refactoring Plan

## Goal
Remove ALL service-to-service dependencies and make services depend ONLY on:
- Repositories (data access layer)
- ConfigService (stateless configuration)
- Infrastructure services (Plugin, CoroutineScope, etc.)

## Current Circular Dependency Chains

### Chain 1: Core Guild Services
```
GuildService → RankService → MemberService → ProgressionService → GuildService
GuildService → MemberService → ProgressionService → GuildService
GuildService → VaultService → RankService → MemberService
```

### Chain 2: War/Progression/Bank
```
WarService → BankService → ProgressionService → (nothing now, but used to depend on WarService)
WarProgressionCoordinator → WarService → WarProgressionCoordinator (ACTIVE CIRCULAR!)
```

### Chain 3: Party/Guild
```
GuildService → PartyService → GuildService
```

### Chain 4: Complex Multi-Service
```
LfgService → GuildService, MemberService, BankService, RankService, VaultService
ChatService → GuildService, MemberService, RelationService, PartyService, RankService
DailyWarCostsService → WarService, GuildService, BankService
ModeService → GuildService, MemberService, RelationService
```

## Refactoring Strategy

### Phase 1: Core Services (Breaking the main cycles)

#### 1.1 ProgressionService
**Current dependencies:**
- GuildService (to notify guild members of level up)

**What it actually needs:**
- MemberRepository.getByGuild(guildId) - to get guild members
- GuildRepository.getById(guildId) - to get guild info

**Changes:**
- Remove GuildService dependency
- Add MemberRepository dependency
- Add GuildRepository dependency
- Implement member notification logic directly

#### 1.2 MemberService
**Current dependencies:**
- ProgressionService (to award XP when member joins)

**What it actually needs:**
- ProgressionRepository.getGuildProgression(guildId)
- ProgressionRepository.save(progression)
- Direct XP calculation logic (can be moved to helper/util)

**Changes:**
- Remove ProgressionService dependency
- Add ProgressionRepository dependency
- Implement XP award logic directly (or create a helper class)

#### 1.3 BankService
**Current dependencies:**
- MemberService (to check permissions)
- ProgressionService (lazy, to get progression bonuses)

**What it actually needs:**
- MemberRepository.getByPlayerAndGuild(playerId, guildId)
- RankRepository.getById(rankId)
- ProgressionRepository.getGuildProgression(guildId)

**Changes:**
- Remove MemberService dependency
- Remove ProgressionService dependency
- Add MemberRepository, RankRepository, ProgressionRepository

#### 1.4 WarService
**Current dependencies:**
- BankService (lazy, to deduct war costs)
- WarProgressionCoordinator (to get max wars)

**What it actually needs:**
- BankRepository.getByGuild(guildId)
- BankRepository.save(bank)
- ProgressionRepository.getGuildProgression(guildId)
- Direct calculation of max wars based on progression level

**Changes:**
- Remove BankService dependency
- Remove WarProgressionCoordinator dependency (DELETE the coordinator entirely)
- Add BankRepository dependency
- Add ProgressionRepository dependency
- Add ProgressionConfigService dependency
- Implement bank deduction logic directly
- Implement max wars calculation directly

### Phase 2: Guild Hierarchy Services

#### 2.1 GuildService
**Current dependencies:**
- RankService
- MemberService
- PartyService (lazy)
- VaultService
- HologramService

**What it actually needs:**
- RankRepository
- MemberRepository
- PartyRepository
- VaultRepository
- Direct logic for rank/member operations

**Changes:**
- Remove ALL service dependencies except utility services
- Add repository dependencies
- Implement rank creation/management directly
- Implement member management directly
- For hologram service: This is infrastructure, keep it (it's just for display)
- For vault service: Need to analyze further

#### 2.2 RankService
**Current dependencies:**
- MemberService (to check permissions)

**What it actually needs:**
- MemberRepository.getByPlayerAndGuild(playerId, guildId)

**Changes:**
- Remove MemberService dependency
- Add MemberRepository dependency
- Implement permission checking directly

#### 2.3 RelationService
**Current dependencies:**
- MemberService (to check permissions)

**What it actually needs:**
- MemberRepository.getByPlayerAndGuild(playerId, guildId)
- RankRepository.getById(rankId)

**Changes:**
- Remove MemberService dependency
- Add MemberRepository and RankRepository

### Phase 3: Complex Multi-Service Dependencies

#### 3.1 ModeService
**Current dependencies:**
- GuildService
- MemberService
- RelationService

**What it actually needs:**
- GuildRepository
- MemberRepository
- RankRepository
- RelationRepository

**Changes:**
- Remove ALL service dependencies
- Add repository dependencies
- Implement guild/member/relation lookups directly

#### 3.2 ChatService
**Current dependencies:**
- MemberService, GuildService, RelationService, PartyService, RankService

**What it actually needs:**
- All the repositories: Member, Guild, Relation, Party, Rank, ChatSettings

**Changes:**
- Remove ALL service dependencies
- Add ALL repository dependencies
- This will be a large refactor

#### 3.3 LfgService
**Current dependencies:**
- GuildService, MemberService, PhysicalCurrencyService, VaultService, BankService, RankService

**What it actually needs:**
- Guild, Member, Vault, Bank, Rank repositories

**For PhysicalCurrencyService:**
- This is infrastructure/configuration, might be okay to keep
- OR: Move physical currency logic into the service itself

**Changes:**
- Remove service dependencies
- Add repository dependencies
- Consider: PhysicalCurrencyService might be okay as it's infrastructure

#### 3.4 PartyService
**Current dependencies:**
- MemberService
- GuildService

**What it actually needs:**
- MemberRepository
- GuildRepository

**Changes:**
- Remove service dependencies
- Add repository dependencies

#### 3.5 CombatService
**Current dependencies:**
- ModeService

**What it actually needs:**
- GuildRepository
- MemberRepository
- RelationRepository
- Mode checking logic

**Changes:**
- Remove ModeService dependency
- Add repositories
- Implement PvP checking logic directly

#### 3.6 DailyWarCostsService
**Current dependencies:**
- WarService, GuildService, BankService

**What it actually needs:**
- War data (currently in-memory in WarService)
- GuildRepository
- BankRepository

**Special consideration:**
- WarService stores wars in-memory (ConcurrentHashMap)
- Need to either:
  a) Add WarRepository and persist wars
  b) Expose wars from WarService via a read-only method
  c) Move war cost logic INTO WarService

**Recommendation:** Move daily war costs logic INTO WarService

### Phase 4: Vault Services

#### 4.1 GuildVaultService
**Current dependencies:**
- RankService

**What it actually needs:**
- RankRepository
- MemberRepository

**Changes:**
- Remove RankService
- Add repositories

#### 4.2 PhysicalCurrencyService
**Current dependencies:**
- GuildVaultService

**Analysis:**
- This might be acceptable if VaultService becomes repository-only
- OR: Inline the vault access logic

**Decision:** Re-evaluate after GuildVaultService is fixed

## Implementation Order

### Stage 1: Break Core Cycle (Critical)
1. WarService - remove coordinator and BankService
2. BankService - remove MemberService and ProgressionService
3. MemberService - remove ProgressionService
4. ProgressionService - remove GuildService

### Stage 2: Break Guild Hierarchy
5. RankService - remove MemberService
6. RelationService - remove MemberService
7. PartyService - remove MemberService and GuildService

### Stage 3: Fix Complex Services
8. ModeService - remove all services
9. CombatService - remove ModeService
10. GuildService - remove all services except infrastructure

### Stage 4: Fix Dependent Services
11. ChatService - remove all services
12. LfgService - remove all services
13. GuildVaultService - remove RankService
14. DailyWarCostsService - move into WarService or refactor

### Stage 5: Cleanup
15. Delete WarProgressionCoordinator (no longer needed)
16. Update Modules.kt to only inject repositories
17. Remove all KoinComponent implementations used for lazy service injection
18. Run tests

## Files to Modify

### Services (17 files)
1. `WarServiceBukkit.kt`
2. `BankServiceBukkit.kt`
3. `MemberServiceBukkit.kt`
4. `ProgressionServiceBukkit.kt`
5. `RankServiceBukkit.kt`
6. `RelationServiceBukkit.kt`
7. `PartyServiceBukkit.kt`
8. `ModeServiceBukkit.kt`
9. `CombatServiceBukkit.kt`
10. `GuildServiceBukkit.kt`
11. `ChatServiceBukkit.kt`
12. `LfgServiceBukkit.kt`
13. `GuildVaultServiceBukkit.kt`
14. `DailyWarCostsServiceBukkit.kt`
15. `PhysicalCurrencyServiceBukkit.kt`

### Files to Delete
1. `WarProgressionCoordinator.kt` (interface)
2. `WarProgressionCoordinatorBukkit.kt` (implementation)

### Configuration
1. `Modules.kt` - Update all service registrations

## Risk Assessment

### High Risk Changes
- **GuildService**: Many dependencies, core to system
- **ChatService**: 10 dependencies, complex logic
- **LfgService**: 8 dependencies, complex workflows

### Medium Risk Changes
- **ProgressionService**: Member notification logic
- **MemberService**: XP awarding logic
- **ModeService**: PvP logic

### Low Risk Changes
- **WarService**: Already has repository access
- **BankService**: Simple permission checks
- **RankService**: Straightforward permission checks

## Testing Strategy

After each stage:
1. Compile the project
2. Test basic functionality
3. Check for StackOverflowError
4. Verify services work correctly

## Estimated Effort

- **Stage 1**: 2-3 hours (critical path)
- **Stage 2**: 1-2 hours
- **Stage 3**: 2-3 hours (complex)
- **Stage 4**: 2-3 hours
- **Stage 5**: 30 minutes
- **Testing**: 1-2 hours

**Total**: 8-13 hours of work

## Success Criteria

1. ✅ No StackOverflowError on plugin load
2. ✅ All services compile successfully
3. ✅ No service depends on another service (except ConfigService and infrastructure)
4. ✅ Plugin loads and runs correctly
5. ✅ Core functionality works (guilds, wars, progression, banking)
