---
title: Troubleshooting
audience: admin
topic: troubleshooting
summary: Operator-facing fixes for common LumaGuilds issues — stuck guilds, broken homes, schema drift, chat leaks.
keywords: [troubleshooting, issues, fixes, recovery, stuck, broken]
related: [override, installation, rosechat]
updated: 2026-05-14
---

# Troubleshooting

Common LumaGuilds issues and how to fix them. Most issues are self-healing — schema backfills on startup, caches refresh on join/leave events — but a few scenarios need operator intervention.

## Owner-less guild

**Symptom:** A guild's owner left the server without transferring ownership. No one can access the guild menu or reassign ranks.

**Cause:** Guild ownership is single-account. When the owner quits, the guild becomes locked because only the owner has access to the Ranks menu. No fallback or co-owner exists.

**Fix:** Use admin override to recover. See [/lumaguilds override and recovery](override.md) → **Recovering an owner-less guild** for step-by-step instructions. TL;DR: toggle override on, join the guild, open menu → Ranks, assign yourself to the owner rank, toggle override off.

## Guild stuck at "Level 1" with high XP

**Symptom:** A guild shows `Level: 1` in `/g info` but has accumulated thousands of XP. It should be level 5+.

**Cause:** This was a real bug before May 2026 (pre-progression refactor). The XP was stored but level calculation was broken.

**Fix:** Upgrade the plugin to the latest build (2026-05+). On first startup after the upgrade, the schema migration runs automatically and recalculates all guild levels. Restart the server once, then check `/g info` again — the level should correct itself. No manual action needed.

## Home teleport from Nether silently fails

**Symptom:** A player tries `/g home` while in the Nether and nothing happens. No error message, but they don't teleport.

**Cause:** This was real and fixed in commit 6fcdfbd (May 2026). The issue was synchronous teleportation blocking on cross-dimension movement. The fix uses `teleportAsync` to defer the teleport one tick.

**Fix:** Verify you're on plugin build 2026-05 or later. Confirm the fix is applied by checking your plugin version:

```bash
/version LumaGuilds
```

Should show a build date of 2026-05-13 or later. If you're on an older build, upgrade. If the issue persists on a current build, check for other teleport-blocking plugins (anticheat, custom teleport systems) in your plugin list. Look at the console for "cancelled teleport" messages from other plugins.

## Guild chat leaks to global with RoseChat installed

**Symptom:** When a player uses `/g chat` to toggle to guild-only chat, their messages still appear in global chat.

**Cause:** Misconfiguration or missing RoseChat channel integration. When LumaGuilds hands off to RoseChat for custom formatting, it expects a `guild-chat` channel to be registered. If the registration fails or isn't complete, messages route to global instead.

**Fix:** See [RoseChat integration](rosechat.md) for the canonical setup. Confirm the LumaGuilds RoseChat channel is registered by running:

```bash
/papi parse <your_name> %lumaguilds_chat_format%
```

Should return a non-empty string (e.g., `[guild_name]`). If it's blank, RoseChat isn't hooked. Restart the server and ensure RoseChat loads *before* LumaGuilds in `plugin.yml` soft-depends.

## Guild banner blank in Diplomatic Relations menu

**Symptom:** The guild banner slot in the guild's Diplomatic Relations menu (alliances, wars) shows a blank/empty item.

**Cause:** Corrupted or missing banner data row in the database.

**Fix:** No operator action needed as of May 2026. The plugin now renders a placeholder banner automatically when the data is missing. The guild can copy a new banner via `/g menu` → Banner.

If you want to clean up the database, you can delete the orphaned row directly:

```bash
sqlite3 plugins/LumaGuilds/lumaguilds.db "DELETE FROM guild_banners WHERE guild_id = <guild_id>;"
```

Replace `<guild_id>` with the guild's ID (visible in `/g info`). Take a backup first.

## First message after `/g chat` toggle off was dropped

**Symptom:** A player toggles off guild chat (`/g chat` to switch to global), types a message, and the first message doesn't appear. Subsequent messages work fine.

**Cause:** Stale chat-claim metadata. When the toggle happens, the permission state is cached but not immediately cleared. The first message checks the cache and still thinks the player is in guild chat mode.

**Fix:** This was fixed in commit a09429d (May 2026). Upgrade to the latest plugin build. If you're already on 2026-05+ and it still happens, clear the permission cache by toggling off guild chat again.

## Schema migration warnings in startup log

**Symptom:** On server startup, you see warnings like:

```text
[LumaGuilds] INFO - Running database migration...
[LumaGuilds] INFO - Migration completed successfully
```

**Cause:** In-place schema migration in progress (new columns, table structure changes). This is normal during major-version upgrades.

**Fix:** These are informational, not errors. The migration is self-healing and runs on first startup. Just let it finish. The server will be briefly slower during the migration; don't stop the server. If you want to avoid downtime, migrate to MariaDB (see `config.yml` database section) and test the upgrade offline first.

## `/lumaguilds override` crashes on claims-disabled servers

**Symptom:** Running `/lumaguilds override` throws a Koin exception:

```text
org.koin.core.error.NoDefinitionFoundException
```

**Cause:** This was a real bug (fixed in commit 3699222). When claims are disabled, the `GuildRolePermissionResolver` is not registered in the Koin DI container. The command tried to access it unconditionally, causing a crash.

**Fix:** Upgrade the plugin to the latest build. The fix makes the resolver lazy-loaded — it's now resolved only if claims are enabled. If you see this on a current build, file a GitHub issue.

## Player reports they can dupe items via guild vault chest

**Symptom:** A player claims they can duplicate items by putting items in the vault, breaking the vault chest, and recovering items from the database.

**Cause:** This was fixed in commit 6bb75e9 (May 2026). Vault chests are now blocked from being used as furnace fuel, and the item recovery system prevents dupe chains.

**Fix:** Verify you're on build 2026-05+. If it's still reproducible on a current build, file a GitHub issue with step-by-step reproduction steps.

## General debugging tips

**Plugin logs:** LumaGuilds logs to the standard server log (`logs/latest.log`). All override toggles, guild events, and errors appear here.

**Inspect the database directly (read-only):** To check guild data without restarting:

```bash
sqlite3 plugins/LumaGuilds/lumaguilds.db ".tables"
```

Shows all tables. Common ones:

- `guilds` — guild names, owners, levels, XP
- `guild_members` — membership and rank assignments
- `guild_homes` — home coordinates
- `guild_invitations` — pending invites
- `claims` — land claim data

View a guild's info:

```bash
sqlite3 plugins/LumaGuilds/lumaguilds.db "SELECT id, name, owner_id, level, xp FROM guilds WHERE name = 'YourGuild';"
```

**Do not edit the database while the server is running** — always stop the server first to avoid corruption.

## When to escalate to GitHub Issues

If you've confirmed the issue is reproducible on the latest plugin build and none of the above fixes apply, file a GitHub issue:

1. Include your plugin version: `/version LumaGuilds`
2. Include your Paper version: `/version`
3. **Step-by-step reproduction:** Exactly what a player does to trigger the bug
4. **Server log excerpt:** Any relevant error messages or stack traces
5. **Config context:** If applicable, relevant sections of `config.yml` (e.g., claims enabled/disabled, database type)

Issues: <https://github.com/BadgersMC/LumaGuilds/issues>

## Related

- [/lumaguilds override and recovery](override.md) — admin recovery tools
- [Installation & config.yml](installation.md) — database and dependency setup
- [RoseChat integration](rosechat.md) — custom chat format troubleshooting
