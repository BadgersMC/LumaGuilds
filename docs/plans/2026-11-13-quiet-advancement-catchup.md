# Quiet advancement catch-up

Goal: stop chat spam when members log in and complete advancements their guild already earned (REQ-229).

## Approach

- `ProgressionService.awardSystemExperienceOnce` reports whether a payout was paid now, already paid, or failed.
- The runtime announces publicly (via `celebrate`, which shows the toast, chat line and Discord post) only when the guild XP payout was paid now.
- Already-paid completions, and reward-free completions during `initialize`, get a private chat line instead.
- A failed payout outside join is still announced, so a database hiccup never hides a genuine first.
