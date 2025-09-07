plugins {
    kotlin("jvm") version "2.0.0"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "net.lumalyte.lg"
version = "0.4.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/central")
    }
    maven {
        url = uri("https://repo.aikar.co/content/groups/aikar/")
    }
    maven {
        url = uri("https://jitpack.io")
    }
    maven {
        name = "codemc-snapshots"
        url = uri("https://repo.codemc.io/repository/maven-snapshots/")
    }

}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    shadow("org.jetbrains.kotlin:kotlin-stdlib")
    implementation ("org.slf4j:slf4j-nop:2.0.13")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("co.aikar:idb-core:1.0.0-SNAPSHOT")
    implementation("com.github.stefvanschie.inventoryframework:IF:0.11.3")
    implementation("io.insert-koin:koin-core:4.0.2")
    implementation("org.json:json:20240303")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.11.6")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")

    // Adventure API for modern text formatting (included with PaperMC)
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

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
    archiveVersion.set("0.4.0")

    // Include runtime dependencies
    mergeServiceFiles()

    // Relocate dependencies to avoid conflicts with other plugins
    // Note: Not relocating coroutines and Koin as they are commonly used and shouldn't conflict
    relocate("com.zaxxer.hikari", "net.lumalyte.lg.shaded.hikari")
    relocate("co.aikar.commands", "net.lumalyte.lg.shaded.acf")
    relocate("co.aikar.idb", "net.lumalyte.lg.shaded.idb")

    // Exclude files that cause conflicts
    exclude("META-INF/maven/**")
    exclude("META-INF/versions/**")
    exclude("**/module-info.class")
}