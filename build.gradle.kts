plugins {
    kotlin("jvm") version "2.0.0"
    id("com.gradleup.shadow") version "8.3.6"
}

group = "net.lumalyte.lg"
version = "2.0.0" // Hytale port

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
    maven {
        name = "lunarclient-public"
        url = uri("https://repo.lunarclient.dev/")
    }
}

dependencies {
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("org.xerial:sqlite-jdbc:3.45.1.0")

    // Hytale Server API
    compileOnly(files("D:/BadgersMC-Dev/hytale-server/Server/HytaleServer.jar"))

    // Kotlin
    shadow("org.jetbrains.kotlin:kotlin-stdlib")

    // Core Dependencies (Platform-Agnostic)
    implementation("org.slf4j:slf4j-nop:2.0.13")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.2")
    implementation("co.aikar:idb-core:1.0.0-SNAPSHOT")
    implementation("io.insert-koin:koin-core:4.0.2")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.2")
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("LumaGuilds-Hytale")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())

    mergeServiceFiles()

    relocate("com.zaxxer.hikari", "net.lumalyte.lg.shaded.hikari")
    relocate("co.aikar.idb", "net.lumalyte.lg.shaded.idb")
    relocate("com.google.gson", "net.lumalyte.lg.shaded.gson")

    exclude("META-INF/maven/**")
    exclude("META-INF/versions/**")
    exclude("**/module-info.class")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("manifest.json") {
        expand(props)
    }
}

// Task to copy built JAR to Hytale server mods folder for testing
tasks.register<Copy>("copyToServer") {
    group = "hytale"
    description = "Copies the built JAR to the Hytale server mods folder"

    dependsOn(tasks.shadowJar)

    from(tasks.shadowJar.get().archiveFile)
    into("D:/BadgersMC-Dev/hytale-server/Server/mods")

    doLast {
        println("✅ Copied ${tasks.shadowJar.get().archiveFileName.get()} to Hytale server mods folder")
    }
}

// Task to build and copy in one command
tasks.register("deployToServer") {
    group = "hytale"
    description = "Builds the plugin and deploys it to the Hytale server"

    dependsOn("copyToServer")

    doLast {
        println("✅ LumaGuilds deployed to Hytale server!")
        println("   Start the server with: D:/BadgersMC-Dev/hytale-server/Server/start-server.bat")
    }
}
