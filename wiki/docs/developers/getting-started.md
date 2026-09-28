---
title: Developer getting started
audience: dev
topic: dev-getting-started
summary: Build, test, and navigate the LumaGuilds 3.0 codebase.
keywords: [development, build, gradle, java, paper]
related: [architecture, api-reference, migration-safety]
updated: 2026-09-28
---

# Developer getting started

LumaGuilds 3.0 targets **Paper 26.2**, **Java 25**, and **Kotlin 2.3.20**. Use the checked-in Gradle wrapper (Gradle 9.1.0).

## Local prerequisites

- JDK 25
- Git
- the repository's local compile jars in `libs/` for RoseChat, CombatLogX API, and EnthusiaPlaytime API

## Useful commands

```bash
./gradlew testClasses
./gradlew test
./gradlew shadowJar
```

The shaded artifact is written to `build/libs/LumaGuilds-<version>.jar`.

## Before a PR

Run the relevant focused tests first, then `test`, `shadowJar`, and `git diff --check`. Architecture rules are enforced by Konsist tests.

Start with [Architecture](architecture.md) before adding a new service or repository, and read [Migration safety](migration-safety.md) before changing persistence.
