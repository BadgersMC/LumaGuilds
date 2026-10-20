# Remaining guild menu return paths

## Scope and source

Roundtable GUILDS-02 continues the menu audit recommended in the recovered
October 17 Guilds handoff. Its starting source is PR #236 head
`b1339efb067b3ec312022e8e4d192edc25f4f262`, on the isolated branch
`roundtable/guilds-navigation-20261020`.

The earlier diplomacy fix introduced `MenuNavigator.returnTo`: a named Back
reuses the nearest existing parent and discards its descendants. A direct entry
replaces the current screen with its fallback. The remaining calls below still
used `openMenu` for returns, leaving the old child under a duplicate parent.

## Return-path changes

| Screen / action | Defect and resulting behavior | Regression coverage |
| --- | --- | --- |
| Party creation: Back and successful creation | Reuse party management, so its next Back reaches the dashboard without revisiting creation. | `GuildPartyReturnTest`: cancellation, repeated Back, successful creation and retained submitted values. |
| Party guild selector: Back | Reuse the party editor and transfer only selected guild IDs. Retain its name, role restrictions and other draft state. | `GuildPartyReturnTest`: guild selection returns without discarding name or roles. |
| Party moderation: Back and revoked permission | Reuse party management and discard moderation descendants, including a parent that can no longer render after permission revocation. | `GuildPartyReturnTest`: complete moderation return journey and real denial-open unwinding. |
| Player moderation: Back and denied entry | Transfer updated party data into the existing moderation screen on Back. A denied open returns directly to accessible party management, skipping denied moderation screens. | `GuildPartyReturnTest`: page/state retention, revoked-permission Back, and denied direct-entry fallback. |
| Home access: Back, revoked permission, removed home | Reuse the homes screen, or replace a direct entry with one fallback. Denied editors cannot reappear through Back. | `GuildHomeReturnTest`: both access editors, repeated Back, revoked permissions, removed home and direct entry. |
| Ally home access: Back and revoked permission | Use the same homes return policy. | `GuildHomeReturnTest`. |
| Rank permission category: Save, Cancel, Back and revoked permission | Reuse the existing rank editor and deliver the corresponding rank data before reopening it. Retain its other draft state, including the chosen icon. | `GuildPermissionReturnTest`: Save, Cancel, draft Back, failed Save and permission revocation. |
| Member contributions: Back to transaction history | Reuse history when present. Direct entry inserts one history fallback without retaining contributions underneath it. | `GuildContributionReturnTest`: history, bank and dashboard unwind. |
| Strike penalty confirmation: Cancel and failed confirmation | Reuse the existing penalty options screen; preserve the existing penalty service calls and outcomes. | `GuildPenaltyReturnTest`: cancellation performs no penalty call; failed confirmation returns without a history loop. |

`MenuNavigator.returnToWithData` shares the same stack restoration as `returnTo`
and delivers child data before opening the retained or fallback parent. It
invalidates pending navigation work in the same way as other navigator actions.
`MenuNavigatorTest` adds nearest-parent/data ordering, retained page state,
repeated returns and direct-entry fallback coverage. Existing `returnTo` callers
keep their prior behavior and do not receive an extra data callback.

Permission persistence is unchanged: category Save calls `RankService.updateRank`,
including its existing failure handling; Cancel returns the original category
rank; Back carries an unsaved draft to the parent editor. The English Back lore
now states that the parent editor's Save applies that draft. Authority checks and
the self-edit protections are unchanged.

## Paging found during the party audit

`GuildSelectionMenu` changed its page data without redrawing the inventory or
page indicator. Its Previous and Next callbacks now reopen the same screen
instance. When guilds disappear while the selector is open, its page is clamped
to the valid range, including an empty list. The fixture checks the displayed
banner count and page indicator after Next, then empties the source list and
checks the resulting empty first page.

## Reported presentation issues

| Report | State at the source pin | Change / evidence |
| --- | --- | --- |
| Untranslated `gui.close` | Partly fixed. `GuildMenuDialog.GUILD_DIALOG_CLOSE_LABEL` already supplied the readable `Close` fallback. `RecruitmentReaderDialog` and the input dialog in `RecruitmentPresentation` still constructed the untranslated component directly. | Both remaining shells use the shared fallback label. Their callback guards and native exit behavior are unchanged. `GuildDialogNativeWidthTest` already covers the fallback's readable value. |
| Raw purchase quote ID | Still displayed as a standalone transaction UUID in the native purchase review card. | The review shows the reward and quoted guild-gold price. The request, transaction ID, confirmation calls and retry identity are unchanged. An uncertain-result message still includes its support reference through `RewardPurchaseFeedback`. Existing `GuildRewardPurchaseMenuTest` covers retry identity and stale/cancel behavior. |
| Overwide recruitment filter button | Already fixed at the shared native button boundary in this pin. | `RecruitmentDialogMenu.action` produces the full filter label; `GuildMenuDialog.button` calls `GuildDialogText.buttonLabel` with `(width - 16) * 2` half-pixels, uses the declared native width, and retains the complete label/details in the tooltip. `GuildDialogTextTest` covers long-label fitting, and icon advances are recognized by the helper. No second layout change is introduced. |

## Other audited paths

- Diplomacy, bank settings/statistics/budget, rank creation/edit, mode, war and
  emoji named returns already use `returnTo` at this source pin.
- Claim-wide and per-player permission editors use ordinary `goBack` through
  their shared controls. Member-rank, invite and generic confirmation cancellation
  paths also unwind history rather than pushing replacement parents.
- The targeted Bedrock party, home access and rank editor forms already use the
  Bedrock navigator's `goBack` / `goBackWithData` paths. Their source is unchanged.
- Inline party pages that redraw their owning menu do not create navigator
  entries. Their existing business behavior is unchanged.

## Validation handoff

Twenty-one focused regression tests are added. The worker performed source review
and `git diff --check`; it did **not** run Gradle. The coordinator owns the
serialized Java 25 validation environment and should run this focused command
against the committed source snapshot:

```sh
./gradlew test --offline --no-daemon \
  --tests 'net.lumalyte.lg.interaction.menus.MenuNavigatorTest' \
  --tests 'net.lumalyte.lg.interaction.menus.guild.Guild*ReturnTest' \
  --tests 'net.lumalyte.lg.interaction.menus.guild.GuildRewardPurchaseMenuTest' \
  --tests 'net.lumalyte.lg.interaction.menus.guild.AllyHomeMenuAuthorizationTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildDialogNativeWidthTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildDialogTextTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildDialogCallbacksTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildDialogTransitionsTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.RecruitmentReader*Test' \
  --tests 'net.lumalyte.lg.interaction.menus.MenuLocalizationTest' \
  --tests 'net.lumalyte.lg.infrastructure.i18n.LocaleContractTest' \
  --tests 'net.lumalyte.lg.interaction.menus.bedrock.BedrockHomeMemberRankParityContractTest' \
  --tests 'net.lumalyte.lg.interaction.menus.bedrock.BedrockPartyLfgParityContractTest'
```

The final integrated branch still needs the project's normal
`./gradlew check shadowJar --offline --no-daemon` gate and independent review.
The tests above exercise source and MockBukkit/callback fixtures. They are not
live client acceptance. The seven theme backgrounds, private pack assets,
native callback guards, permissions, purchase rules, progression persistence
and Bedrock implementations are unchanged by this task.

No deployment, service restart, production action or shared TEST interaction
was performed by this worker. Real client journeys remain the coordinator's
separate acceptance task, including repeated Back, direct menu entry, paging,
cancellation and supported Java/Bedrock presentations.
