plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "8.3.11"
    idea
}

group = "net.lumalyte.lg"
version = findProperty("releaseVersion")?.toString() ?: "3.0.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.opencollab.dev/main/")
    maven("https://nexus.scarsz.me/content/groups/public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven {
        name = "artillex-studios"
        url = uri("https://repo.artillex-studios.com/releases/")
    }
    maven {
        name = "sirblobman-public"
        url = uri("https://nexus.sirblobman.xyz/public/")
    }
    maven {
        name = "nexomc-releases"
        url = uri("https://repo.nexomc.com/releases")
    }
    maven {
        name = "lunarclient-public"
        url = uri("https://repo.lunarclient.dev/")
    }
    // Sonatype OSS snapshots — kept as last-resort fallback because
    // the domain has frequent outages (HTTP 504).  All key SNAPSHOT
    // deps are covered by dedicated repos above:
    //   - PlaceholderAPI → JitPack
    //   - Geyser / Floodgate / Cumulus → OpenCollab
    //   - ACF / IDB → Aikar
    //   - CombatLogX → SirBlobman
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    testImplementation(kotlin("test"))
    // InventoryFramework expects this server-provided library when constructing real GUIs.
    testRuntimeOnly("commons-lang:commons-lang:2.6")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1")
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation("com.github.retrooper:packetevents-spigot:2.11.2")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
    testImplementation("com.lemonappdev:konsist:0.17.3")

    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")
    compileOnly("com.discordsrv:discordsrv:1.28.0")
    // Provider-neutral EnthusiaStaff lifecycle API; CI builds the exact pinned source into libs/.
    // Runtime classes are supplied by the EnthusiaStaff plugin through the soft dependency.
    val enthusiaStaffModerationApi = files("libs/EnthusiaStaff-moderation-api.jar")
    compileOnly(enthusiaStaffModerationApi)
    testImplementation(enthusiaStaffModerationApi)
    shadow("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("org.slf4j:slf4j-nop:2.0.13")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.2")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("co.aikar:idb-core:1.0.0-SNAPSHOT")
    implementation("com.github.stefvanschie.inventoryframework:IF:0.12.2-SNAPSHOT")
    implementation("io.insert-koin:koin-core:4.0.2")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")

    // nexus-i18n for LangService — all player-facing strings in en_US.yml
    implementation("com.github.BadgersMC.Nexus:nexus-i18n:v2.1.1")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("com.github.placeholderapi:placeholderapi:2.11.6")
    compileOnly("com.artillexstudios:AxKothAPI:4")
    // The Koin graph smoke test constructs every service (incl. placeholder + KOTH
    // hooks) — these APIs must be on the test classpath too.
    testImplementation("com.github.placeholderapi:placeholderapi:2.11.6")
    testImplementation("com.artillexstudios:AxKothAPI:4")
    // RoseChat is required at compile-time for the GuildChatListener channel switch.
    // Drop the built jar into libs/ from the RoseChat project (libs/ is gitignored).
    val roseChatApi = files(findProperty("roseChatJar") ?: "libs/RoseChat-RC-2.jar")
    compileOnly(roseChatApi)
    compileOnly(files("libs/EnthusiaPlaytime-api.jar"))
    testCompileOnly(roseChatApi)
    testRuntimeOnly(findProperty("roseChatRuntimeJar")?.let { files(it) } ?: roseChatApi)
    testImplementation(files("libs/EnthusiaPlaytime-api.jar"))

    // Nexo API (com.nexomc.nexo.api.NexoItems) for custom item textures/icons.
    // compileOnly — Nexo bundles its API at runtime; shading would conflict.
    compileOnly("com.nexomc:nexo:1.21.0")

    // LiteBans API (litebans.api.* — Events/Entry/Database) for Guild Strikes.
    // Resolved from JitPack (official API repo: gitlab.com/ruany/LiteBansAPI),
    // so CI needs no local jar — see the API wiki's Gradle setup. Must stay
    // compileOnly: LiteBans bundles the API at runtime, shading it would cause
    // a version conflict. jitpack.io is declared in repositories above.
    compileOnly("com.gitlab.ruany:LiteBansAPI:0.6.1")
    // The Koin graph smoke test constructs every service, including the LiteBans
    // strike hook — the API must be on the test classpath too.
    testImplementation("com.gitlab.ruany:LiteBansAPI:0.6.1")

    // geyser
    compileOnly("org.geysermc.geyser:api:2.9.4-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT") {
        // The legacy Geyser implementation embeds Gson 2.3.1; only Floodgate's API is required.
        exclude(group = "org.geysermc.geyser", module = "common")
    }
    compileOnly("org.geysermc.cumulus:cumulus:2.0.0-SNAPSHOT")
    // Exercise actual Bedrock form responses in the reward confirmation contracts.
    testImplementation("org.geysermc.cumulus:cumulus:2.0.0-SNAPSHOT")

    //combatlogX api
    compileOnly("com.github.sirblobman.api:core:2.9-SNAPSHOT")
    compileOnly(files("libs/CombatLogX-api.jar"))

    // Lunar Client Apollo API
    compileOnly("com.lunarclient:apollo-api:1.2.3")
    compileOnly("com.lunarclient:apollo-extra-adventure4:1.2.3")

}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// Keep local tooling trees (e.g. Claude worktree copies) out of the IDE module so
// Kotlin does not see duplicate sources like TeleportationService.kt twice.
idea {
    module {
        excludeDirs.add(file(".claude"))
        excludeDirs.add(file(".worktrees"))
    }
}

