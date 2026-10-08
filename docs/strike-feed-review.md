# Staff strike feed review

Canonical main a15b244 is included in PR #209 head 62cca6d. This isolated review work uses the exact EnthusiaStaff API source pinned by the PR's CI: 8539bb8c77d7ecaf083546dda7ca8ccbfa8064e6, built clean as moderation-platform-api. No production changes.

SPEAR spec: REQ-130 preserves attribution and page retry when membership storage is unavailable. Proof: failedHistoryDoesNotFallback failed against the PR head because the adapter converted a failed historical read into empty history, permitting current-guild fallback. Engine: propagate that read failure to the existing page failure handler, which leaves the cursor unadvanced for retry. Architecture: the platform adapter retains lifecycle identities, legacy backfill settings and application service calls. Refine: the complete EnthusiaStaffStrikeFeedTest class passes, including the new regression; baseline focused Staff/strike tests passed before the new test. This does not claim a live sanction test. Project EARS/state helpers are absent; this record is manual evidence.

Existing hosted Codacy findings require further review refinement; a successful local test is not a green hosted quality check. No PR merge or deployment authorized.

The provider projector is now separate from scheduler/cursor orchestration, and the new external-strike application method accepts the existing GuildStrike value rather than eleven positional arguments. Lifecycle identity, legacy backfill switches, expiration and retry behavior are preserved. Clean Java 25/Paper 26.2 test + shadowJar passed; the exact test totals are recorded in the PR validation. Hosted code-quality results remain a distinct pending gate.
