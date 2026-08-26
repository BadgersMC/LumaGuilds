# Guild Emoji Grant Reconciliation Design

**Task:** LG-1104  
**Requirement:** REQ-048  
**Status:** Proposed

## Purpose

Operators can map a guild name to one Nexo-compatible permission node in
`guild.emoji_grants`. LumaGuilds must grant that permission to every current
member and must remove only the permissions it previously granted when the
membership, guild name, or configuration changes.

The current implementation grants on startup and member join, but derives
revocation solely from the current config. After a mapping is removed or
replaced, the old permission is no longer discoverable. A restart loses the
last in-memory configuration entirely. This design adds durable ownership and
one reconciliation path for every lifecycle trigger.

## Operator Contract

```yaml
guild:
  emoji_grants:
    Badgers: enthusia.emoji.badger
    Dragons: enthusia.emoji.dragon
```

- Guild-name matching is case-insensitive.
- Each guild name maps to at most one permission node.
- Permission nodes are trimmed, normalized to lowercase, and accepted only
  when they match `[a-z0-9][a-z0-9_.-]*`.
- Invalid or blank mappings are ignored and logged. Any formerly owned grant
  for that mapping becomes obsolete and is revoked during reconciliation.
- The configured permission must be the permission Nexo/chat actually checks.
  LumaGuilds does not invent a second parallel permission.
- Configuration remains the only operator-facing mutation mechanism. This
  task does not add an admin command.

## Architecture

### Domain

No Bukkit, LuckPerms, SQL, or configuration types enter `domain/**`.

`EmojiPermissionGrant` is a small domain value containing:

- `playerId: UUID`
- `guildId: UUID`
- `permission: String`

Its identity is `(playerId, guildId)` because the current config permits one
managed permission per guild. The permission is the replaceable value.

### Application ports

`EmojiGrantRepository` owns the durable record of grants made by LumaGuilds:

- `getAll(): List<EmojiPermissionGrant>`
- `getForGuild(guildId): List<EmojiPermissionGrant>`
- `getForPlayerAndGuild(playerId, guildId): EmojiPermissionGrant?`
- `upsert(grant): Boolean`
- `delete(playerId, guildId): Boolean`

`EmojiPermissionGateway` owns the external permission side effect:

- `grant(playerId, permission): Boolean`
- `revoke(playerId, permission): Boolean`

The gateway is the only component allowed to communicate with the permissions
plugin. Tests use an in-memory gateway; production initially retains the
existing LuckPerms command integration behind this port.

### Infrastructure

`EmojiGrantRepositorySQLite` creates and accesses:

```sql
CREATE TABLE IF NOT EXISTS guild_emoji_grants_applied (
    player_id TEXT NOT NULL,
    guild_id TEXT NOT NULL,
    permission TEXT NOT NULL,
    PRIMARY KEY (player_id, guild_id)
);
CREATE INDEX IF NOT EXISTS idx_guild_emoji_grants_guild
    ON guild_emoji_grants_applied(guild_id);
```

`LuckPermsEmojiPermissionGateway` wraps the current console-command behavior.
UUID and permission values are validated before command construction. Calls
return the dispatch result. All callers enter through Bukkit's primary thread;
the gateway schedules onto that thread only when necessary.

`GuildEmojiGrantService` becomes the reconciliation coordinator. It depends on
guild/member read services, the validated config mapping, the ownership
repository, and the permission gateway. It does not directly construct or
dispatch console commands.

## Reconciliation Algorithm

Global reconciliation computes a desired map keyed by `(playerId, guildId)`:

1. Read every current guild.
2. Resolve its normalized name in the validated config mapping.
3. If mapped, pair every current member with the configured permission.
4. Read every recorded grant owned by LumaGuilds.
5. Revoke recorded entries that are absent from desired or whose permission
   differs.
6. Grant desired entries that are absent from recorded or whose permission
   differs.

