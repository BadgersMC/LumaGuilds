---
title: Schema setup
audience: dev
topic: schema-setup
summary: How LumaGuilds initializes and versions SQLite/MariaDB schemas.
keywords: [schema, sqlite, mariadb, migrations]
related: [migration-safety, infrastructure]
updated: 2026-09-28
---

# Schema setup

LumaGuilds supports SQLite and MariaDB through the infrastructure persistence layer.

## SQLite

SQLite schema versioning uses `PRAGMA user_version`. `SQLiteMigrations` reads the current version and applies each missing migration in order.

## MariaDB

MariaDB maintains explicit schema-version state and applies its corresponding migration sequence.

## Adding schema changes

1. Add the next migration step for SQLite.
2. Add the equivalent MariaDB migration.
3. Update repositories only after the schema exists.
4. Add tests for fresh schema and upgrade-from-prior-version paths.
5. Verify restart/retry behavior for partial failures.
6. Run both dialect-specific tests where available.

Schema ownership belongs in the migration layer. Repositories should assume the expected schema version rather than silently creating missing DDL.

See [Migration safety](migration-safety.md) before touching Chapter, reward, quest, war, or leaderboard tables.
