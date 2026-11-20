# Diplomacy history (REQ-203, REQ-204)

Status: approved mockup ("Parties and Diplomacy", 2026-11-10), building.

## Why

The Relations menu's History button only said "Diplomatic history coming soon!".
Relations are one row per pair of guilds that is overwritten or deleted when it
changes, so no history exists. From this update on, every change is logged.

## What is logged

One entry per guild for each change, worded from that guild's side.

| Change | Logged as |
| --- | --- |
| Alliance accepted | Allied with |
| Alliance broken, or ended by a war declaration | Alliance ended |
| Enemies declared | Declared enemies / Declared enemies by |
| Truce accepted | Truce for N days |
| Truce ran out | Truce ended |
| Peace (unenemy) accepted | Peace |
| War ended | War won / War lost / War drawn |

Each entry keeps the other guild, who did it (when a player did), the time and,
for truces, the length. A write that fails is logged and never blocks the
change itself.

Breaking an alliance from the Allies list used to delete the row directly, which
skipped the ally home whitelist cleanup and fired no event. It now goes through
`RelationService.breakAlliance`, which does both and logs the entry.

## The page

Relations → History opens a 6-row page: 45 entries, newest first, icon by kind,
with the date and who did it in the tooltip; arrows for more pages; Back.
Bedrock gets a form with the same entries, 20 per page.

## Not in scope

Changes before this update, which were never saved.