Replacement from A to B is ordered: revoke A and retain its ledger row until
the revoke succeeds; then grant B and replace the ledger row only after the
grant succeeds. This prevents the old permission from being forgotten and
ensures a failed operation can be retried on the next reconciliation.

If a grant succeeds but the ledger write fails, the service immediately
attempts a compensating revoke and reports failure. If a revoke succeeds but
ledger deletion fails, the stale row remains and the next reconciliation
performs an idempotent revoke again.

The service serializes reconciliation calls with one lock so startup, reload,
and lifecycle events cannot interleave two replacements for the same entry.

## Lifecycle Triggers

- **Plugin enable:** run global reconciliation after repositories and services
  are ready.
- **Config reload:** run global reconciliation after `reloadConfig()` and
  `initConfig()` so removals and replacements are observed immediately.
- **Member join:** reconcile the `(player, guild)` pair and grant the current
  mapping if one exists.
- **Member leave/kick:** revoke and delete the recorded `(player, guild)` grant
  directly. This does not depend on event ordering or the current config.
- **Guild create:** reconcile the new guild so its owner receives a preconfigured
  name mapping.
- **Guild rename:** after the rename commits, reconcile every member of that
  guild. Old-name grants are recorded in the ledger and are therefore revoked
  before a new-name grant is applied.
- **Guild disband:** revoke and delete every recorded grant for the guild. This
  uses the ledger and remains valid after the guild row is deleted.

A new `GuildRenamedEvent` carries the guild ID, old name, and new name and fires
only after repository update succeeds. Existing join, removal, creation, and
disband events remain the other lifecycle boundaries.

## Failure Handling

- One failed player operation does not stop reconciliation for other players.
- Failed external operations remain represented by their prior ledger state and
  are logged with player, guild, permission, and operation.
- Repository failures are logged and return an unsuccessful reconciliation
  result; no success is claimed to the reload caller.
- Plugin disable performs no revocation. The ledger and external grants are
  intentionally durable across restarts and are reconciled on the next enable.
- LumaGuilds never revokes an unrecorded permission, even if it matches a
  configured node. Operator- or group-owned grants therefore remain untouched.

## Testing Strategy

SPEAR red-green cycles cover:

1. Initial mapping grants every current guild member and records ownership.
2. A later member join receives and records the grant.
3. Leave/kick revokes only that membership's recorded grant.
4. Guild disband revokes all recorded grants after the guild is gone.
5. Guild rename revokes the old-name permission and grants the new-name mapping.
6. Config removal revokes and deletes prior grants.
7. Config replacement A to B revokes A before granting B.
8. Restart recovery reconciles persisted ledger rows against current config.
9. Unrecorded matching permissions are never revoked.
10. Revoke failure retains the old ledger row for retry.
11. Grant failure does not create a ledger row.
12. Config loading normalizes names/nodes and rejects invalid nodes.
13. Architecture tests preserve domain/application/infrastructure boundaries.

Focused service tests use real in-memory repository and gateway fakes so they
assert state and ordered side effects rather than mock existence. SQLite tests
prove schema persistence and restart behavior. Listener tests prove events call
the correct narrow reconciliation entry points.

## Scope Boundaries

Included:

- Durable ownership tracking.
- Startup, reload, membership, rename, creation, and disband reconciliation.
- Validation and documentation of `guild.emoji_grants`.
- Replacement of direct permission commands with a gateway adapter.

Excluded:

- Creating or editing Nexo glyphs.
- Choosing the guild's displayed emoji (LG-1103).
- Multiple automatic emoji permissions per guild.
- An in-game admin command or GUI for mapping changes.
- General-purpose LuckPerms synchronization outside guild emoji grants.

## Acceptance

LG-1104 is complete when all REQ-048 lifecycle cases pass, stale owned grants
are recoverable across restart, unowned permissions are preserved, the full
test suite is green, and `docs/tasks.md` contains concrete evidence and files.
