# LumaGuilds Network Federation and Gamemode Isolation

Date: 2026-09-20

Status: Approved architecture direction; future implementation

Scope: future multi-gamemode Enthusia network support, local LumaGuilds instances, optional guild federation, and explicitly network-scoped systems

## 1. Purpose

LumaGuilds is currently field-tested on the Enthusia semi-anarchy SMP, but the plugin is intended to support additional, substantially different gamemodes on the same Velocity network.

A future gamemode may enable claims, Nexo custom content, AuraSkills, and progression rules that do not belong on the SMP. A level 100 SMP guild must not automatically become level 100 on another gamemode, and local economies, wars, homes, claims, perks, Elo, and ordinary weekly quests must not leak between servers.

At the same time, completely isolated guild systems would miss an opportunity to make the network feel connected. The approved direction is therefore **federation rather than shared progression**.

The core rule is:

> **Guild progression belongs to the gamemode. Guild identity may optionally federate across the network. Only explicitly network-scoped systems cross that boundary.**

This design keeps each LumaGuilds installation authoritative for its own gamemode while leaving room for network-wide guild identity, quests, events, cosmetics, and other deliberately shared systems.
## 2. Terminology

### 2.1 Local LumaGuilds instance

One LumaGuilds installation attached to one gamemode/server domain. It owns that gamemode's guild membership and progression.

Examples:
- Enthusia SMP LumaGuilds instance
- future claims/Nexo/AuraSkills gamemode LumaGuilds instance

### 2.2 Local guild

A guild created inside one LumaGuilds instance. Its UUID, roster, ranks, progression, wars, economy, and other gameplay state belong to that instance.

A local guild can operate forever without participating in network federation.

### 2.3 Network guild

An optional network-level organization that federates one or more local guilds. It is not a replacement for local guilds and does not own their progression.

A network guild is an umbrella identity for explicitly network-scoped features.

### 2.4 Federation link

A durable association from a local guild to at most one network guild at a time.

The link does **not** merge databases, rosters, levels, economies, permissions, or progression.

### 2.5 Local and network scope

A local-scoped operation is authoritative only inside its LumaGuilds instance. A network-scoped operation is explicitly designed to aggregate or coordinate across federation links.
## 3. Non-negotiable invariants

1. **Player guild membership is local to a gamemode.**
   A player may belong to different guilds on different servers.

2. **There is no global player-guild allegiance.**
   Joining a guild on one gamemode never auto-joins, blocks, removes, or changes membership on another.

3. **Local progression never synchronizes implicitly.**
   Guild level, XP, gold, claims, homes, perks, Elo, wars, local quest progress, and similar state remain local.

4. **A local guild has zero or one federation link.**
   No local action may be double-attributed to multiple network guilds.

5. **A network guild may federate multiple local guilds.**
   Roster overlap is not required. The local guilds may have completely different members.

6. **Federation does not require matching rosters.**
   The SMP guild and the guild representing the same network organization on another gamemode may be different communities.

7. **Cross-server effects must be explicit.**
   A feature must declare itself network-scoped before it may read or mutate network-level state.

8. **Local authority wins for local gameplay.**
   Loss of the network federation service must not corrupt or disable ordinary local guild gameplay.

These invariants intentionally permit one player to contribute to different network guilds depending on which gamemode they are playing.
## 4. Player membership model

Example:

```text
Player: Brandon

Enthusia SMP:
  Local guild: Badgers
  Federated network guild: Badgers Coalition

Future gamemode:
  Local guild: Mercury
  Federated network guild: Mercury Network
```

This is valid even if Badgers does not play the second gamemode at all.

Activity is attributed from the bottom up:

```text
player action
    -> local guild on the current gamemode
        -> optional network guild through that local guild's federation link
```

Therefore:

```text
40 mob kills on SMP
-> Badgers SMP +40
-> Badgers Coalition +40 for an explicitly network-scoped objective

60 mob kills on the future gamemode
-> Mercury +60
-> Mercury Network +60 for that network objective
```

The player never has to choose one network-wide allegiance. Attribution follows the local guild that actually owns the activity.
## 5. Progression isolation

The following state is local by default and must not cross federation boundaries:

