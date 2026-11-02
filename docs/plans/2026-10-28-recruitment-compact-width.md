# Recruitment board compact-width investigation

## Requirement

When the recruitment board uses a two-column action grid, the grid must fit
within the themed dialog surface while search submission, navigation, focus,
and callbacks retain their existing behavior. The search input remains 280 px.

## Evidence and proposed change

On TEST, a compact FainNeito client showed the board shifted beyond the right
edge while the guild detail screen fit. The board used 280 px for each of two
action columns, wider than the 369 px surface; the proposed change uses 180 px
actions, already supported by the theme artwork, without changing callbacks.
Screenshot: `2026-10-28_17.08.34.png` in the Dawn profile screenshots directory.
This width diagnosis is a hypothesis until native client acceptance.

## Verification state

A focused width contract test was added. On 2026-10-28, the local Gradle run
passed 14 tests across the search, buttons, surface and compact board suites,
with no failures or skips. The first compile failed because the fresh checkout
lacked the companion API JARs; the successful retry used local test-checkout
copies (RoseChat SHA-256 `f050b732f061bbab284be6fabfae62d3b62897e56b11686e8dffc05ab4bc4ba9`,
CombatLogX `8e37f4a543b68f74541e5e567c95b901110526cec1bd6feee3726411ccf6d2f7`,
Staff API `4c834ececb860a88aab7f9a757b9ce86301c24a8452d4c3c5b1f1653a28e51ea`).
This is local evidence, not proof of companion runtime compatibility. No
corrected artifact has been deployed or tested in a client. Confirm compact
and normal windows, all themes, keyboard focus, search, pagination and card
navigation before treating the issue as fixed.
