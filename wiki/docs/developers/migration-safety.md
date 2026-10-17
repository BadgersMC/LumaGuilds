---
title: Migration safety
audience: dev
topic: migration-safety
summary: Rules for changing LumaGuilds schemas and durable Chapter lifecycle state safely.
keywords: [migration, database, sqlite, mariadb, chapter]
related: [schema-setup, infrastructure, upgrade-3.0]
updated: 2026-09-28
---

# Migration safety

Persistence changes must work for both SQLite and MariaDB and must survive restart/crash boundaries.

## Rules

- Never rewrite an already-released migration in place.
- Add a new versioned migration for schema changes.
- Keep SQLite and MariaDB behavior equivalent.
- Make migrations safe to retry where practical.
- Persist state transitions before relying on in-memory follow-up work.
- Test crash windows around rewards, payouts, backups, and Chapter rollover.
- Do not let repository constructors create ad-hoc production tables.

Chapter rollover uses durable lifecycle state, transition markers, backup verification, and recovery tooling. Treat those records as part of the protocol, not as disposable bookkeeping.

Before merging migration work, run the focused migration/repository tests plus the full test suite. For release upgrades, validate against a copy of production-like data.
