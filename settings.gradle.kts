rootProject.name = "LumaGuilds"
providers.gradleProperty("nexusSourceDir").orNull?.let { path ->
    val source = file(path)
    val expected = providers.gradleProperty("nexusCommit").get()
    val revision = providers.exec {
        workingDir(source)
        commandLine("git", "rev-parse", "HEAD")
    }.standardOutput.asText.get().trim()
    require(revision == expected) { "Nexus checkout is at $revision. Guilds requires $expected." }

    includeBuild(source) {
        dependencySubstitution {
            for (name in listOf("nexus-core", "nexus-paper", "nexus-i18n")) {
                substitute(module("com.github.BadgersMC.Nexus:$name")).using(project(":$name"))
            }
        }
    }
}