| State | Authority |
| --- | --- |
| Guild level / current-run XP | Local instance |
| Guild gold / economy | Local instance |
| Claims / territory | Local instance |
| Homes / teleport progression | Local instance |
| Purchased or permanent perks | Local instance |
| Seasonal Elo / rated-war history | Local instance |
| Wars / declarations / wagers | Local instance |
| Local weekly quests | Local instance |
| Local statistics / leaderboards | Local instance |
| Ranks and rank permissions | Local instance |
| Membership / ownership | Local instance |

A network guild may expose aggregate read models, but those aggregates are not authoritative replacements for local values.

Example:

```text
Badgers SMP       level 100
Badgers Kingdoms  level 17
Network identity  "Badgers Coalition"
```

The existence of the network identity does not imply a network level of 100, 17, 117, or any other derived gameplay level.

If a future network progression track exists, it must be a separate explicitly network-owned track.
## 6. Network-wide quests and events

Network quests are an intentional bridge between otherwise isolated gamemodes.

A network quest may aggregate qualifying contributions from every federated local guild:

```text
Network quest: Kill 20,000 hostile mobs

Badgers SMP contribution:       8,400
Badgers second-mode contribution: 5,100

Badgers Coalition total:       13,500
```

The local instances remain authoritative for detecting the gameplay event. The network layer receives a normalized, idempotent contribution associated with:
- source instance / gamemode;
- source local guild;
- linked network guild;
- player or actor when required for audit;
- objective/event identity;
- amount;
- stable deduplication identity.

A network outage must not rewrite local progression. Network contribution delivery must be retry-safe and deduplicated.

### 6.1 Reward semantics must be explicit

Network quests are allowed to create cross-network progression, but never implicitly.

Each network objective must declare a reward strategy, for example:
- **NETWORK_ONLY** — network title, cosmetic, reputation, Discord role, network leaderboard points;
- **EACH_LINKED_GUILD** — grant a defined reward independently to each participating local guild;
- **CONTRIBUTING_GUILD** — reward only the local guild(s) that supplied contribution;
- **CONTRIBUTION_WEIGHTED** — distribute a defined reward according to contribution.

The exact reward catalogue is future design work. The architectural requirement is that the strategy is explicit and auditable.
## 7. Federation lifecycle

Federation is optional and deliberate.

Creating a local guild does not automatically create a network guild.

Joining a local guild does not automatically change any federation membership.

A future federation flow should support:
- create a network guild identity;
- invite/link an existing local guild;
- prove authorization from the local guild leadership;
- accept the link from the network organization side when appropriate;
- unlink without deleting either guild;
- transfer or recover federation administration;
- audit link/unlink history.

Unlinking a local guild stops future network attribution from that guild. It must not delete local quest progress, local progression, local history, or the network guild itself.

Historical network contributions already accepted remain historical records unless an explicit administrative correction is performed.

Network naming, banner, Discord identity, and other presentation may be independent from local guild presentation. Automatic rename propagation is not assumed.

Federation should be designed as a separate service/boundary rather than placing shared tables directly inside every local LumaGuilds database.
## 8. Quest-engine consequences

PR-16 remains a **local weekly quest engine** for the current SMP. Future federation must not be required for PR-16 to function.

However, the local quest architecture should avoid choices that make future integrations difficult.

### 8.1 Namespaced target providers

Quest targets should be provider-owned and namespaced rather than hard-coded to Bukkit enums as the domain identity.

Examples:

```text
minecraft:block/stone
minecraft:entity/zombie
nexo:block/ancient_ore
nexo:item/ruby_ingot
auraskills:skill/mining
```

The local instance builds its procedural quest pool from the providers installed on that gamemode.

Likely providers include:
- vanilla Bukkit/Paper materials, entities, recipes and other registries;
- Nexo custom items, blocks, furniture or other supported content;
- AuraSkills skills and XP events;
- claims/territory content only on gamemodes where claims are enabled.

The generator owns action/target/amount/condition composition and sanity validation. Providers expose discoverable content and progress events; they do not own weekly rotation.

### 8.2 Gamemode capabilities

Claims, Nexo, AuraSkills and future systems are capabilities of a local instance, not assumptions baked into the guild domain.

A claims-disabled SMP simply has no claims quest targets/events. A claims-enabled future mode may contribute them automatically.

