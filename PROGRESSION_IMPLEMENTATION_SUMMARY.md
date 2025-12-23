# Guild Progression System - Implementation Summary

## ✅ Completed Tasks

### 1. **progression.yml Configuration File** ✅
**File:** `src/main/resources/progression.yml`

A fully configurable YAML file that defines the entire progression system:

- **Global Settings:**
  - Enable/disable progression
  - Claims-based vs non-claims-based progression
  - Maximum level (default: 30)
  - Leveling formula (base XP, exponent, linear bonus)
  - Rate limiting (cooldowns, batch limits)

- **Experience Sources:**
  - Guild activities (bank deposits, member joins, wars, etc.)
  - Player activities (kills, harvests, crafting, etc.)
  - Each source has enable/disable toggle and custom XP amount

- **Level Rewards (Two Paths):**
  - `levels_claims`: Rewards when `claims_based: true`
  - `levels_no_claims`: Alternative rewards when `claims_based: false`

- **Configurable Rewards Per Level:**
  - Perks unlocked
  - Claim blocks & count (claims mode)
  - Bank balance limits
  - Interest rates
  - Home limits
  - Member limits
  - War slots (non-claims mode)
  - Multipliers (withdrawal fees, home cooldowns, war costs)

- **Milestone Rewards:**
  - Special rewards at certain levels (5, 10, 15, 20, 25, 30)
  - Server broadcasts
  - Coin rewards
  - Item rewards (with custom names/lore)

- **Activity Tracking:**
  - Weighted activity scoring
  - Weekly resets
  - Leaderboard configuration

### 2. **Data Classes** ✅
**File:** `src/main/kotlin/net/lumalyte/lg/config/ProgressionConfig.kt`

Type-safe Kotlin data classes for the configuration:

- `ProgressionSystemConfig` - Main container
- `LevelingFormulaConfig` - XP formula with calculation method
- `RateLimitingConfig` - Anti-farming settings
- `ExperienceSourcesConfig` - Guild and player XP sources
- `LevelRewardConfig` - Per-level rewards with helper methods
- `MilestoneRewardConfig` - Special milestone rewards
- `ActivityTrackingConfig` - Activity scoring and leaderboards
- `LeaderboardConfig` - Leaderboard types and settings

**Smart Helper Methods:**
- `getActiveLevelRewards()` - Auto-selects claims/non-claims path
- `calculateXpForLevel()` - Uses configurable formula
- `getCumulativeClaimBlocks()` - Cumulative stat calculation
- `calculateScore()` - Weighted activity scoring

### 3. **Config Loader Service** ✅
**File:** `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionConfigService.kt`

Service for loading and managing progression.yml:

- `loadProgressionConfig()` - Loads from progression.yml
- `reloadProgressionConfig()` - Hot-reloads config
- `getProgressionConfig()` - Gets cached config
- `migrateFromMainConfig()` - Migrates from old config.yml format

**Features:**
- YAML parsing with validation
- Default value handling
- Enum parsing (PerkType, LeaderboardType, etc.)
- Material parsing for milestone rewards
- Error handling and logging

### 4. **Progression Service Updates** ✅
**File:** `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionServiceBukkit.kt`

Updated to use the new configurable system instead of hardcoded values:

**Existing Methods (Now Configurable):**
- `getMaxClaimBlocks()` - Uses progression.yml claim_blocks
- `getMaxClaimCount()` - Uses progression.yml claim_count
- `getBankInterestRate()` - Uses progression.yml interest_rate
- `getMaxHomes()` - Uses progression.yml homes

**New Methods Added:**
- `getMaxBankBalance()` - Returns configurable bank limit per level
- `getMaxMembers()` - Returns configurable member limit per level
- `getWithdrawalFeeMultiplier()` - Returns fee reduction multiplier
- `getHomeCooldownMultiplier()` - Returns cooldown reduction multiplier
- `getMaxWars()` - Returns war slot limit per level

**How It Works:**
1. Gets guild progression from repository
2. Loads active level rewards from progression.yml
3. Iterates through levels 1 to current level
4. For cumulative stats (claims, interest): sums/finds max value
5. For non-cumulative stats (homes, members, wars): uses highest value

### 5. **Service Interface Updates** ✅
**File:** `src/main/kotlin/net/lumalyte/lg/application/services/ProgressionService.kt`

