# Async piston and quest provenance fix

## Scope and invariants

Production's tick thread stalled while the quest piston listener borrowed a database connection. Quest listeners now snapshot Bukkit event state on the main thread and submit database work to a bounded shared FIFO. Quest and XP provenance operations must preserve placement, piston movement, break reads and cleanup ordering across coordinates.

- One worker drains up to 8192 pending operations; it does not borrow a connection per competing worker.
- Piston transactions snapshot source markers before deleting/reinserting them, preserving overlapping chains. SQLite reserves its writer before the first read; other dialects retain their transaction behavior.
- Reward item delivery/finalization and notification presentation marshal to the Bukkit main thread.
- Database errors, queue overflow and executor rejection latch tracking closed and log the first failure. Unknown provenance must not earn natural-block rewards.

## Recovery boundary

The queue is in memory, not a durable journal. Failed, overflowed or interrupted operations are not replayed. Investigate the first error and verify/repair affected placement provenance before restarting: restarting alone does not reconstruct missing block history. A closed queue also pauses queued quest progress. No automatic repair, schema migration or world deletion is included.

## Verification and remaining acceptance

Regression coverage includes shared FIFO ordering, failure/overflow/rejection, nonblocking piston event coordinate snapshots, overlapping marker chains, SQLite writer contention and 750 queued place/move/read/cleanup operations.

The deployed candidate is `3.0.13-piston-db-test.1`. The October 3 startup completed and an initial nine-player Spark sample reached 20 TPS with a 9.7 ms median and 19.6 ms 95th-percentile tick. This short observation is not sustained farm-load proof. Unrelated startup warnings were present, and no claim of complete server compatibility is made.

Before release, verify sustained piston-farm load and queue throughput; natural versus placed-block XP/quests; creative placement, retraction and explosions; online/offline item rewards and notifications; shutdown with pending work; and MariaDB integration. Confirm no SQLite busy, pool exhaustion, queue failure or watchdog recurrence under representative load.