tasks.test {
    useJUnitPlatform()
    val testMariaPort = providers.environmentVariable("GUILD_SHOP_XP_TEST_MARIA_PORT")
    inputs.property("guildShopXpTestMariaPort", testMariaPort.orElse(""))
    testMariaPort.orNull?.let { environment("GUILD_SHOP_XP_TEST_MARIA_PORT", it) }
    outputs.upToDateWhen { !testMariaPort.isPresent }
    // This optional integration profile must not reuse a skipped/stale result when
    // the companion path or the JAR's contents change.
    val marketApiJar = providers.environmentVariable("MARKET_API_JAR").orElse("")
    inputs.property("marketApiJarPath", marketApiJar)
    inputs.files(marketApiJar.map { path -> if (path.isBlank()) files() else files(path) })
        .withPropertyName("marketApiJarContents")
    environment("MARKET_API_JAR", marketApiJar.get())
}

// Explicit opt-in: the same ownership contract runs against a disposable loopback
// MariaDB instance. Ordinary test runs remain self-contained SQLite tests.
tasks.register<Test>("mariaDbRewardTest") {
    group = "verification"
    description = "Run Chapter 2 reward and XP boost contracts against a disposable local MariaDB instance"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*Reward*RepositorySQLTest")
        includeTestsMatching("*ExperienceBoostRepositorySQLTest")
        includeTestsMatching("*GuildCreation*SQLTest")
        includeTestsMatching("*GuildCosmeticUnlockRepositorySQLiteTest")
        includeTestsMatching("*GuildInsertColumnOrderTest")
        includeTestsMatching("*GuildThemeUpdateSQLTest")
    }
    doFirst {
        val port = providers.gradleProperty("mariaDbTestPort").orNull
            ?: error("Supply -PmariaDbTestPort for a disposable local MariaDB instance")
        require(port.toInt() in 1024..65535 && port.toInt() != 3306)
        systemProperty("lg.test.mariadb.port", port)
    }
    outputs.upToDateWhen { false }
    shouldRunAfter(tasks.test)
}

tasks.shadowJar {
    archiveBaseName.set("LumaGuilds")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())

    mergeServiceFiles()

    relocate("com.zaxxer.hikari", "net.lumalyte.lg.shaded.hikari")
    relocate("co.aikar.commands", "net.lumalyte.lg.shaded.acf")
    relocate("co.aikar.idb", "net.lumalyte.lg.shaded.idb")

    exclude("META-INF/maven/**")
    exclude("META-INF/versions/**")
    exclude("**/module-info.class")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
