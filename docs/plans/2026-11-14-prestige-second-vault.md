# Second guild vault at Prestige VI

Requirements: REQ-242 to REQ-248 in `../requirements.md`.

Decisions (Fain, 2026-11-14):

- The final prestige (VI) unlocks a **second physical vault chest** that the
  guild places somewhere else. It has its own location, hologram and
  protection, and holds its own items.
- The second vault is **storage only**. Guild gold lives in the main vault; the
  second vault has no gold balance button and nothing reads it for payments.
- If a guild later stops qualifying, the second vault **keeps its items and
  locks deposits**: members can still take items out but can't put new ones in.

## Why

Prestige VI is the last prestige and currently unlocks no perk of its own (only
the badge and history entry). A second vault is a practical reward for the
guilds that finish every climb.

## How a guild gets it

- `prestige.perks.second_vault_tier` (default 6) sets the tier. It is listed
  with the other perks: the prestige confirmation summary shows "New perk: your
  guild can place a second vault (/g getvault 2)".
- `/g getvault 2` gives a "Guild Vault II" chest item to a member with the
  existing `PLACE_VAULT` permission, when the guild has the tier and no second
  vault placed. `/g getvault` keeps giving the main vault, unchanged.
- Placing it works like the main vault: same placement rules (no adjacent
  chests, valid location), same hologram and beam style labelled "Guild Vault
  II". It can't be placed next to the main vault either.
- `/g vault 2` opens it remotely with the same rules as `/g vault`. Clicking the
  chest opens it as usual.

## Storage

- The second vault is stored as its own vault under a fixed ID derived from the
  guild (`nameUUIDFromBytes("lumaguilds:second-vault:<guild>")`), so the
  existing slot storage, write buffer, viewers, backups and transaction log are
  reused without a schema change to `vault_slots`.
- Its location and status live in a new table, `guild_second_vaults`
  (guild ID, world, x, y, z, status, placed at), on SQLite and MariaDB.
- Capacity grows with guild level exactly like the main vault: 1 row at level 1,
  2 rows from level 2, 3 from 6, 4 from 11, 5 from 16 and the full 6 rows from
  level 21. The guild is back at level 1 after Prestige VI, so the second vault
  starts at 1 row and upgrades as the guild climbs (Fain, 2026-11-14). Both
  vaults read the same capacity rule, so a change to it changes both.
- No gold balance button: slot 0 is an ordinary slot. Gold items put in it are
  just items.

## Losing the tier

A guild only loses the tier through staff action or a disband and re-form. In
that case the second vault stays where it is, keeps its items, and refuses
deposits: shift-clicks, drags and placing items into it are cancelled, taking
items out still works. Its hologram reads "Guild Vault II · withdraw only".

## Breaking and disbanding

- Breaking the second vault follows the same confirmation and
  `drop_items_on_break` rules as the main vault.
- Disbanding drops or keeps its items under the same rules as the main vault.
- Restoring vaults on startup restores both chests.

## Menus to mock up before building

1. Prestige confirmation summary at Prestige VI with the new perk line.
2. The "Guild Vault II" chest inventory (no gold button) and the withdraw-only
   state.
3. The vault chest item and hologram.

## Out of scope

- A second gold balance or splitting gold between vaults.
- More than two vaults.
- Changes to how the main vault works.
