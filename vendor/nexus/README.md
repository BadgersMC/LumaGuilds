# Nexus source used by Guilds

The Guilds wrapper builds Nexus from this directory by default. REVISION records the exact upstream commit and must match nexusCommit in the Guilds gradle.properties. The four module directories, root build script, Detekt config and MIT license come from that Git revision. The included settings.gradle.kts keeps only the four modules Guilds consumes. Nexus core uses JDK 21 and the Paper modules and Guilds use JDK 25.

To refresh, export these paths from the reviewed Nexus commit with git archive into a fresh directory and replace the snapshot, including deletions. Filter the upstream settings file to the same modules, update both pins, then run the Guilds tests and packaging checks from a clean checkout. A developer testing a separate checkout can pass -PnexusSourceDir=/path/to/Nexus at the pinned commit.
