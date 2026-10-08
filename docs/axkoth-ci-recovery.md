# Pinned AxKoth dependency transport recovery

## Spec and boundaries

REQ-142 remains authoritative: use only AxKothAPI 4, axapi 1.4.8 and the exact original POMs/JARs listed in ci/axkoth/artifacts.sha256. Verify restored caches and downloads before Gradle. Keep bounded artifact-only retries; never retry compilation/tests, alter compile-only/shading behavior or publish a replacement API. Initial failure evidence uses #216 a2bffa1. Delivery uses the sanitized #216 head 87f78d3 and canonical main e11fb16; no pre-cleanup history is merged back into the public branch.

## Prove

Hosted #216 run 37827256985, job 113483193997 failed at dependency preparation, before Gradle. All six HTTP/1.1 curl attempts ended with error 18 and incomplete bodies. The first transfer was missing 292001 bytes; subsequent attempts missed up to 2053281 bytes. Local direct downloads reproduce error 18 and the common 147114-byte prefix of the expected 2192203-byte AxKothAPI JAR. Range requests receive HTTP 200 and another incomplete prefix, so range resume is unsupported. Cache-busting queries, headers and protocol changes did not consistently recover the transfer. Occasional full transfers match the unchanged pinned SHA-256; a single successful request is not reliability proof.

## Engine, architecture and refine

The preparation helper will retry recoverable transfer failures for at most 32 attempts and 120 seconds per artifact, with each curl request capped at 15 seconds. Complete checksum mismatches and HTTP authorization/not-found errors fail immediately. Downloads remain temporary until SHA-256 verification succeeds, then replace the destination atomically. Valid cache hits require no network. Retry exhaustion must preserve an existing artifact and remove temporary bytes. The focused proof covers recovery beyond the previous six attempts, finite exhaustion, deadline enforcement, permanent errors, checksum rejection and verified cache reuse.

This is CI infrastructure work: plugin-runtime behavioral red/green does not apply. The six-attempt baseline fails the recovery-beyond-six regression; all eight final transport/cache regressions pass. A fresh upstream preparation probe still exhausted 32 attempts: the upstream transfer problem remains an external risk until hosted verification succeeds, and no reliable-network claim is made. Valid cache verification is independent of that network probe.

Canonical main's artwork cleanup was subsequently rewritten to `e11fb16`, with the pending stack rewritten to `87f78d3`. The repair is reapplied on that sanitized history and includes current main. No resource-pack files are tracked. The complete pre-reconciliation pack is preserved privately outside the repository. HolidayStylePackTest now validates that separately supplied pack through LUMAGUILDS_PRIVATE_ASSET_ROOT; an explicitly configured invalid pack fails all four checks, while the preserved valid private pack passes all four. An absent private profile is reported as an external integration skip. Gradle tracks the configured path and contents to avoid stale cached test results. No menu, texture or server rendering behavior is changed.

Final local Java 25 clean offline test/shadowJar passes 1714 cases, zero failures/errors, 19 skips (the previous 15 external skips plus four private-pack checks). The actual Market API contract executes. The separately supplied private pack passes all four checks; an invalid configured root fails all four. The tested runtime/build/test tree equals published repair 68506a4; delivery was moved onto sanitized history without runtime edits. Local review JAR 3.0.0-market-companion-review.2 SHA-256: e3c6100e0810235c5a52d08d1cd039e324a20755dc6bc424478d39978fdf1efc.

Reciprocal Market d2090ce clean test/shadowJar/JaCoCo against the updated Guilds runtime passes 915 cases, zero failures/errors, eight existing skips. Market review JAR 1.0.0-latest-guilds-companion-review.1 SHA-256: c9ddebec17caa7476e12fb43ef6b77e96c4e322527e016cddd689ad189deb727. Its public alliance-service contract also passes against the final Guilds review.2 JAR: one test, zero failures/errors/skips. Market runtime source is unchanged; final Guilds runtime source is unchanged between these reciprocal checks.

Hosted repair 68506a4 run 37842408052, job 113534801869 passes all eight transport regressions, then exhausts 32 incomplete AxKothAPI transfers. Gradle is correctly prevented from running. Thus transport safeguards and local companion compatibility are verified, but the hosted build remains blocked by the official upstream download. No weakened checksum, substituted dependency, public binary mirror or test-gate bypass was used.

EARS/state helpers and Codacy MCP CLI are unavailable; manual SPEAR evidence is maintained. Hosted analysis and maintainer approval remain separate. No GitHub merge, production operation or Minecraft client acceptance is authorized or claimed.
