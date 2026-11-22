plugins {
    kotlin("jvm")
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    if (providers.gradleProperty("useMavenLocal").orNull == "true") {
        mavenLocal() // opt-in via -PuseMavenLocal=true — never on CI
    }
}

dependencies {
    api(project(":nexus-core"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("io.github.classgraph:classgraph:4.8.174")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
    // Paper API needed at test runtime for PaperArgumentResolvers Player resolver
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "net.badgersmc"
            artifactId = "nexus-paper"
            version = rootProject.version.toString()
            from(components["java"])
        }
    }
}
