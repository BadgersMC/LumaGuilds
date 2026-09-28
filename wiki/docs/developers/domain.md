---
title: Domain layer
audience: dev
topic: domain
summary: Pure guild business entities, values, rewards, and domain services.
keywords: [domain, entities, values, rewards]
related: [architecture, application]
updated: 2026-09-28
---

# Domain layer

Location: `src/main/kotlin/net/lumalyte/lg/domain/`.

The domain layer contains pure business concepts: guilds, ranks, relations, wars, quests, progression values, reward entitlements, prestige state, and domain-level generation/calculation logic.

It must remain framework-free. Do not import Bukkit/Paper, Koin, ACF, Adventure, SQL, or plugin APIs here.

Good domain code:

- validates invariants;
- calculates outcomes from plain values;
- models state with entities/value objects;
- exposes deterministic logic that is easy to unit test.

If a class needs a Bukkit `Player`, SQL connection, scheduler, logger, or plugin instance, it does not belong in domain.
