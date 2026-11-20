# Bank statistics system actor

Requirement: When the Java Bank Statistics Recent Activity item displays a transaction recorded by the reserved system actor, it shall show the localized System label without looking up a player. Known and unknown player actors shall keep their existing labels and the item shall still open transaction history.

Evidence: `GuildBankStatisticsMenu.getRecentActivity` previously looked up every actor as an offline player. `BankServiceBukkit` records system transactions with the zero UUID. The existing `BankHistoryPresentation.actorName` handles this actor and has focused system, known-player and unknown-player cases in `BankHistoryPresentationTest`. Bedrock statistics shows aggregate totals and has no recent-activity actor list.

The menu now reuses that shared presentation. The existing item and its click handler are unchanged. `GuildBankStatisticsRecentActivityTest` prepares a MockBukkit inventory-level check for all three actor cases; it has not been run or compiled. The interactive HTML is a simulation of the relevant item, labels and navigation, not an in-game capture. Tests, hosted checks, mobile-accessible publication and native client acceptance remain pending.
