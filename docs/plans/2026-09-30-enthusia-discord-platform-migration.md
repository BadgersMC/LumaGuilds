# Enthusia Discord Platform Migration

Tracking umbrella: `wsg138/EnthusiaStaff#264`.

## Goal

Migrate LumaGuilds Discord role management away from DiscordSRV and onto the provider-neutral Enthusia Discord platform without changing guild business rules.

## Current boundary

LumaGuilds currently owns guild-role policy and persists the Discord role ID for each guild. DiscordSRV is used as the infrastructure adapter for account-link lookup and Discord role creation/mutation.

The migration must keep the existing application boundary (`DiscordGuildRoleGateway`) and replace only the infrastructure implementation once the Enthusia platform contract is stable.

## Target design

- LumaGuilds continues deciding when a guild role should exist and which Minecraft players should hold it.
- LumaGuilds does not own a Discord Gateway/JDA connection.
- LumaGuilds does not depend on DiscordSRV after cutover.
- LumaGuilds does not depend on EnthusiaStaff moderation internals.
- The Enthusia Discord platform resolves Minecraft UUIDs through canonical Enthusia account links and performs Discord role mutations.
- Managed role ownership is namespaced so LumaGuilds can only mutate roles it owns.
- Unmanaged Discord roles are never removal candidates.

Expected adapter direction:

```text
GuildDiscordRoleService
        |
DiscordGuildRoleGateway
        |
EnthusiaDiscordGuildRoleGateway
        |
provider-neutral Enthusia Discord platform contract
```

## Compatibility requirements

The replacement must preserve the observable behavior of the existing DiscordSRV gateway:

- ensure/create the configured guild role;
- grant the role for a Minecraft UUID;
- revoke the role for a Minecraft UUID;
- revoke unexpected members during reconciliation;
- delete a Luma-owned role when appropriate;
- handle unavailable Discord infrastructure without breaking unrelated guild behavior;
- retain current serialization/reconciliation protections against role-update races.

One Discord account may be linked to multiple Minecraft accounts. Desired membership must therefore be calculated without accidentally revoking a role while another linked Minecraft account still qualifies.

## Checkpoint 1: provider-neutral unlink identity

The first migration checkpoint intentionally leaves DiscordSRV operational while removing a provider-specific identity leak from application orchestration.

- `DiscordAccountReference` is a bounded printable opaque account reference; LumaGuilds does not parse provider-specific identity formats.
- `GuildDiscordRoleService` now performs unlink cleanup through the opaque reference overload.
- `DiscordGuildRoleGateway` exposes `revokeRoleForAccount(...)` as the provider-neutral cleanup seam.
- the DiscordSRV adapter alone unwraps the reference into its legacy Discord snowflake;
- the raw `String` cleanup overload remains temporarily as a compatibility seam for existing callers/test doubles and will be removed after consumers migrate;
- focused validation covers opaque-value preservation and malformed/unbounded reference rejection.

This checkpoint does **not** add an Enthusia transport client, change the configured provider, or authorize DiscordSRV removal.

## Migration sequence

1. Keep the existing DiscordSRV implementation working while the Enthusia platform contract is introduced.
2. Add an Enthusia-backed adapter behind `DiscordGuildRoleGateway`.
3. Add contract-focused tests for success, rejection, unavailable platform, retry, stale-link, duplicate/multi-account, and unexpected-member reconciliation behavior.
4. Run the adapter in a non-production validation environment and compare resulting managed-role membership with the existing DiscordSRV implementation.
5. Switch configuration to the Enthusia adapter only after parity is established.
6. Remove direct DiscordSRV/JDA dependencies from LumaGuilds only after the replacement path is accepted.

## Non-goals for this PR

- Do not redesign guild progression or role-unlock policy.
- Do not move guild business logic into EnthusiaStaff.
- Do not make Discord roles authoritative for Minecraft permissions or staff authorization.
- Do not remove DiscordSRV from the network as part of the initial Luma adapter PR.
- Do not perform production Discord mutations during development.

## Quality gates

Before merge:

- full Gradle build/tests pass;
- focused role synchronization tests cover success, rejection, retry/outage, reconciliation conflict, and multi-linked-account behavior;
- changed methods remain within project complexity limits where practical;
- provider-specific infrastructure remains separated from application/domain policy;
- no internal Enthusia persistence records are exposed across the public contract;
- hosted analyzers report zero new valid findings.
