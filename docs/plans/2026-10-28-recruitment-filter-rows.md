# Recruitment filter rows

When a player opens recruitment filters, the dialog shall identify itself as
Filters and display fee, guild level and sort choices in distinct rows. Each
choice shall continue to set its original filter and preserve the existing
results, reset and back actions.

TEST PR 13 showed the choices crowded together with a misleading guild mode
heading. Screenshot: Dawn client `2026-10-28_19.23.22.png`. Free fee results,
level and sort selection, and reset worked before this change. The candidate
groups existing clickable components without changing their callbacks.
The focused local Gradle run passed 11 board, compact-flow and surface tests,
with no failures or skips, using the same local companion API inputs as PR 13.
Hosted checks and client verification are pending.