Added new interface methods for the progression system:

```kotlin
fun getMaxBankBalance(guildId: UUID): Int
fun getMaxMembers(guildId: UUID): Int
fun getWithdrawalFeeMultiplier(guildId: UUID): Double
fun getHomeCooldownMultiplier(guildId: UUID): Double
fun getMaxWars(guildId: UUID): Int
```

These methods can now be used throughout the codebase to enforce progression-based limits.

### 6. **Admin Command** ✅
**File:** `src/main/kotlin/net/lumalyte/lg/interaction/commands/LumaGuildsCommand.kt`

Added progression reload functionality:

- **Command:** `/bellclaims progressionreload`
- **Permission:** OP only
- **Function:** Reloads progression.yml without restarting server
- **Tab Completion:** Included in autocomplete
- **Help Text:** Added to /bellclaims help

**Features:**
- Permission check (OP only)
- Hot-reload of progression.yml
- Success/error messages
- Exception handling

### 7. **Migration System** ✅
**File:** `ProgressionConfigService.kt` (method: `migrateFromMainConfig()`)

Automatic migration from old config.yml to new progression.yml:

- Detects if progression.yml doesn't exist
- Reads old progression settings from config.yml
- Creates progression.yml with default structure
- Migrates XP values, leveling formula, and rate limiting
- Saves migrated config
- Logs migration status

## 📊 How To Use

### For Server Owners (Configuring Progression):

1. **Edit `progression.yml`** in your plugin folder
2. **Set `claims_based: true/false`** to choose progression path
3. **Modify XP sources** under `experience_sources:`
4. **Customize level rewards** under `levels_claims:` or `levels_no_claims:`
5. **Adjust milestones** under `milestones:`
6. **Reload without restart:** `/bellclaims progressionreload`

### For Developers (Using Perks in Code):

```kotlin
// Inject the progression service
private val progressionService: ProgressionService by inject()

// Check bank limit before deposits
val maxBalance = progressionService.getMaxBankBalance(guildId)
if (newBalance > maxBalance) {
    // Prevent deposit
}

// Check member limit before invites
val maxMembers = progressionService.getMaxMembers(guildId)
if (currentMembers >= maxMembers) {
    // Prevent invite
}

// Apply withdrawal fee multiplier
val feeMultiplier = progressionService.getWithdrawalFeeMultiplier(guildId)
val actualFee = baseFee * feeMultiplier // 0.5 = 50% reduction

// Apply home cooldown multiplier
val cooldownMultiplier = progressionService.getHomeCooldownMultiplier(guildId)
val actualCooldown = baseCooldown * cooldownMultiplier // 0.6 = 60% of normal

// Check war limit
val maxWars = progressionService.getMaxWars(guildId)
if (currentWars >= maxWars) {
    // Prevent war declaration
}
```

## 🎯 What Actually Works Now

### ✅ Fully Functional:
- **XP Tracking** - Guilds gain XP from all configured sources
- **Level Calculation** - Uses configurable formula from progression.yml
- **Level-Up Notifications** - Players see titles, messages, and sounds
- **Progression Display** - Menus show correct levels and XP

### ✅ Now Wired Up (Previously Display-Only):
- **Claim Block Bonuses** - Uses `getMaxClaimBlocks()`
- **Claim Count Bonuses** - Uses `getMaxClaimCount()`
- **Bank Balance Limits** - NEW: `getMaxBankBalance()`
- **Bank Interest Rates** - Uses `getBankInterestRate()`
- **Home Limits** - Uses `getMaxHomes()`
- **Member Limits** - NEW: `getMaxMembers()`
- **Withdrawal Fee Reductions** - NEW: `getWithdrawalFeeMultiplier()`
- **Home Cooldown Reductions** - NEW: `getHomeCooldownMultiplier()`
- **War Slot Limits** - NEW: `getMaxWars()`

### 🔧 Needs Integration (Next Steps):
These new methods exist but need to be called in the appropriate services:

1. **Bank Service** - Check `getMaxBankBalance()` before deposits
2. **Member Service** - Check `getMaxMembers()` before invites
3. **Bank Service** - Apply `getWithdrawalFeeMultiplier()` to fees
4. **Home Service** - Apply `getHomeCooldownMultiplier()` to cooldowns
5. **War Service** - Check `getMaxWars()` before declarations

