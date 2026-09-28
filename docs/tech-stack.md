# LumaGuilds — Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.3.20 (JVM), JDK 25 toolchain |
| Build | Gradle 9.1.0 + Shadow 8.3.11 — archive `LumaGuilds.jar`; relocates `com.zaxxer.hikari`, `co.aikar.commands`, `co.aikar.idb` to `net.lumalyte.lg.shaded.*` |
| Server API | Paper 26.2 `paper-api:26.2.build.129-stable` (compileOnly + testImplementation) |
| Commands | ACF `acf-paper:0.5.1-SNAPSHOT` + `idb-core:1.0.0-SNAPSHOT` (`interaction/commands/`, `@CommandPermission` nodes) |
| DI | Koin `koin-core:4.0.2` (`di/Modules.kt`: coreModule, claimsModule, guildsModule, socialModule, progressionModule) |
| GUIs | InventoryFramework `0.12.2-SNAPSHOT` (`interaction/menus/`) with a compatibility layer for positioned panes |
| Persistence | MariaDB `mariadb-java-client:3.3.2` + HikariCP `5.1.0` (shaded); sqlite-jdbc 3.45.1.0 on test classpath |
| Async | kotlinx-coroutines-core/jdk8 `1.10.2` |
| Bedrock | Geyser api `2.9.4-SNAPSHOT`, Floodgate api `2.2.5-SNAPSHOT`, Cumulus `2.0.0-SNAPSHOT` (forms in `interaction/menus/bedrock/` + `BedrockLocalizationServiceFloodgate`) |
| Chat/display | Paper-provided Adventure/MiniMessage; PlaceholderAPI `2.11.6` (compileOnly); Vault `1.7` (compileOnly) |
| Integrations | RoseChat RC-2 (local jar `libs/`, compileOnly — GuildChatListener channel switch), Nexo `1.21.0` (compileOnly, soft dependency), LiteBansAPI `0.6.1` (compileOnly, JitPack), CombatLogX api, LunarClient Apollo `1.2.3`, AxKothAPI `4` |
| i18n | `lang/defaults/*.properties` — actually serves claims UI + Bedrock forms only; guild/bank/war/admin commands hardcode `§`-strings (REQ-016 migrates them) |
| Tests | kotlin-test, JUnit Jupiter 5.8.1, MockK 1.13.11, MockBukkit 4.116.1 (26.2), sqlite-jdbc; Konsist `0.17.3` added in PR-0 |
| Out of stack | Nexus framework, Flyway / DB migration frameworks, external web servers |

## CI

`.github/workflows/` — Gradle build + shadowJar artifact on push/PR. Konsist runs as part of `./gradlew test` (LayerRulesTest under `src/test/kotlin/net/lumalyte/lg/architecture/`).
