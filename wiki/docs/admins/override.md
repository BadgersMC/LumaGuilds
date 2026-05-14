---
title: /lumaguilds override and recovery
audience: admin
topic: override
summary: Use admin override to unstick broken guilds — owner-less, locked-out, or corrupted state.
keywords: [override, recovery, admin, lumaguilds, broken guild, owner-less]
related: [permissions, troubleshooting]
updated: 2026-05-14
---

# /lumaguilds override and recovery

Use admin override to unstick broken guilds — when an owner leaves without transferring, a guild is locked-out, or other edge cases prevent normal recovery.

## Quick reference

| Gate | Bypass | Notes |
|------|--------|-------|
| `/g join` invitation requirement | Yes | Closed guilds with no invites can now be joined. |
| `/g menu` management permission check | Yes | Access all guild controls regardless of rank. |
| **Rank → Manage Rank** (priority 0 gate) | Yes | Can assign ranks including owner without promotion flow. |
| Owner self-protection (demotion block) | Yes | Owner can demote/remove themselves when override is ON. |
| Claims permission checks (if enabled) | Yes | Owner-level access to guild claims during override. |

## How it works

`/lumaguilds override` toggles a per-player, per-session flag. While ON, the gates above are bypassed for that admin. The flag is stored in-memory and **cleared when the player logs out** — it's not persistent.

When override is enabled, you become the functional owner of every guild on the server for menu access and permission checks. You still need to physically join the guild (`/g join <guild>`) to access its menu.

## Toggling override

Run the command to toggle:

```bash
/lumaguilds override
```

You'll see:

- **Enable:** `✅ Admin guild override enabled! You now have owner permissions in all guilds.`
- **Disable:** `❌ Admin guild override disabled! You no longer have owner permissions in all guilds.`

Check your permission with `/lumaguilds override` again — it prints the new state.

## Recovering an owner-less guild

When the guild owner leaves without transferring ownership:

1. **Toggle override on:**

   ```bash
   /lumaguilds override
   ```

   Confirm you see the "enabled" message.

2. **Join the guild** (no invite needed):

   ```bash
   /g join <guild_name>
   ```

3. **Open the guild menu** and navigate to **Ranks**:

   ```bash
   /g menu
   ```

   → Members → Manage Rank

4. **Assign yourself to the owner rank** (priority 0). If you're still blocked, make sure override is still ON.

5. **Toggle override off** once ownership is transferred:

   ```bash
   /lumaguilds override
   ```

Once you own the rank, the guild is recoverable — the new owner can reassign as needed. Do not edit the database directly; the `/lumaguilds override` flow is the supported path.

## Kicking a stuck member

If a member is locked in the guild (e.g. they left but their account is orphaned), use override to force-kick:

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

## Gotchas

- **Per-session only:** Restarting the server clears all override flags. Logging out also clears your override state.
- **Override does NOT bypass `/g transfer`:** The single-owner invariant is still enforced. You can't use override to avoid the transfer-ownership flow; you must properly demote the old owner and promote the new one.
- **Requires the `bellclaims.admin` permission:** Verify your permission node is set. By default, only OPs have access.
- **Override is all-or-nothing:** When ON, you have owner-level access to every guild, not just the broken one. Use caution on semi-anarchy servers where trust is limited.
- **Claim permission cache:** If claims are enabled and you toggle override, the permission cache is invalidated immediately. No need to rejoin.

## Related

- [Troubleshooting](troubleshooting.md) — other common issues
- [Permissions reference](permissions.md) — see `bellclaims.admin` node
- [Installation](installation.md) — verify plugin.yml permissions
