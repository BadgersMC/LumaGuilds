# Member rank tooltip colors

Requirement: REQ-063. WHEN the Java member list displays a stored guild rank,
THE SYSTEM SHALL render supported legacy and hex color codes as color rather
than literal characters. Missing ranks retain the localized default. Rank IDs,
assignments, permissions, storage and menu navigation remain unchanged.

Spec/prove: current main bfa6931e passes raw rank.name to the member-list lore
placeholder. The screenshot reports literal `&#f99801Founder`. Other rank menus
already use RankNameContent. Add a real inventory regression using the production
locale/GUI renderer; check visible text and the actual Adventure text color.

Engine/arch: reuse the existing RankNameContent conversion at this presentation
boundary. No persistence or external API changes. No asset bytes are published.

Refine: focused/full local results and exact-head hosted checks will be recorded
below. Project-local EARS/state helpers and docs/verification.md are absent;
this manual SPEAR record applies. Preview is a private schematic, not in-game
acceptance. Production remains untouched; keep the PR open for review.

Local evidence: the three real-inventory hex cases fail on the unmodified
member-list implementation, reporting literal hex in item lore. After conversion,
all three pass and assert actual Adventure color `#f99801`; the stored name is
unchanged. Focused rank converter plus inventory tests: six cases, no skips or
failures. Full Java 25 / Paper 26.2 `test shadowJar`: 1,645 discovered cases,
zero failures/errors, 14 existing optional/environment skips. Initial compilation
needed the existing ignored EnthusiaStaff moderation API JAR restored locally;
that dependency issue was separate from the reproduced rank failure.

Private clickable schematic: https://chatgpt.com/space/page_7ceb7c8b79dc8191a505b86856879494
Page and embedded-file access are verified through the connector; the local
interactive comparison works at 320px with no horizontal overflow and orange
RGB (249,152,1). Browser access to ChatGPT reached a verification challenge;
actual phone access and in-game acceptance are not claimed. Custom artwork is
not embedded or published in this PR. Exact-head hosted checks remain separate.

Codacy refinement: normalize multiline calls, use an internal test fixture with
documented temporary storage, and avoid a numeric color literal. The six focused
cases pass after these style changes; the rank conversion is unchanged.

Follow-up spec: audit all Java/Bedrock menu rank presentation and menu feedback.
Convert raw stored names at display sinks, including info, promotion, assignment,
confirmation, home access, rank priority neighbors, member pickers and rank management.
Bedrock form labels retain the existing plain-text contract; editable input defaults
retain raw color syntax. IDs, dropdown order, permissions and persistence do not change.
Regression proof covers real Java inventory output and native Cumulus forms.

Follow-up evidence: six additional parameterized cases failed before conversion
(three Java inventory groups, three Bedrock form cases). The Java red groups
reproduced literal hex in info, promotion and confirmation output; initial
fixture setup errors were corrected separately. Final focused suite: 18 cases,
zero failures/errors/skips, including five additional Java inventories, rank-editor
priority neighbors and five native Bedrock form families. Tests verify actual
Adventure color and visible text, plain dropdown options, unchanged default
selection and raw editable input. Source audit also covers related feedback and
member selection callbacks. Full suite and final-head CI results are recorded
in the delivery evidence. No production or artwork changes.
