# Database Migration Safety System

## Overview

This document explains the multi-layer protection system that prevents database schema issues from breaking the plugin.

## The Problem We Solved

Previously, if a database migration failed silently (columns not added but version counter incremented), the plugin would crash with errors like:
```
[SQLITE_ERROR] SQL error or missing database (no such column: join_fee_enabled)
```

## The Solution: Multi-Layer Protection

### Layer 1: Migration Verification (New!)

**File:** `MigrationVerifier.kt`

After migrations run, the system automatically:
1. **Verifies** all expected columns exist in the `guilds` table
2. **Auto-repairs** if columns are missing by adding them
3. **Logs clearly** what was verified or repaired

**What you'll see in logs:**
```
✓ Database migrations completed (v15)
✓ Schema verification passed (23 columns verified)
```

Or if repair is needed:
```
⚠ Schema verification failed, attempting auto-repair...
  ✓ ALTER TABLE guilds... added column
✓ Schema auto-repair successful
✓ Schema verification passed (23 columns verified)
```

### Layer 2: Robust Column Checks (Improved!)

**File:** `GuildRepositorySQLite.kt`

- Uses `PRAGMA table_info()` instead of flaky SELECT queries
- **Cached** on startup (checked once, never re-checked)
- **Fallback SQL** used if columns don't exist

**Before:**
```kotlin
// Flaky - could be cached or fail silently
val hasLfgColumns = try {
    storage.connection.getResults("SELECT is_open FROM guilds LIMIT 1")
    true
} catch (e: SQLException) {
    false
}
```

**After:**
```kotlin
// Robust - uses PRAGMA and cached
private val hasLfgColumns: Boolean by lazy {
    checkColumnExists("guilds", "is_open") &&
    checkColumnExists("guilds", "join_fee_enabled") &&
    checkColumnExists("guilds", "join_fee_amount")
}
```

### Layer 3: Idempotent Migrations (Already Existed!)

All migrations check if columns/tables exist before creating:
```kotlin
if (!columnExists("guilds", "is_open")) {
    sqlCommands.add("ALTER TABLE guilds ADD COLUMN is_open INTEGER DEFAULT 0;")
}
```

This means migrations can be re-run safely without errors.

## How This Prevents Future Issues

### Scenario 1: Migration Fails Silently
**Before:** Plugin crashes at runtime when trying to use missing columns
**After:** Auto-repair detects and adds missing columns on startup

### Scenario 2: Database Gets Corrupted
**Before:** Manual SQL fixes required
**After:** Auto-repair system fixes it automatically

### Scenario 3: Adding New Columns in Future
**Steps to follow:**
1. Create migration (migrateToVersionXX)
2. Add column checks to MigrationVerifier
3. Increment schema version
4. Build and deploy

**The system will:**
- Run the migration
- Verify the columns were added
- Auto-repair if they're missing
- Log everything clearly

## Adding Future Migrations

### 1. Create Migration Function

In `SQLiteMigrations.kt`:
```kotlin
private fun migrateToVersion16() {
    val sqlCommands = mutableListOf<String>()

    // Always check if column exists first
    if (!columnExists("guilds", "new_column")) {
        sqlCommands.add("ALTER TABLE guilds ADD COLUMN new_column TEXT;")
    }

    if (sqlCommands.isNotEmpty()) {
        executeMigrationCommands(sqlCommands)
    }

    componentLogger.info(Component.text("✓ Added new_column to guilds table"))
}
```

### 2. Wire Up Migration

In the `migrate()` function:
```kotlin
if (dbVersion < 16) {
    migrateToVersion16()
    updateDatabaseVersion(16)
    dbVersion = 16
}
```

### 3. Add Verification

In `MigrationVerifier.kt`, add to `expectedColumns`:
```kotlin
"new_column" to "TEXT"
```

And to `autoRepairSchema()`:
```kotlin
if (!columnExists("new_column", "TEXT")) {
    repairs.add("ALTER TABLE guilds ADD COLUMN new_column TEXT;")
}
```

### 4. Update Repository (If Needed)

If the column is optional, update `hasLfgColumns` check:
```kotlin
private val hasNewFeature: Boolean by lazy {
    checkColumnExists("guilds", "new_column")
}
```

Then use it in SQL generation:
```kotlin
val sql = if (hasNewFeature) {
    // SQL with new column
} else {
    // Fallback SQL without new column
}
```

## Testing New Migrations

### Test 1: Fresh Database
1. Delete `lumaguilds.db`
2. Start server
3. Check logs for: `✓ Database migrations completed (v16)`
4. Check logs for: `✓ Schema verification passed`

### Test 2: Upgrade from Previous Version
1. Use database at v15
2. Start server with v16 code
3. Check logs for: `Current database schema version: v15`
4. Check logs for: `✓ Database migrations completed (v16)`
5. Check logs for: `✓ Schema verification passed`

### Test 3: Corrupted Database (Simulated)
1. Manually remove a column: `ALTER TABLE guilds DROP COLUMN new_column;`
2. Keep schema version at 16: `PRAGMA user_version = 16;`
3. Start server
4. Check logs for: `⚠ Schema verification failed, attempting auto-repair...`
5. Check logs for: `✓ Schema auto-repair successful`

## Maintenance

### When Adding Features That Need New Columns
1. Always check if you need backward compatibility
2. Use fallback SQL if older databases might not have the column
3. Add verification to MigrationVerifier
4. Test with both fresh and upgraded databases

### When Troubleshooting Schema Issues
1. Check server logs for verification/repair messages
2. If auto-repair failed, check the error message
3. Report persistent issues with full logs

### Emergency: Manual Column Addition
If auto-repair fails, you can manually add columns:

```sql
-- Connect to database
sqlite3 /path/to/lumaguilds.db

-- Add missing columns
ALTER TABLE guilds ADD COLUMN is_open INTEGER DEFAULT 0;
ALTER TABLE guilds ADD COLUMN join_fee_enabled INTEGER DEFAULT 0;
ALTER TABLE guilds ADD COLUMN join_fee_amount INTEGER DEFAULT 0;

-- Verify
PRAGMA table_info(guilds);

-- Exit
.quit
```

## Summary

**3 Layers of Protection:**
1. ✅ Auto-verification and repair after migrations
2. ✅ Robust column existence checks (PRAGMA-based, cached)
3. ✅ Idempotent migrations (safe to re-run)

**Result:** Database schema issues are caught and fixed automatically, preventing runtime crashes.
