# Website guild profiles: local work in progress

## Spec

The website must display current authoritative Guilds data and must distinguish unavailable values from zero. Expose only approved public profile fields; never expose guild homes, vaults, permissions, private messages or credentials. A missing or failed prestige lookup must stay unavailable. Keep the current leaderboard response compatible.

## Evidence and implementation

On 2026-10-28 the website's production guild response was a limited leaderboard projection. Vibe's prestige, seasonal ELO, membership roster and banner were unavailable there; its banner was present in the separate public Market feed. Canonical LumaGuilds main was fetched at 283d75f1e1016d415aa5afdfac840252f0391846. This local branch adds guild mode, persisted prestige and recorded active-chapter ELO to the existing leaderboard response. Prestige and ELO are nullable if ownership or a rating is missing or a read fails, not invented zero or starting values. The existing website guild projection accepts all three fields. This does not provide a complete guild directory or player roster.

The local plugin also exposes a separate `/api/community/guild-directory` endpoint requiring a nonempty configured bearer token. Pages are ordered by guild UUID, limited to 50 records, and fail if a selected guild disappears. Each page uses a bounded immutable capture revision, which the consumer supplies on subsequent requests. The capture freezes the results of sequential source reads and is not a transactional database snapshot. Member UUIDs appear only on the protected directory, not the public leaderboard. The website resolves roster names only against a complete fresh public player snapshot; unavailable names remain unavailable.

## Remaining

- Complete and privacy-review the profile export and website integration, including confirming the real player snapshot is populated and banners for guilds without market stalls; reconcile multi-page changes safely.
- The current website fallback requests at most 50 leaderboard entries and marks the result limited. A stored guild snapshot takes precedence over that fallback without a freshness comparison; integration must avoid allowing an old snapshot to mask a newer live producer. Keep unavailable and stale states explicit.
- Confirm the semantics of recruiting before presenting open-to-join as recruitment.
- Focused leaderboard, directory access/pagination, nonempty roster privacy, oversized roster and SQLite recorded-ELO tests passed locally: eight tests, no failures or skips, using Java 25 and local companion API jars. Rosters above 200 are unavailable rather than truncated. The SQLite test covers missing rating, active chapter bounds, and frozen chapter. An earlier run failed because the test fixture used `rating` instead of the actual `elo` column; the corrected fixture passed. The first pagination run failed because its test guild name exceeded the domain limit; the corrected fixture passed. An intervening build failed before compilation from insufficient disk space; the later retry succeeded. More multi-page consistency, privacy, and website consumer tests remain.
- Review and deliver source through the required Git workflow. Separately verify production configuration, deployed plugin/source provenance and live readback before claiming activation.

The project has no identified local EARS/state helper; this is the manual SPEAR record. Current work has focused tests only and remains incomplete and unmerged; no production server or website changes were made.

## Current-main reconciliation, 2026-11-02

Spec: a consumer must receive all directory pages from one immutable bounded capture, never pages from independently changing guild sets. Require the capture revision after page one; reject missing, invalid, expired or evicted revisions. Bound each capture to 1,000 guilds, four concurrent captures and 30 seconds; continue to restrict rosters to the authenticated directory. The capture freezes the results of sequential provider reads and does not claim a database-wide transactional instant.

Engine: server captures sorted entries once; subsequent pages use the frozen capture. Website source base 461f933; Guilds base c0a144b. Names are published only from known public players, and incomplete rosters remain unknown. Existing 50-guild fallback stays explicitly limited. Local consumer tests and producer results must be recorded separately. Deployment and production verification have not occurred.

Current local verification: eight focused Guilds tests passed on Java 25 after the revision-bound capture and reuse changes. The build used locally available companion API jars; their exact runtime compatibility and canonical CI build remain unverified. No production operation was performed.
