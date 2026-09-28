---
title: Master diagram
audience: dev
topic: master-diagram
summary: High-level data and dependency flow through LumaGuilds.
keywords: [architecture, diagram, flow]
related: [architecture, domain, application, infrastructure, interaction]
updated: 2026-09-28
---

# Master diagram

```mermaid
flowchart LR
  Player[Java / Bedrock player] --> Interaction[Interaction\ncommands + menus + forms]
  Admin[Admin / console] --> Interaction
  Interaction --> Application[Application\nuse cases + service contracts]
  Application --> Domain[Domain\nentities + rules + values]
  Infrastructure[Infrastructure\nBukkit + SQL + integrations] --> Application
  Infrastructure --> Domain
  DI[DI / composition root] --> Interaction
  DI --> Infrastructure
  API[Public API\nServicesManager + Bukkit events] --> Application
  DB[(SQLite / MariaDB)] <--> Infrastructure
  External[RoseChat / Vault / PAPI / Geyser / Nexo / etc.] <--> Infrastructure
```

Dependency direction for the enforced core is inward: infrastructure → application → domain.

Player input enters through interaction; persistence and platform effects leave through infrastructure. Cross-plugin consumers should use the public API or Bukkit events instead of reaching into Koin/internal repositories.
