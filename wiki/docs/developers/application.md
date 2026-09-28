---
title: Application layer
audience: dev
topic: application
summary: Use cases, service contracts, persistence ports, actions, and results.
keywords: [application, services, actions, repositories]
related: [architecture, domain, infrastructure]
updated: 2026-09-28
---

# Application layer

Location: `src/main/kotlin/net/lumalyte/lg/application/`.

Application code coordinates domain behavior through interfaces. It contains service contracts, persistence ports, actions, results, application events, and use-case utilities.

The architecture test allows application to depend on **domain only**.

Typical pattern:

1. Interaction code invokes an application service/action.
2. The application layer validates/co-ordinates domain work.
3. Persistence or platform work is requested through an interface.
4. Infrastructure supplies the implementation through DI.

Keep Bukkit, SQL, Koin, and third-party plugin calls out of this layer. Define a port/interface here and implement it in infrastructure instead.
