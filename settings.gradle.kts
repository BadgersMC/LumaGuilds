rootProject.name = "LumaGuilds"

val nexusCommit = providers.gradleProperty("nexusCommit").get()
val sourceOverride = providers.gradleProperty("nexusSourceDir").orNull
val nexusSource = file(sourceOverride ?: "vendor/nexus")
val nexusRevision = if (sourceOverride == null) {
    providers.fileContents(layout.settingsDirectory.file("vendor/nexus/REVISION")).asText.get().trim()
} else {
    providers.exec {
        workingDir(nexusSource)
        commandLine("git", "rev-parse", "HEAD")
    }.standardOutput.asText.get().trim()
}
require(nexusRevision == nexusCommit) { "Nexus source is at $nexusRevision. Guilds requires $nexusCommit." }

includeBuild(nexusSource) {
    dependencySubstitution {
        for (name in listOf("nexus-core", "nexus-paper", "nexus-i18n")) {
            substitute(module("com.github.BadgersMC.Nexus:$name")).using(project(":$name"))
        }
    }
}
