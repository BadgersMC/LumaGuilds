# War and diplomacy moments

Goal: build the approved war notification mockup (enthusia-message-theme, war-redesign) in game (REQ-233 to REQ-235).

## Moments

| Moment | Title | Subtitle | Chat line | Sound |
|---|---|---|---|---|
| Declared | WAR DECLARED | A has declared war on B | ⚔ WAR DECLARED » A has declared war on B! | two bells, low impact |
| Begins | WAR BEGINS | A and B | ⚔ WAR BEGINS » War between A and B has begun. | war drum |
| Victory | VICTORY | Your guild defeated B | ⚔ VICTORY » Your guild defeated B. | challenge fanfare |
| Defeat | DEFEAT | B defeated your guild | ⚔ DEFEAT » B defeated your guild. | beacon power-down |
| Peace | PEACE AGREED | Peace between A and B | ☮ PEACE AGREED » Peace agreement accepted. | amethyst chimes |
| Alliance | ALLIANCE FORMED | A and B are now allies | ❖ ALLIANCE FORMED » A and B are now allies. | bells |
| War kill | (none) | (none) | existing "⚔ WAR KILL! You killed X!" | slowed crit, quick level-up |

## Approach

- `GuildMoment` (domain) lists each moment's sound cues; `GuildMomentPresenter` shows the title, chat line and cues.
- War declared, begins, victory and defeat replace the toast in `WarNotificationServiceBukkit.present`, so offline members still get them on join; the detailed chat block stays.
- Peace is shown from `warEnded` when a war ends with peace terms and no winner.
- Alliance listens for `GuildRelationChangeEvent` with `ALLY`. Alliance was not in the mockup; it follows the same style.
- The existing server-wide "WAR ENDED" broadcast stays; no new server-wide broadcasts, to keep chat quiet.
