---
title: Troubleshooting
audience: admin
topic: troubleshooting
summary: Operator-facing checks for LumaGuilds 3.0 startup, Chapter 2, quests, Bedrock forms, chat, homes, and recovery.
keywords: [troubleshooting, issues, recovery, 3.0, chapter 2, quests, bedrock]
related: [override, installation, upgrade-3.0, rosechat, geyser]
updated: 2026-10-20
---

# Troubleshooting

Start with the environment. LumaGuilds 3.0 targets **Paper 26.2**, **Java 25**, and requires **RoseChat** to load.

## Plugin will not load

Check the first LumaGuilds error in `logs/latest.log`, not the cascade that follows it.

Verify:

```text
/version
/version LumaGuilds
/version RoseChat
```

Expected baseline:

- Paper 26.2 or a compatible fork;
- Java 25;
- LumaGuilds 3.0.x;
- RoseChat loaded before LumaGuilds.

If you upgraded from 2.x, follow [Upgrading to LumaGuilds 3.0](upgrade-3-0.md) before changing database state manually.

## Chapter 2 features are present but disabled

This is normally configuration, not a migration failure. The 3.0 defaults intentionally gate high-impact systems:

```yaml
seasonal_elo:
  enabled: false

progression:
  chapter_two_rewards_enabled: false
  chapter_two_gold_costs_enabled: false
  prestige:
    enabled: false
```

Verify the migration and live behavior first, then enable each gate deliberately. See the [3.0 upgrade guide](upgrade-3-0.md).

## Chapter lifecycle looks stuck

Use the admin lifecycle tooling before editing SQL by hand.

```text
/lumaguilds chapter status <chapter_id>
```

The status includes lifecycle phase, end time, backup/restore verification state, and the last recorded error. Use `/lumaguilds help` for the current recovery actions.

If a rollover failed after a crash, preserve the database and logs before retrying. The lifecycle code is designed to recover idempotently; manual table edits can destroy the evidence needed to do that safely.

## Weekly quest progress is not moving

First read the full objective. Procedural quests may have conditions in addition to the action itself, such as a dimension or location requirement.

Check:

- the player belongs to the guild being viewed;
- the action/target matches the generated objective;
- any dimension or corridor/location condition is satisfied;
- the quest week has not rolled over;
- the target is not excluded by server quest-generation policy.

Completing the normal target does **not** stop leaderboard progress. A guild can continue scoring past completion.

If the quest is genuinely impossible, capture the exact rendered objective and report it. Do not replace quest rows manually while the server is running.

## Quest completed but a player did not see the toast

Quest reward state is server-side; the toast is a notification surface, not the source of truth.

Check the guild quest menu and progression state first. LumaGuilds persists completion notification state and reconciles pending rewards so a crash or offline member does not require a manual reward claim.

If the reward state is correct but one client missed the toast, treat it as a notification/UI issue rather than re-awarding the quest.

## Bedrock player gets Java menus

LumaGuilds uses a Bedrock form only when:

1. Bedrock menus are enabled;
2. Floodgate identifies the player as Bedrock;
3. the Cumulus form API is available.

If one of those checks fails, the menu can fall back to the Java path. See [Geyser/Floodgate behavior](geyser.md).

If only one form fails while the rest work, capture that specific action and stack trace. That usually indicates a menu-specific bug, not a Geyser installation problem.

## Guild chat leaks to global

RoseChat is a hard dependency in 3.0. Confirm it loaded successfully and that the LumaGuilds channel integration initialized without errors.

Restart the server after changing RoseChat/channel configuration. Do not use a plugin hot-reloader to repair chat routing.

If the problem persists, include the RoseChat and LumaGuilds startup sections from `latest.log` when reporting it.

## Owner or rank permissions are broken

For a guild with no Owner, preview a recovery using the guild UUID and an
existing member's Minecraft UUID:

```text
/lumaguilds owner recover <guild-uuid> <member-uuid>
```

Review the result, then use the confirmation command it supplies within 60
seconds. This requires both `lumaguilds.admin` and
`lumaguilds.admin.owner.recover`. Recovery preserves the guild's progress and
refuses to replace an existing Owner, create a membership, or proceed with
inconsistent ranks.

For temporary management access, `/lumaguilds override` grants owner-level
permission checks for the session. It does not assign the Owner rank or permit
direct Owner demotion or removal. Use `/g transfer` when a current Owner is
available.

See [Override & recovery](override.md) for the complete procedure and rejection
reasons. If a storage error leaves the result unconfirmed, inspect current
ownership and the server logs before retrying.

## Home teleport is blocked as unsafe

LumaGuilds validates the destination before teleporting. Damaging blocks, invalid world state, and invalid height can trigger the safety warning.

If the location is intentionally safe and the command offers an unsafe-location confirmation flow, follow the confirmation shown in chat. Otherwise move the home to a safer block.

If another plugin cancels the teleport, its cancellation may appear in the server log even though the LumaGuilds home itself is valid.

## Migration messages appear on startup

Schema migration output during a major upgrade is expected. Let the migration finish and do not stop the server midway through it.

If startup ends in an error:

1. stop the server;
2. preserve `latest.log`;
3. preserve the migrated database;
4. do **not** repeatedly start old and new plugin versions against the same database;
5. use your pre-upgrade backup if a rollback is required.

## Reload behavior

`/lumaguilds reload` reloads supported runtime configuration and refreshes several caches. `/lumaguilds progressionreload` reloads `progression.yml`.

Neither command replaces a full restart for plugin dependencies, database backend changes, startup-only settings, or major-version upgrades.

## When to escalate

Include:

- `/version` output;
- `/version LumaGuilds`;
- Java version;
- relevant config section;
- exact reproduction steps;
- the first relevant exception and surrounding log lines.

Issues: <https://github.com/BadgersMC/LumaGuilds/issues>

## Related

- [Upgrading to LumaGuilds 3.0](upgrade-3-0.md)
- [Override & recovery](override.md)
- [RoseChat integration](rosechat.md)
- [Geyser/Floodgate behavior](geyser.md)
