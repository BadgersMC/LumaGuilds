# War duration and objective ending

## Requirements

- A declaring guild can select longer finite durations subject to an explicitly configured maximum. The receiver sees the actual duration before accepting.
- A declaration can explicitly request a war with no time limit. Both guilds see this before acceptance.
- No-time-limit wars must remain active across restarts until a supported victory condition, a mutually accepted peace, or an authorized cancellation resolves them.
- Existing serialized wars and declarations retain their timed expiration semantics.
- Kills are currently the only objective with a live, guild-attributed victory path. A no-time-limit option must clearly state this and must not imply that unsupported objective types can end a war.
- Existing wager funding, settlement, chapter cutover, and notifications remain consistent for both modes.
- Java and Bedrock duration and active-war displays must identify an unlimited duration without presenting a zero-day remaining time.

## Evidence

Production uses a 168-hour cap and a 25-kill target. Java offers 14 days but the service clamps it to 7 days. WarRecordCodec validates canonical JSON and requires compatibility work when record fields change. Non-kill addObjectiveProgress has no gameplay callers and does not select a winner.

## Verification required

Unit and serialization tests for legacy records, new timed and unlimited records, restart, pending declarations, acceptance, no expiry, supported kill victory and wager settlement. Validate Java/Bedrock rendering and declaration terms. Run repository checks and inspect exact PR head. Obtain live TEST verification separately.

## Current state

Implemented in the draft branch: explicitly persisted no-expiry mode, backward decoding for older records, longer timed choices, Java and Bedrock displays, and existing kill-target settlement. The complete local test suite passed after incorporating main at 175e9a2d. Scheduled expiration now excludes no-expiry wars; a regression verifies an aged persisted war survives cleanup and still settles on victory. Updated-head hosted CI, review, an existing TEST configuration cap adjustment, and live client verification remain outstanding. The interactive preview is a simulation; other objective types have no verified gameplay victory path.

## Per-war kill targets (REQ-230, REQ-231)

Feedback from TEST: 50 kills was listed but could not be picked, and every war ended at the global 25.

- `WarKillTargets.forWar(objectives, default)` gives a war's target: its kill objective, else `war_kill_win_target`.
- `WarKillTargets.options(default, max)` gives 5, 10, 25, 50 and the default, up to the new `war_kill_target_max` (default 50).
- Victory, the kill counter and every menu read the war's own target.
