# Remove cosmetic progression perks

User-requested scope: remove Custom Banner Colors and Animated Emojis from
progression before merging the fork PR. Remove enum entries, automatic grants,
bundled configuration, and Java/Bedrock labels. Ordinary banner functionality
remains independent. Existing stored/configured names must be safely ignored by
the existing tolerant perk parsers, without rewriting player data.

SPEAR: establish failing contracts for fresh guild grants and retired perk names,
then remove the entries and run regression, architecture, locale and JAR checks.

## Verification

- RED: both `RemovedCosmeticPerksTest` contracts failed before implementation.
- GREEN: JDK 21, offline Gradle `test shadowJar`, in-process Kotlin compiler:
  1,000 tests passed, zero failures/errors/skips, including layer and locale checks.
- No retired names remain in `src/main`; existing SQL/config parsers ignore
  unknown enum names, preserving other stored perks and progression values.
- JAR SHA-256: `b13eb1429615b99b35086d8385aac1abb628066abb786196e298cf7e662d1267`.
- No SQL schema or transaction changes. The earlier 36 MariaDB contracts were
  not rerun for this enum/configuration/UI removal.
- User explicitly requested merging fork PR #1 after this removal. Default
  Chapter 2 gate remains disabled; live-client and migration readiness remain open.
