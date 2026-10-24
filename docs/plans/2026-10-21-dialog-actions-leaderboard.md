# Direct leaderboard access and themed native controls

## Spec

- DN-001: WHEN a current guild member with leaderboard access uses either
  `/g leaderboard` or `/guild leaderboard`, the system SHALL open the existing
  guild rankings directly without management-rank checks. Nonmembers receive
  existing localized feedback. Java and Bedrock use their native presentations.
  Direct-entry Back closes safely; navigation from Progression returns normally.
- DN-002: WHEN the matching optional button-art contract is available, Java
  dialog actions SHALL use square theme-colored artwork inside native buttons.
  The native focus rim and existing action, audience, replay, membership,
  search-response and dismissal guards SHALL remain intact. Single-column
  actions align with the 360px panel; unsupported labels, missing assets and
  Vanilla retain standard controls. Client-translated Close remains native when
  its rendered text width cannot be established safely.
- DN-003: Seven themes SHALL reuse the pack palette with restrained edge shading,
  no random texture, and bitmap cells no larger than256px. Art and text advances
  SHALL fit the native control. Pack and plugin are paired optional changes;
  neither file presence nor browser simulation proves native rendering.

## Prove and engine

Baseline inspection: GuildCommand has no leaderboard subcommand. Existing Java
rankings are reachable only from Progression; native action labels only receive
an accent color, leaving gray button textures and half-width single-column rows.
The mockup is a proposed native-button label overlay, not a client screenshot.
Add real ACF routing/access regressions, shared ranking/privacy/pagination checks,
bitmap geometry/style/advance tests, and all-theme generated asset checks.
Do not claim historical red/green evidence for new tests.

## Architecture and refinement

Continue owned PR241 from clean b98bfded, fetched canonical main283d75f1.
Keep the earlier tint correction. MenuFactory owns platform selection; rankings
share their read projection and do not award XP or alter guild state. Decoration
only returns a label and width, leaving native ActionButton callback construction
unchanged. The pack owns procedural artwork and the optional versioned marker.
Missing project EARS/state helpers require this manual requirement/task/evidence
record. Hosted build, independent review and native acceptance are separate.

No TEST/client window, deployment, reload, restart, merge or production action is
authorized by this source/design unit. The previous window was released832.
Interactive proposal: https://chatgpt.com/space/page_15340b17f1148191aa947127d230a501.

## Verification evidence

Pack d08df39 passes hosted lint/build39640415310 and the local palette, icon,
control-clearance, action geometry and full pack checks. New artwork has no
random grain. Browser checks cover both command entries, tabs, Back, keyboard
focus and all eight theme/fallback selections at320px;390px and768px fit without
horizontal overflow. Browser sample data is not native rendering proof.

Guilds f55e581c hosted39640401398 compiled and executed2,247 cases: one existing
locale-contract check failed because two new Bedrock references were dynamic.
All nine new regression cases passed. Correction2d920143 makes the three keys
explicit rather than relaxing that baseline. Recruitment input Submit/Cancel
also use the shared artwork helper, retaining each unsupported-label width.
The local build could not compile unrelated companion adapters because RoseChat,
CombatLogX and EnthusiaStaff API artifacts are missing; canonical hosted CI
supplies the pinned APIs. Final corrected-head hosted results are recorded in
the delivery evidence and PR. Native button alignment, hover, focus, click and
pack delivery acceptance remain pending a new authorized coordination window.
