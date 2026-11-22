plugins {
    kotlin("jvm") version "2.3.20" apply false
    kotlin("plugin.serialization") version "2.3.20" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("com.gradle.plugin-publish") version "1.3.1" apply false
}

group = "net.badgersmc"
version = "2.3.0"

/**
 * Root project is a pure multi-module aggregator. All publishable artifacts
 * live under the `nexus-*` sub-modules. The Hytale support that used to live
 * here was removed in 2.0.0 — see the changelog / README for the migration
 * story (TL;DR: there is no more Hytale code anywhere in this repo).
 */

allprojects {
    repositories {
        mavenCentral()
        // Opt-in to a locally-published Nexus snapshot via -PuseMavenLocal=true.
        // Off by default so CI builds never pick up stale local jars.
        if (providers.gradleProperty("useMavenLocal").orNull == "true") {
            mavenLocal()
        }
    }
}

/**
 * Inject the GitHub Packages publish repository into every sub-module that
 * applies `maven-publish`. Sub-modules keep their own `publications` blocks
 * (so they can pin artifactIds), but the repo wiring + credentials live in
 * one place here so the CI workflow only needs one set of secrets.
 */
subprojects {
    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/BadgersMC/Nexus")
                    credentials {
                        username = project.findProperty("gpr.user") as String?
                            ?: System.getenv("GITHUB_ACTOR")
                        password = project.findProperty("gpr.token") as String?
                            ?: System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }

    // Detekt runs standalone; harmless on non-Kotlin modules, no-op when there are no sources.
    // The plugin already wires `detekt` into the `check` lifecycle by default.
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = false
        allRules = false
    }

    // Detekt 1.23.8's embedded Kotlin compiler accepts JVM targets only through 22.
    // This affects static analysis only; production Kotlin/Java bytecode still targets Java 25.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "22"
    }
}
