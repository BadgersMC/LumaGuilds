# Unresolved guild gaps: implementation and investigation

## Source and scope

Started from fetched canonical main `a15b244e8a294bf18e6dedf722462edf9faa40ae` in an isolated worktree. Existing Discord cleanup work is preserved. Production access is read-only; this change does not merge, deploy, reload, delete roles or alter balances.

User-confirmed chat, hex rank, Vegas panel, SleepingOnCheese tag, relocation and login notification defects remain closed. More homes, free activation and alternative relocation cooldowns remain denied.

## SPEAR evidence

- Spec: REQ-122 / LG-2001 and REQ-123 / LG-2002.
- Prove: bank command regression initially failed compilation because `onBank` did not exist. After introducing the bank entry, eight focused tests ran: four cooldown regressions failed and all three bank tests passed. These are newly observed results, not historical red/green claims.
- Engine: direct `/guild bank` / `/g bank` opens the existing platform bank menu for a current guild member. Configured announcement and ping cooldowns replace hardcoded 5/1-minute limits. Nonpositive cooldowns disable only the time gate; existing hourly caps remain.
- Architecture: command retains `lumaguilds.guild.menu`; Java/Bedrock selection stays in MenuFactory. Bank operations retain their existing authorization and transaction services. No persistence schema, economy policy or management-menu access is changed. ChatService reads current configuration on each rate-limit check.
- Refine: all eight focused tests passed. Announcement help no longer advertises a fixed five-minute cooldown. Clean `test shadowJar` passed on Java 25 / pinned Paper 26.2: 1,546 tests, zero failures/errors, four skips. Existing rank priority (11), own/ally home access (16), leaderboard repository/projection (3), rank visibility (3), and vault gold audit-failure (1) tests passed. These are local automated checks, not live acceptance.
- Tooling: no project-local EARS validator or SPEAR state helper exists. This requirement/task/evidence record supplies traceability; no automated tooling pass is claimed.

## Investigations and remaining acceptance

| Item | Current evidence | Next required evidence or decision |
| --- | --- | --- |
| G03 bank entry | Management menu required unrelated moderator powers; direct bank command now covered by regressions. | Actual Java and Bedrock command registration/menu walkthrough, including denied operations. |
| G17 home click versus private message | Own-home command resolves destinations directly; current Java home UI uses inventory clicks and Bedrock uses forms. No corresponding chat selector was identified in GuildCommand. | Exact interface and reproducible click conflict with RoseChat. Do not call the historical report fixed or introduce a speculative chat override. |
| G22 chat specification | Existing `/gc`, `/gac`, `/gmc`, `/ga` present; `/gfa` and `/gh` aliases/fullscreen behavior not identified. | Establish precise alias/title/highlight behavior and permission/cooldown contract from the accepted specification before adding commands. Existing configured cooldown defect is fixed separately here. |
| G31 shop XP | Freshly fetched EnthusiaMarket main `14351db4dc416138341a11d0e4e27602f207221b` emits PostShopTransactionEvent after successful container trades. Event includes buyer, landlord, item, quantity, price, shop ID and direction. LumaGuilds has no corresponding listener or shop XP source. | Guild attribution, qualifying transaction directions, XP amount/caps, self-trade/refund/retry handling and stable transaction identity require a cross-plugin contract. The existing event alone does not establish durable exactly-once XP. No XP economy policy was invented. |
| Missing Nexo menu textures | Prior read-only production audit captured 17 missing textures. Repository contains newer Enthusia art; server-kit repoint manifest covers only 42 legacy IDs. | Reconcile exact live item IDs/models against available textures, stage only after approval, regenerate pack and obtain client acceptance. Build success alone cannot prove live assets. |
| Discord below-level cleanup | Existing upstream PR #207 owns this fix and remains open for review. | Exact-head review and approved later delivery; no duplicate implementation or live role deletion here. |
| Guild insert/alignment and strikes | Existing upstream #208 and #209 own these changes. | Review their exact heads/checks; keep separate from this patch. |
| Production provenance | Earlier panel filename 3.0.23 versus Bukkit metadata 3.0.0; download did not yield measured hash. | Obtain actual JAR bytes/hash and embedded metadata before any approved release. No binary equivalence claim. |

Historical merged code and fork work are source evidence, not production acceptance. Remaining permission, rollback, chapter rollover, bank/vault, leaderboard and home regression checks stay on the operations checklist; this bounded patch does not claim completion of all 48 items.

Exact-head hosted checks inspected on 7 October: #207 `0a0b0d99` remains open with build/Codacy success; #208 `eefe25ec` has successful build/wiki checks and Codacy in progress; #209 `62cca6da` has successful build but Codacy action_required (summary: 138 added issues; inspected annotations include missing documentation, formatting, method length and complexity). No merge or rerun was requested.
