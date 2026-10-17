---
title: Upgrading to LumaGuilds 3.0
audience: admin
topic: upgrade-3-0
summary: Upgrade an existing LumaGuilds server to 3.0 on Paper 26.2 and Java 25 without skipping the Chapter 2 safety gates.
keywords: [upgrade, migration, 3.0, chapter 2, paper 26.2, java 25]
related: [installation, troubleshooting]
updated: 2026-09-28
---

# Upgrading to LumaGuilds 3.0

LumaGuilds 3.0 is the Chapter 2 / Season 2 release line. It also moves the runtime baseline to **Paper 26.2** and **Java 25**.

Treat this as a major server upgrade, even though the plugin performs its database migrations automatically.

## Before upgrading

1. Stop the server cleanly.
2. Back up the entire LumaGuilds plugin data directory.
3. Back up your SQLite database or MariaDB database independently.
4. Back up config.yml, progression.yml, and language overrides.
5. Upgrade the server runtime to Java 25.
6. Upgrade Paper or your compatible fork to 26.2.
7. Install the matching RoseChat build before starting LumaGuilds.

Do not test the migration for the first time against your only production database.

## First 3.0 boot

On first startup, LumaGuilds applies its schema migrations and reconstructs the services required by Chapter 2.

Watch the console until startup is fully complete. Do not interrupt the process while a migration is running.

After startup, verify:

- /version LumaGuilds reports 3.0.x;
- /version reports Paper 26.2 or a compatible build;
- the JVM is Java 25;
- RoseChat loaded before LumaGuilds;
- existing guilds, members, homes, ranks, vault state, and relations are present;
- Java and Bedrock guild menus both open correctly.

## Chapter 2 rollout gates

The default configuration intentionally keeps the riskiest Chapter 2 systems gated:

```yaml
seasonal_elo:
  enabled: false

progression:
  chapter_two_rewards_enabled: false
  chapter_two_gold_costs_enabled: false
  prestige:
    enabled: false
```

These switches are independent. Enabling one does not perform or validate the migration for the others.

## Recommended rollout order

1. Boot 3.0 with the Chapter 2 gates still disabled.
2. Verify guild data and schema health.
3. Test progression and weekly quests with real Java and Bedrock clients.
4. Validate reward ownership and purchases.
5. Enable Chapter 2 rewards when the migration is confirmed.
6. Enable guild-gold creation/home costs when the economy behavior is confirmed.
7. Enable prestige only after the reset and retained-perk flow has been tested.
8. Enable seasonal Elo only after rated-war lifecycle validation is complete.

This staged rollout keeps permanent progression data live while withholding the systems that are hardest to undo after a bad migration.

## Release and reload behavior

`/lumaguilds reload` reloads supported configuration and refreshes several caches. `/lumaguilds progressionreload` reloads `progression.yml`.

A full restart is still required for startup-only settings, plugin dependencies, database backend changes, and major-version upgrades. Do not use PlugMan-style plugin hot reloads for a 3.0 migration.

## Chapter recovery tools

LumaGuilds includes admin recovery commands under `/lumaguilds chapter` for inspecting and recovering Chapter lifecycle state.

Use `/lumaguilds help` for the current command surface. Recovery operations should only be used after you have a verified database backup.

## Rollback

If 3.0 fails before you intentionally enable or exercise new Chapter 2 behavior:

1. Stop the server.
2. Save the failed logs and database for diagnosis.
3. Restore the pre-upgrade database and LumaGuilds data directory.
4. Restore the previous plugin/server combination together.

Do not run an older LumaGuilds build against a database already migrated by 3.0 and then continue using that database as production.

## Related

- [Installation & config.yml](installation.md)
- [Troubleshooting](troubleshooting.md)
- [Geyser/Floodgate behavior](geyser.md)
