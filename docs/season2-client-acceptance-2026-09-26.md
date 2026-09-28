# Season 2 client acceptance — 2026-09-26

Validated build: `040C47B3AAD1AC015928389536440CC355C286EC602802F369BB0B39056F5572`

Staging only:
- Java: `127.0.0.1:25570`
- Bedrock/Geyser: `127.0.0.1:19132`
- LumaGuilds 2.1.0 / Leaf 1.21.11-179
- Geyser 2.11.3-b1247 / Floodgate 2.2.5-b141 / ViaVersion 5.12.0
- Bedrock menus enabled; real-client sign-off is the remaining LG-1812 gate.
- Client prep verified at 18:37: Minecraft for Windows is installed and launched, Windows loopback exemption is present, and the existing Bedrock external-server entry `local` points to `localhost:19132`.
- Runtime readiness rechecked at 18:33-18:35: Java `127.0.0.1:25570` and Bedrock UDP `127.0.0.1:19132` are listening on the staging server process; `/bedrockcachestats` responds; Geyser reports 2.11.3-b1247 and Bedrock support 26.30-26.51.

## Bedrock walkthrough

Record PASS/FAIL plus any screenshot or exact visible text for each row.

| Area | Acceptance check | Result |
| --- | --- | --- |
| Dashboard | Opens native Bedrock control panel; all Season 2 sections are reachable; close/back does not jump into Java inventory UI. | PENDING |
| Weekly Quests | Six shared quests render with real progress/state; claim path works or returns the correct authoritative rejection; no unavailable placeholder. | PENDING |
| Progression | Current reward catalog renders; purchases use current quote/service semantics; unavailable rewards cannot be bought. | PENDING |
| Prestige | Disabled/max/not-ready states are truthful. When enabled and eligible, selection -> quote -> confirmation remains Bedrock and retry/rejection state is coherent. | PENDING |
| Settings | Identity/appearance/access controls show the correct persisted values; unauthorized controls are read-only/denied; theme/Lunar/open-state behavior is truthful. | PENDING |
| Bank | Canonical balance renders; deposit/withdraw path is authoritative; History/Statistics/Contributions reachable; Automation/Budget/Security respect MANAGE_BANK_SETTINGS. | PENDING |
| Homes | Teleport/remove/paid activation still work; per-home rank whitelist and inbound ally-home access are native Bedrock forms. | PENDING |
| Members | Member list -> selected member -> supported rank-change/kick actions remains Bedrock and respects permission boundaries. | PENDING |
| Ranks | Create/edit presents current permission model; ordinary edits preserve rank identity/priority. | PENDING |
| Party/LFG | Native LFG browser pages correctly; browser -> requirements -> join/result remains Bedrock; party requests/details/creation use persisted PartyService state. | PENDING |
| Diplomacy | Ally/truce/enemy requests and incoming/outgoing actions use current state and stay in Bedrock forms. | PENDING |
| Warfare | Declare/accept/reject/cancel/history/stats/peace work through persisted WarService state; KILLS/TIME_SURVIVAL and claims objective availability match config. | PENDING |
| Statistics | No fabricated XP/activity/territory values; unsupported data uses explicit unavailable/empty states. | PENDING |
| Navigation | Back/close paths remain platform-native throughout all tested flows. | PENDING |
| Timeout/reconnect | Close a form, wait/reopen as applicable, reconnect, and confirm no stale workflow state corrupts the next form. | PENDING |
| Description/Discord | Guild description is readable plain text and Discord invite URLs remain visibly intact. | PENDING |

## Java second walkthrough

Re-run the visual audit against the same build. Confirm the previously reported Season 2 UI/data fixes remain correct: Dashboard, Information, Members/Ranks, Weekly Quests, Bank, Settings, Progression/Prestige, Diplomacy/Warfare/Party, and Statistics.

Result: **PENDING**

## Automated/runtime evidence

- Full suite re-run at 18:29-18:31: **1,393 tests / 0 failures / 0 errors / 3 skipped**
- Focused Bedrock parity + architecture/localization/resource contracts: PASS
- `gradlew check`: PASS
- `git diff --check`: no errors; line-ending warnings only
- Semgrep `p/kotlin`: 9 rules / 795 tracked targets / 0 findings; one non-blocking PartialParsing warning in `BedrockGuildSelectionMenu.kt` around `open()` calls.
- Geyser/Floodgate/ViaVersion staging boot: PASS
- Bedrock cache command after boot/scheduler windows: PASS
- Bank scheduler regression: audit pruning moved off Bukkit main thread; first five-minute interest cycle and first async prune window produced no `SQLITE_BUSY`, database-lock, or watchdog event.
- Real Bedrock walkthrough: **PENDING**
- Independent Java sign-off: **PENDING**
- Independent Bedrock sign-off: **PENDING**
