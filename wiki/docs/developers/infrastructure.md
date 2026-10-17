---
title: Infrastructure layer
audience: dev
topic: infrastructure
summary: Bukkit adapters, persistence, migrations, integrations, schedulers, and concrete services.
keywords: [infrastructure, sql, bukkit, migrations, integrations]
related: [architecture, application, migration-safety]
updated: 2026-09-28
---

# Infrastructure layer

Location: `src/main/kotlin/net/lumalyte/lg/infrastructure/`.

Infrastructure implements the application ports using Paper/Bukkit, SQLite/MariaDB, external plugins, schedulers, web endpoints, PlaceholderAPI, Vault, Geyser/Floodgate, and other runtime systems.

Major areas include:

- `persistence/` — repositories, storage backends, migrations;
- `services/` — concrete application service implementations;
- `listeners/` — Bukkit/Paper event bridges;
- `placeholders/` — PlaceholderAPI expansion;
- `vault/`, `web/`, `i18n/`, and integration adapters.

Infrastructure may depend on application and domain, but core business policy should not leak into SQL/Bukkit adapters when it can live in the inner layers.
