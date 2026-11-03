# Recruitment filter control

When a player browses guild recruitment, the filter button shall show a short
localized label and open the same filter screen as before. It shall not show
the redundant tooltip that becomes unreadable beside the right screen edge.
Other buttons retain their tooltips and existing callbacks.

TEST evidence: PR 12 board fits in the compact client, but the former label
`Guild Mode Filter (Any / Peaceful / Hostile)` was truncated and its tooltip
wrapped into a narrow column. The candidate changes only the board control's
label and tooltip; a focused flow test checks the original callback.
Focused local Gradle run passed eight tests with no failures or skips using the
same companion API JARs recorded for PR 12. Hosted checks and client
acceptance remain pending.