Nexo is a first-class future requirement. The provider architecture must be capable of discovering newly added Nexo content without requiring a LumaGuilds release for every custom block/item addition.
## 9. Relationship to procedural quest generation

The approved quest-generation direction remains true randomness with guardrails:

```text
random action
    x random compatible target
    x random sane amount
    x zero or more random compatible conditions
```

The universe of targets is discovered at runtime wherever practical.

Manual configuration is for:
- technical blacklists;
- compatibility exceptions;
- pathological balance overrides;
- weighting/sanity policy;
- recent-history repetition policy.

It is not intended to become a hand-maintained whitelist of every Minecraft or Nexo object.

Strategic X/Z corridor conditions are part of the desired condition model so generated quests can naturally concentrate activity around player-built highways such as X=0 and Z=0.

Local quest history should reject excessive repetition without eliminating rare or chaotic objectives.

A future network quest coordinator may use the same vocabulary and target IDs, but it is a separate scope and persistence authority.

## 10. Deployment model

Initial state:

```text
Velocity network

SMP server
  -> LumaGuilds instance A
  -> local DB A

Future gamemode
  -> LumaGuilds instance B
  -> local DB B
```

Both instances remain fully functional without federation.
Future federation adds a separate boundary:

```text
LumaGuilds A ----\
                  -> network federation service/state
LumaGuilds B ----/
```

The transport could later be implemented through a Velocity-side service, message bus, dedicated network service, or another durable mechanism. This design intentionally does not select that transport yet.

The federation layer should exchange stable IDs and versioned messages rather than exposing one server's database directly to another.

## 11. Failure and abuse boundaries

The eventual implementation must account for:
- duplicate network contribution delivery;
- local server restart during delivery;
- federation unlink while contributions are in flight;
- deleted or disbanded local guilds;
- network guild deletion/recovery;
- stale federation mappings;
- malicious attempts to link a guild without leadership authority;
- different plugins/content availability between gamemodes;
- one player belonging to different federated organizations on different gamemodes;
- network objectives whose target is unavailable on one gamemode.

Unavailable content on one gamemode is not automatically an error. A network quest may intentionally accept contribution only from capable instances, but this must be declared by the objective.

No federation failure may silently grant local guild progression.

## 12. Phased direction

### Phase 1 — current work
- keep each LumaGuilds deployment local;
- finish PR-16 as a robust procedural local quest engine;
- design PR-16 target discovery around provider/namespaced identities;
- support Nexo as a required future provider boundary;
- leave claims optional and capability-driven.

### Phase 2 — second gamemode
- run a separate LumaGuilds instance and database;
- enable its own claims/progression/Nexo/AuraSkills integrations;
- preserve completely separate membership and progression;
- validate that the same plugin architecture supports both modes without mode-specific forks.

### Phase 3 — federation
- introduce optional network guild identities and federation links;
- add network presentation/read models;
- preserve local membership and progression authority.

### Phase 4 — explicitly network-scoped systems
- network quests/events;
- network cosmetics/reputation/leaderboards;
- explicitly defined cross-progression rewards where desired.
## 13. Decisions intentionally deferred

The following are not decided by this design:
- the physical transport/service used for federation;
- whether a network guild may link multiple local guilds from the same gamemode;
- network guild naming/creation UX;
- federation leadership and voting rules;
- whether network guilds have their own ranks;
- network-wide economy, if any;
- exact network quest reward strategies and values;
- how much federation metadata is surfaced through Discord;
- whether network-level wars or diplomacy ever exist.

These must not be guessed during implementation.

## 14. Architectural summary

The model is deliberately asymmetric:

```text
Player
  -> local membership on each gamemode independently

Local Guild
  -> owns all local gameplay/progression
  -> optionally links to one Network Guild

Network Guild
  -> federates local organizations
  -> owns only explicitly network-scoped state

Network Quest/Event
  -> may aggregate local contributions
  -> may cross progression boundaries only through an explicit reward contract
```

This allows Enthusia to add new gamemodes without forcing players to abandon the communities that make sense on each server, while still giving LumaGuilds a path to make the entire network feel connected.

The federation layer should make every gamemode feel supported by LumaGuilds rather than making one gamemode's progression the canonical version of the guild for everyone else.
