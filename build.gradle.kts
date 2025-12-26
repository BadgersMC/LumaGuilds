plugins {
    kotlin("jvm") version "2.0.0"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "net.lumalyte.lg"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/central")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://repo.opencollab.dev/main/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven {
        name = "artillex-studios"
        url = uri("https://repo.artillex-studios.com/releases/")
    }
    maven {
        name = "sirblobman-public"
        url = uri("https://nexus.sirblobman.xyz/public/")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.98.0")
    testImplementation("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")

    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    shadow("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("org.slf4j:slf4j-nop:2.0.13")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.2")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("co.aikar:idb-core:1.0.0-SNAPSHOT")
    implementation("com.github.stefvanschie.inventoryframework:IF:0.11.3")
    implementation("io.insert-koin:koin-core:4.0.2")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("com.github.placeholderapi:placeholderapi:2.11.6")
    compileOnly("com.artillexstudios:AxKothAPI:4")

    // geyser
    compileOnly("org.geysermc.geyser:api:2.7.0-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    compileOnly("org.geysermc.cumulus:cumulus:2.0.0-SNAPSHOT")

    //adventure
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    //combatlogX api
    compileOnly("com.github.sirblobman.api:core:2.9-SNAPSHOT")
    compileOnly("com.github.sirblobman.combatlogx:api:11.6-SNAPSHOT")

}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
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