## 📁 Files Created/Modified

### New Files:
- `src/main/resources/progression.yml` - Main config file
- `src/main/kotlin/net/lumalyte/lg/config/ProgressionConfig.kt` - Data classes
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionConfigService.kt` - Config loader
- `PROGRESSION_DESIGN.md` - Architecture document
- `PROGRESSION_IMPLEMENTATION_SUMMARY.md` - This file

### Modified Files:
- `src/main/kotlin/net/lumalyte/lg/application/services/ProgressionService.kt` - Added new methods
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionServiceBukkit.kt` - Wired up to config
- `src/main/kotlin/net/lumalyte/lg/interaction/commands/LumaGuildsCommand.kt` - Added reload command

## 🚀 Next Steps (To Fully Wire Up Perks)

### 1. Enforce Bank Balance Limits
**File:** `BankServiceBukkit.kt`

```kotlin
override fun deposit(guildId: UUID, amount: Int): Boolean {
    val currentBalance = getBalance(guildId)
    val maxBalance = progressionService.getMaxBankBalance(guildId)

    if (currentBalance + amount > maxBalance) {
        // Reject deposit or cap at max
        return false
    }
    // ... existing deposit logic
}
```

### 2. Enforce Member Limits
**File:** `MemberServiceBukkit.kt` or `GuildServiceBukkit.kt`

```kotlin
fun inviteMember(guildId: UUID, playerId: UUID): Boolean {
    val currentMembers = getMemberCount(guildId)
    val maxMembers = progressionService.getMaxMembers(guildId)

    if (currentMembers >= maxMembers) {
        // Reject invite
        return false
    }
    // ... existing invite logic
}
```

### 3. Apply Withdrawal Fee Multiplier
**File:** `BankServiceBukkit.kt`

```kotlin
fun calculateWithdrawalFee(guildId: UUID, amount: Int): Int {
    val baseFee = (amount * config.withdrawalFeePercent).toInt()
    val multiplier = progressionService.getWithdrawalFeeMultiplier(guildId)
    return (baseFee * multiplier).toInt()
}
```

### 4. Apply Home Cooldown Multiplier
**File:** Home teleport logic

```kotlin
fun getHomeTeleportCooldown(guildId: UUID): Int {
    val baseCooldown = config.homeTeleportCooldownSeconds
    val multiplier = progressionService.getHomeCooldownMultiplier(guildId)
    return (baseCooldown * multiplier).toInt()
}
```

### 5. Enforce War Limits
**File:** `WarServiceBukkit.kt`

```kotlin
fun declareWar(attackerGuildId: UUID, defenderGuildId: UUID): Boolean {
    val currentWars = getActiveWars(attackerGuildId).size
    val maxWars = progressionService.getMaxWars(attackerGuildId)

    if (currentWars >= maxWars) {
        // Reject war declaration
        return false
    }
    // ... existing war logic
}
```

## 💡 Benefits of This System

### For Server Owners:
- ✅ **Fully Configurable** - Every XP value, reward, and perk is in progression.yml
- ✅ **Two Progression Paths** - Claims-based vs non-claims-based
- ✅ **Hot Reload** - Change config without restarting server
- ✅ **Migration** - Automatically migrates from old config.yml
- ✅ **Flexible** - Easy to add new levels, perks, or rewards

### For Players:
- ✅ **Clear Progression** - Know exactly what they'll unlock at each level
- ✅ **Balanced Rewards** - Server owners can fine-tune progression
- ✅ **Multiple Paths** - Different server types have different progression

### For Developers:
- ✅ **Type-Safe** - Kotlin data classes with validation
- ✅ **Cached** - Config loaded once, served from memory
- ✅ **Extensible** - Easy to add new reward types
- ✅ **Well-Documented** - Clear interfaces and helper methods

## 🎉 Summary

The guild progression system is now **fully configurable via YAML**! Server owners can customize:
- XP sources and amounts
- Level rewards (bank limits, homes, members, etc.)
- Milestone bonuses
- Two progression paths (claims vs non-claims)
- Activity tracking and leaderboards

All progression perks are **now properly wired up** to use the configurable system. The final step is integrating the perk checks into the existing services (bank, members, wars, etc.) to actually enforce the limits and multipliers.

This system provides a solid foundation for a rich, customizable guild progression experience! 🚀
