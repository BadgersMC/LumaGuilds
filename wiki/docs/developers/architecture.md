---
title: Architecture
audience: dev
topic: architecture
summary: LumaGuilds layer boundaries and dependency direction.
keywords: [architecture, domain, application, infrastructure, interaction]
related: [domain, application, infrastructure, interaction, master-diagram]
updated: 2026-09-28
---

# Architecture

LumaGuilds uses a layered architecture with executable dependency rules.

## Enforced core layers

`LayerRulesTest` enforces:

- **Domain** depends on nothing outside the domain/Kotlin standard library.
- **Application** depends only on domain.
- **Infrastructure** depends only on application and domain.

The domain layer is also forbidden from importing Bukkit, Koin, ACF, or Adventure packages.

## Edge layers

`interaction` contains commands, Java inventory menus, Bedrock forms, help rendering, and user-facing routing. `api` is the stable cross-plugin boundary. `di` is the composition root.

## Rule of thumb

Business rules belong in domain/application. Bukkit, SQL, plugin integrations, schedulers, and external APIs belong in infrastructure. UI/commands should orchestrate services rather than own persistence or domain policy.

See the [Master diagram](master-diagram.md) for the flow.
