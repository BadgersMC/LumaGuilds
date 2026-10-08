# Pinned AxKoth dependency transport recovery

## Spec and boundaries

REQ-142 remains authoritative: use only AxKothAPI 4, axapi 1.4.8 and the exact original POMs/JARs listed in ci/axkoth/artifacts.sha256. Verify restored caches and downloads before Gradle. Keep bounded artifact-only retries; never retry compilation/tests, alter compile-only/shading behavior or publish a replacement API. Initial failure evidence uses #216 a2bffa1. Delivery uses the sanitized #216 head 87f78d3 and canonical main e11fb16; no pre-cleanup history is merged back into the public branch.

## Prove

Hosted #216 run 37827256985, job 113483193997 failed at dependency preparation, before Gradle. All six HTTP/1.1 curl attempts ended with error 18 and incomplete bodies. The first transfer was missing 292001 bytes; subsequent attempts missed up to 2053281 bytes. Local direct downloads reproduce error 18 and the common 147114-byte prefix of the expected 2192203-byte AxKothAPI JAR. Range requests receive HTTP 200 and another incomplete prefix, so range resume is unsupported. Cache-busting queries, headers and protocol changes did not consistently recover the transfer. Occasional full transfers match the unchanged pinned SHA-256; a single successful request is not reliability proof.

## Engine, architecture and refine

The preparation helper will retry recoverable transfer failures for at most 32 attempts and 120 seconds per artifact, with each curl request capped at 15 seconds. Complete checksum mismatches and HTTP authorization/not-found errors fail immediately. Downloads remain temporary until SHA-256 verification succeeds, then replace the destination atomically. Valid cache hits require no network. Retry exhaustion must preserve an existing artifact and remove temporary bytes. The focused proof covers recovery beyond the previous six attempts, finite exhaustion, deadline enforcement, permanent errors, checksum rejection and verified cache reuse.

This is CI infrastructure work: plugin-runtime behavioral red/green does not apply. The six-attempt baseline fails the recovery-beyond-six regression; all eight final transport/cache regressions pass. A fresh upstream preparation probe still exhausted 32 attempts: the upstream transfer problem remains an external risk until hosted verification succeeds, and no reliable-network claim is made. Valid cache verification is independent of that network probe.

Canonical main's artwork cleanup was subsequently rewritten to `e11fb16`, with the pending stack rewritten to `87f78d3`. The repair is reapplied on that sanitized history and includes current main. No resource-pack files are tracked. The complete pre-reconciliation pack is preserved privately outside the repository. HolidayStylePackTest now validates that separately supplied pack through LUMAGUILDS_PRIVATE_ASSET_ROOT; an explicitly configured invalid pack fails all four checks, while the preserved valid private pack passes all four. An absent private profile is reported as an external integration skip. Gradle tracks the configured path and contents to avoid stale cached test results. No menu, texture or server rendering behavior is changed.

Executable transport/cache regression evidence and plugin regression/two-way companion checks remain required. EARS/state helpers and Codacy MCP CLI are unavailable; manual SPEAR evidence and final hosted Codacy results will be recorded. No GitHub merge, production operation or Minecraft client acceptance is authorized or claimed.
