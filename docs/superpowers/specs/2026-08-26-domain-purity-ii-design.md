# Domain Purity II Design

## Scope

This design implements REQ-045 and LG-1001. It removes every import of
`org.bukkit`, `org.koin`, `co.aikar`, and `net.kyori` from `domain/**`, makes
that rule executable, and preserves LumaGuilds' public Bukkit event behavior
through a deliberate API package.

The current audit contains 20 framework-leaking domain files: 17 Bukkit event
classes plus `VaultInventory`, `ViewerSession`, and `WriteBuffer`. The older
task estimate of 21 files is stale; implementation evidence will use the
audited count.

## Decisions

### Public events belong to the API boundary

The 17 classes currently under `net.lumalyte.lg.domain.events` are public
Bukkit integration events rather than domain-owned events. Move them to
`net.lumalyte.lg.api.events` and retain:

- class names;
- constructor parameter order, types, and nullability;
- public payload properties;
- synchronous Bukkit dispatch timing;
- one shared `HandlerList` per event; and
- non-cancellable, synchronous behavior.

All internal producers and listeners move to the new imports atomically.
Third-party plugins must replace the old package import with the corresponding
`net.lumalyte.lg.api.events` import and recompile. No compatibility classes
remain in `domain.events`, because a Bukkit-compatible shim there would violate
REQ-045.

No pure domain-event hierarchy or application event bus is introduced. The
existing events originate in Bukkit infrastructure services and are consumed
through Bukkit's event bus; duplicating them behind a second bus adds no current
business value.

## Event migration contract

Every old event maps one-to-one to the same simple class name:

| Old package | New package |
|---|---|
| `net.lumalyte.lg.domain.events.GuildBankDepositEvent` | `net.lumalyte.lg.api.events.GuildBankDepositEvent` |
| `net.lumalyte.lg.domain.events.GuildBannerChangedEvent` | `net.lumalyte.lg.api.events.GuildBannerChangedEvent` |
| `net.lumalyte.lg.domain.events.GuildBannerSetEvent` | `net.lumalyte.lg.api.events.GuildBannerSetEvent` |
| `net.lumalyte.lg.domain.events.GuildCreatedEvent` | `net.lumalyte.lg.api.events.GuildCreatedEvent` |
| `net.lumalyte.lg.domain.events.GuildDisbandedEvent` | `net.lumalyte.lg.api.events.GuildDisbandedEvent` |
| `net.lumalyte.lg.domain.events.GuildHomeSetEvent` | `net.lumalyte.lg.api.events.GuildHomeSetEvent` |
| `net.lumalyte.lg.domain.events.GuildLeaderboardRankChangeEvent` | `net.lumalyte.lg.api.events.GuildLeaderboardRankChangeEvent` |
| `net.lumalyte.lg.domain.events.GuildLevelUpEvent` | `net.lumalyte.lg.api.events.GuildLevelUpEvent` |
| `net.lumalyte.lg.domain.events.GuildMemberJoinEvent` | `net.lumalyte.lg.api.events.GuildMemberJoinEvent` |
| `net.lumalyte.lg.domain.events.GuildMemberRemovedEvent` | `net.lumalyte.lg.api.events.GuildMemberRemovedEvent` |
| `net.lumalyte.lg.domain.events.GuildOwnershipTransferEvent` | `net.lumalyte.lg.api.events.GuildOwnershipTransferEvent` |
| `net.lumalyte.lg.domain.events.GuildRelationChangeEvent` | `net.lumalyte.lg.api.events.GuildRelationChangeEvent` |
| `net.lumalyte.lg.domain.events.GuildTrackingChangedEvent` | `net.lumalyte.lg.api.events.GuildTrackingChangedEvent` |
| `net.lumalyte.lg.domain.events.GuildVaultPlacedEvent` | `net.lumalyte.lg.api.events.GuildVaultPlacedEvent` |
| `net.lumalyte.lg.domain.events.GuildWarDeclaredEvent` | `net.lumalyte.lg.api.events.GuildWarDeclaredEvent` |
| `net.lumalyte.lg.domain.events.GuildWarEndEvent` | `net.lumalyte.lg.api.events.GuildWarEndEvent` |
| `net.lumalyte.lg.domain.events.GuildWarKillEvent` | `net.lumalyte.lg.api.events.GuildWarKillEvent` |

This table also serves as the release migration guide for other BadgersMC
plugins.

## Vault subsystem ownership

`VaultInventory`, `ViewerSession`, and `WriteBuffer` directly hold Bukkit
`ItemStack`, `Inventory`, and player-session state. They are infrastructure
models, not domain entities.

Moving only those types would create an application-to-infrastructure
dependency because `VaultInventoryManager` and `VaultBackupService` currently
consume them from `application.services`. Move all five classes together into
an infrastructure vault package. Update Koin bindings and consumers to their
new packages without changing public methods or runtime behavior.

The move preserves:

- concurrent vault caching;
- dirty-state and retry semantics;
- slot deletion buffering;
- gold balance atomicity;
- viewer-session timing and idle checks;
- database serialization and restoration; and
- real-time inventory synchronization.

No Bukkit inventory abstraction is added. These classes are intentionally
Bukkit-specific, so relocating the subsystem is simpler and more accurate than
wrapping `ItemStack` or `Inventory` in domain interfaces.

## Executable architecture contract

Populate the `forbidden:` list in `docs/implementation.md` with:

```yaml
forbidden:
  - org.bukkit
  - org.koin
  - co.aikar
  - net.kyori
```

Extend `LayerRulesTest` with an external-package assertion over production
files in the domain package. The test must fail if a domain import equals a
forbidden prefix or begins with that prefix followed by `.`. The executable
test and documented list must share the same values or have a contract test
that rejects drift.

Existing layer direction remains:

```text
domain <- application <- infrastructure
```

Packages such as `api` and `interaction` remain outside the three-layer Konsist
graph, as already documented.

## Migration sequence

1. Add a failing architecture test that identifies the current forbidden
   imports and proves documentation enforcement.
2. Move the 17 events to `api.events`; update all producers, listeners, and
   tests in one compile-safe slice.
3. Add event API contract tests for inheritance, handler lists, and payloads.
4. Move the five Bukkit vault classes as one subsystem; update DI and consumers.
5. Run vault behavior tests and add missing regression coverage before changing
   behavior-sensitive code.
6. Populate the implementation guide contract and migration table.
7. Run the complete Gradle suite, architecture tests, markdown lint, diff
   integrity checks, and code review.

## Error handling and compatibility

Event-handler failures remain governed by Bukkit. Events remain synchronous and
non-cancellable, and no new asynchronous dispatcher is introduced.

Vault persistence keeps its existing failure behavior: unsuccessful writes
retain dirty/buffered state for retry. This task does not change retry policy,
storage schema, inventory format, or business rules.

The event package change is source- and binary-breaking for external listeners.
That break is accepted because all affected plugins are controlled by the same
operator. The migration table provides the complete required update.

## Acceptance evidence

Completion requires all of the following:

- zero forbidden framework imports under `domain/**`;
- no production classes under `domain.events`;
- all 17 replacement Bukkit events available under `api.events`;
- every existing event producer and listener compiling against the new API;
- the five Bukkit vault classes located outside domain and application;
- executable enforcement of the documented forbidden prefixes;
- event and vault regression tests passing; and
- the full repository verification suite and documentation lint passing.

No new event bus, compatibility shim, schema migration, or vault behavior change
is part of this work.
