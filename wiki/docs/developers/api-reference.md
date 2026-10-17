---
title: API reference
audience: dev
topic: api-reference
summary: Stable cross-plugin lookup services and public Bukkit events.
keywords: [api, servicesmanager, events, integration]
related: [architecture]
updated: 2026-09-28
---

# API reference

Public integration types live under `net.lumalyte.lg.api`.

## GuildLookup

`GuildLookup` is the stable cross-plugin data/service boundary. LumaGuilds registers it in Bukkit's `ServicesManager`.

```kotlin
val lookup = Bukkit.getServicesManager().load(GuildLookup::class.java)
```

It exposes guild membership, guild summaries, rank/permission checks, member IDs, bank balance, and bounded bank deposit/withdraw operations.

Do not reach into LumaGuilds' Koin container from another plugin; classloader boundaries can make reified Koin types unsafe across plugins.

## GuildVisualLookup

`GuildVisualLookup` provides read-only leader/banner presentation data using JDK/string transfer types instead of internal Bukkit/persistence objects.

## Public events

LumaGuilds publishes Bukkit events for major lifecycle changes, including guild creation/disband, membership, ownership transfer, relation changes, level-ups, bank deposits, banners, homes, leaderboard rank changes, vault placement, and war declaration/kill/end events.

Prefer these public events over polling internal repositories.
