---
title: /lumaguilds override and recovery
audience: admin
topic: override
summary: Use admin override for management access and the guarded owner recovery command to repair an ownerless guild.
keywords: [override, recovery, admin, lumaguilds, broken guild, ownerless]
related: [permissions, troubleshooting]
updated: 2026-10-20
---

# /lumaguilds override and recovery

Use `/lumaguilds override` for temporary guild management access. Use `/lumaguilds owner recover` to restore ownership when an existing guild has no Owner. Recovery preserves the guild and its progress.

## Quick reference

| Gate | Bypass | Notes |
|------|--------|-------|
| `/g join` invitation requirement | Yes | Closed guilds with no invites can now be joined. |
| `/g menu` management permission check | Yes | Access all guild controls regardless of rank. |
| Assign the Owner rank (priority 0) through ordinary rank management | No | Use `/g transfer` when an Owner exists, or the guarded recovery command below when none exists. |
| Directly demote or remove an Owner | No | `/g leave` transfers ownership before removing the departing Owner. |
| Claims permission checks (if enabled) | Yes | Owner-level access to guild claims during override. |

## How it works

`/lumaguilds override` toggles a per-player, per-session flag. While ON, the gates marked **Yes** above are bypassed for that admin. The flag is stored in-memory and **cleared when the player logs out** — it's not persistent.

When override is enabled, you receive owner-level guild permissions for management checks. It does not change your membership or assign you the Owner rank. You still need to physically join the guild (`/g join <guild>`) to access its menu.

## Toggling override

Run the command to toggle:

```bash
/lumaguilds override
```

You'll see:

- **Enable:** `✅ Admin guild override enabled! You now have owner permissions in all guilds.`
- **Disable:** `❌ Admin guild override disabled! You no longer have owner permissions in all guilds.`

Run the command again when finished to disable override.

## Recovering an ownerless guild

The recovery command appoints an **existing member** as Owner only when the guild has no current Owner. It supports offline members and the server console. The administrator does not need to join the guild or enable override.

The sender needs both `lumaguilds.admin` and `lumaguilds.admin.owner.recover`. Both default to operators. The specialist recovery permission is not inherited from `lumaguilds.admin`, `bellclaims.admin`, or a guild rank. Grant it explicitly to any non-operator administrator who should perform recovery.

### Preview and confirm

1. Verify the intended member's Minecraft UUID and their authority to receive ownership. If an account was compromised, complete its account recovery first. Use the UUID belonging to the existing guild membership, including for an offline player.
2. Run the preview with the guild UUID and member UUID:

   ```text
   /lumaguilds owner recover <guild-uuid> <member-uuid>
   ```

   The preview displays the guild, target member, current rank, intended Owner rank, and their identifiers. A successful preview verifies that the guild has no Owner and makes no changes.

3. Review the preview and run the exact confirmation command it supplies within **60 seconds**:

   ```text
   /lumaguilds owner recover <guild-uuid> <member-uuid> confirm <token>
   ```

   The token is single-use and belongs to the sender, guild, and target in that preview. Confirmation rechecks the live state before committing. If the token expires or the relevant membership or rank state changes, request and review a new preview.

4. Check the result. A successful recovery changes only the target member's rank and refreshes their cached claim permissions. Membership date, other members, guild level, experience, bank state, homes, and rank definitions are preserved.

### When recovery is refused

| Condition | Action |
|-----------|--------|
| The guild already has an Owner | Use the normal `/g transfer` flow through the existing Owner. Recovery cannot replace an Owner. Investigate duplicate Owner memberships separately if more than one exists. |
| The guild or target membership does not exist | Verify both UUIDs. Recovery does not create a guild or add a member. |
| The Owner rank is missing or more than one rank has priority 0 | Investigate the rank configuration. Recovery does not invent or merge ranks. |
| A membership references a missing or foreign-guild rank | Investigate the inconsistent membership/rank data before retrying. |
| The preview is stale or its token is invalid, expired, or already used | Run a fresh preview and review its current state. |
| Storage failure leaves the result unconfirmed | Check the current ownership state and server logs before trying again. |

Normal ownership changes continue to use `/g transfer`. An Owner using `/g leave` must complete the existing succession flow; directly demoting or removing an Owner remains blocked even with override enabled.

## Kicking a stuck member

For a stuck non-Owner member, use the existing member-management flow with override:

1. **Toggle override on:**

   ```bash
   /lumaguilds override
   ```

2. **Open the guild menu** and go to **Members**:

   ```bash
   /g menu → Members
   ```

3. **Kick the member** (works even if they're offline).

4. **Toggle override off** when done.

## Bypassing a closed guild

If a guild is locked to new joins (invitation-only) and you need to grant access to an admin:

1. **Toggle override on.**
2. The admin runs `/g join <guild_name>` — the invitation requirement is bypassed.
3. The admin is now a member and can use `/g menu`.

## Auditing override use

The `AdminOverrideServiceImpl` logs all toggles at INFO level:

```text
Player {uuid} enabled admin guild override
Player {uuid} disabled admin guild override
```

Check your server logs (`logs/latest.log`) for these lines to audit who has used override and when. Override is a powerful tool — log rotation and retention are recommended so you can trace any abuse.

Owner recovery has its own server-log audit entries. They identify the administrator or console, guild UUID, target member UUID, previous rank, intended Owner rank, and outcome. Use these entries to distinguish a preview or rejected attempt from a committed recovery.

## Gotchas

- **Per-session only:** Restarting the server clears all override flags. Logging out also clears your override state.
- **Ownership remains protected:** Override cannot assign the Owner rank or directly demote or remove an Owner. Use `/g transfer` for ordinary succession and `/lumaguilds owner recover` only for an ownerless guild.
- **Recovery requires a separate permission:** `lumaguilds.admin.owner.recover` is checked for both preview and confirmation. Enabling override does not grant it.
- **Requires the `lumaguilds.admin` permission:** Verify your permission node is set. By default, only OPs have access. The legacy alias `bellclaims.admin` (inherited from the Bell Claims fork lineage) is still checked by some internal code paths but is granted automatically when you grant `lumaguilds.admin` — see `plugin.yml`.
- **Override is all-or-nothing:** When ON, you have owner-level access to every guild, not just the broken one. Use caution on semi-anarchy servers where trust is limited.
- **Claim permission cache:** If claims are enabled and you toggle override, the permission cache is invalidated immediately. No need to rejoin.

## Related

- [Troubleshooting](troubleshooting.md) — other common issues
- [Permissions reference](permissions.md) — `lumaguilds.admin` (canonical) and `bellclaims.admin` (legacy alias)
- [Installation](installation.md) — verify plugin.yml permissions
