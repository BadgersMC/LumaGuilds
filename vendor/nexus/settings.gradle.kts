pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "nexus"

include("nexus-core")
include("nexus-paper")
include("nexus-resources")
include("nexus-i18n")
// Root project is a pure aggregator — no source, no publishable artifact.
