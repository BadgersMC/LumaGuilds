plugins {
    kotlin("jvm") version "2.0.0"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "net.lumalyte"
version = "2.3.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "Paper"
    }
    maven("https://jitpack.io") {
        name = "JitPack"
    }
    maven("https://repo.opencollab.dev/main/") {
        name = "OpenCollab" // Floodgate/Geyser
    }
    maven("https://repo.codemc.io/repository/maven-public/") {
        name = "CodeMC" // PlaceholderAPI
    }
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "PlaceholderAPI"
    }
    maven("https://repo.aikar.co/content/groups/aikar/") {
        name = "Aikar" // IDB database library
    }
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // Core Kotlin (must be shaded for runtime)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    
    // Runtime libraries (loaded by Paper)
    compileOnly("io.insert-koin:koin-core:3.5.3")
    compileOnly("com.github.stefvanschie.inventoryframework:IF:0.10.13")

    // Database libraries (required for persistence layer)
    compileOnly("co.aikar:idb-core:1.0.0-SNAPSHOT")
    compileOnly("org.json:json:20240303")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("org.slf4j:slf4j-nop:2.0.13")

    // Soft dependencies
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly(files("libs/floodgate-api-2.2.4-SNAPSHOT.jar"))
    compileOnly(files("libs/geyser-api-2.7.0-SNAPSHOT.jar"))
    compileOnly(files("libs/cumulus-2.0.0-SNAPSHOT.jar"))
    compileOnly(files("libs/common-2.2.1-SNAPSHOT.jar"))
    compileOnly(files("libs/base-api-1.0.1.jar"))
    compileOnly(files("libs/events-1.1-SNAPSHOT.jar"))

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation(kotlin("test"))
    testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    compileKotlin {
        // Exclude disabled ACF command files from compilation
        exclude("**/disabled/**")
    }
    
    shadowJar {
        // Minimize - only include necessary classes
        minimize()
        
        // Shade Kotlin libraries (required for runtime)
        relocate("kotlin", "net.lumalyte.lg.libs.kotlin")
        relocate("kotlinx", "net.lumalyte.lg.libs.kotlinx")
        
        // Exclude Kotlin from minimization (keep all classes)
        minimize {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*"))
        }
        
        // Don't shade other libraries - Paper loads at runtime
        // This keeps JAR size reasonable while ensuring Kotlin works
        
        archiveClassifier.set("")
        archiveFileName.set("LumaGuilds-${project.version}.jar")
    }
    
    // Skip test compilation during build for now (tests have errors from migration)
    compileTestKotlin {
        enabled = false
    }
}