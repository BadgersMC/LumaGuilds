# LumaGuilds Database Schema Setup

This document explains how to set up the LumaGuilds database using the provided schema file.

## Quick Start (MariaDB)

### 1. Create Database

```bash
mysql -u root -p
```

```sql
CREATE DATABASE s97222_lumaguilds CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON s97222_lumaguilds.* TO 'your_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 2. Import Schema

```bash
mysql -u root -p s97222_lumaguilds < schema.sql
```

Or from within MySQL:

```sql
USE s97222_lumaguilds;
SOURCE /path/to/schema.sql;
```

### 3. Verify Tables

```sql
USE s97222_lumaguilds;
SHOW TABLES;
```

You should see 35 tables created.

### 4. Configure Plugin

Update `config.yml`:

```yaml
database_type: mariadb

mariadb:
  host: localhost
  port: 3306
  database: s97222_lumaguilds
  username: your_user
  password: your_password
```

## Schema Overview

### Core Guild Tables (7 tables)
- `guilds` - Main guild data with vault location and LFG settings
- `ranks` - Guild ranks and permissions
- `members` - Guild membership records
- `relations` - Guild alliances, wars, truces
- `guild_invitations` - Pending guild invitations
- `guild_banners` - Guild banner designs and history
- `guild_progression` - Guild XP and leveling system

### Party System (3 tables)
- `parties` - Active parties with moderation
- `party_requests` - Pending party formation requests
- `player_party_preferences` - Player party preferences

### Banking & Economy (2 tables)
- `bank_tx` - Guild bank transactions
- `vault_gold` - Vault gold balance

### Vault System (2 tables)
- `vault_slots` - Vault item storage (54 slots per guild)
- `vault_transaction_log` - Complete audit trail of vault operations

### Combat & War (5 tables)
- `kills` - PvP kill tracking with weapon and location data
- `guild_kill_stats` - Guild-wide kill/death statistics
- `player_kill_stats` - Individual player kill/death statistics
- `anti_farm_data` - Anti-farming system data
- `wars` - War declarations and outcomes

### Progression (3 tables)
- `guild_progression` - XP, levels, unlocked perks
- `experience_transactions` - XP gain/loss history
- `guild_activity_metrics` - Weekly activity stats

### Auditing & Leaderboards (2 tables)
- `leaderboards` - Competition rankings
- `audits` - Administrative action logs

### Chat System (2 tables)
- `chat_visibility_settings` - Per-player channel visibility
- `chat_rate_limits` - Anti-spam protection

### Claims System (5 tables - optional)
- `claims` - Land claims
- `claim_partitions` - Subdivided claim areas
- `claim_default_permissions` - Default access rules
- `claim_flags` - Claim feature flags
- `claim_player_permissions` - Per-player claim access

### Meta (1 table)
- `schema_version` - Tracks schema version (currently 15)

## Data Types: SQLite → MariaDB Conversion

The schema has been converted from SQLite to MariaDB:

| SQLite | MariaDB | Notes |
|--------|---------|-------|
| `TEXT` | `VARCHAR(36)` | For UUIDs |
| `TEXT` | `VARCHAR(255)` | For names, strings |
| `TEXT` | `TEXT` | For large text/JSON |
| `INTEGER` | `INT` | Numeric values |
| `INTEGER` | `TINYINT(1)` | Booleans (0/1) |
| `INTEGER` | `BIGINT` | Unix epoch milliseconds |
| `TEXT` | `DATETIME` | ISO-8601 datetime strings |
| `REAL` | `DOUBLE` | Floating point numbers |

**Important Timestamp Handling:**

The codebase uses two different timestamp formats:

1. **BIGINT** - Unix epoch milliseconds (Java `System.currentTimeMillis()`)
   - Used by: `guild_progression`, `experience_transactions`, `player_kill_stats`, `guild_banners`, `vault_slots`, `vault_transaction_log`
   - Example: `1702751234567`

2. **DATETIME** - ISO-8601 formatted strings
   - Used by: `guilds`, `kills`, `wars`, `parties`, `bank_tx`, etc.
   - Example: `2025-12-16 03:35:37`
   - Plugin converts `Instant.now()` to SQL datetime format

## Datetime Format

**Important:** MariaDB requires `YYYY-MM-DD HH:MM:SS` format for DATETIME columns.

The plugin automatically formats timestamps correctly when using MariaDB. Old versions that used ISO-8601 format (`2025-12-16T03:35:37.425232274Z`) will cause errors.

## Indexes

The schema includes 50+ indexes for performance:
- Foreign key columns
- Frequently queried columns (guild_id, player_id, timestamps)
- Sorting columns (priority, level, type, status)

## Foreign Keys

All foreign keys use `ON DELETE CASCADE` or `ON DELETE SET NULL`:
- Deleting a guild removes all related data (members, relations, vault, etc.)
- Orphaned records (kills, audits) keep references but set to NULL

## Character Set

All tables use:
- **Engine:** InnoDB (ACID transactions, foreign keys)
- **Charset:** utf8mb4 (full Unicode support, including emojis)
- **Collation:** utf8mb4_unicode_ci (case-insensitive comparison)

## Schema Version

The schema includes a `schema_version` table that tracks the migration level:

```sql
SELECT version FROM schema_version;
-- Should return: 15
```

This matches the plugin's migration system (currently at version 15).

## Troubleshooting

### Error: Table already exists
If you get "Table already exists" errors, you have two options:

**Option 1: Drop existing tables**
```sql
DROP DATABASE s97222_lumaguilds;
CREATE DATABASE s97222_lumaguilds CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Then import schema.sql again
```

**Option 2: Skip existing tables**
The schema uses `CREATE TABLE IF NOT EXISTS`, so it's safe to run multiple times. It will only create missing tables.

### Error: Cannot add foreign key constraint
Make sure you:
1. Created the database with utf8mb4 charset
2. Import schema.sql in order (parent tables before child tables)
3. Have InnoDB engine enabled

### Error: Incorrect datetime value
Make sure you're using the **latest JAR** (0.5.3+) that formats datetimes correctly for MariaDB.

Old JARs use ISO-8601 format which MariaDB rejects.

## Migration from SQLite

If you have existing data in SQLite:

1. **Export SQLite data** (TODO: Create migration tool)
2. **Convert datetime formats** from ISO-8601 to SQL format
3. **Import to MariaDB**

For closed beta, it's easier to **wipe and start fresh** with the new schema.

## Maintenance

### Backup
```bash
mysqldump -u root -p s97222_lumaguilds > backup_$(date +%Y%m%d).sql
```

### Restore
```bash
mysql -u root -p s97222_lumaguilds < backup_20251216.sql
```

### Check table sizes
```sql
SELECT
    TABLE_NAME,
    ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS 'Size (MB)'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 's97222_lumaguilds'
ORDER BY (DATA_LENGTH + INDEX_LENGTH) DESC;
```

## Support

If you encounter issues:
1. Check server logs for detailed error messages
2. Verify MariaDB version (10.3+ recommended)
3. Confirm plugin version matches schema version (0.5.3 / v15)
4. Review datetime format compatibility
