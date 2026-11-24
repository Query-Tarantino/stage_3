# Process-failure matrix

Every command runs from the root of the repository, on one machine, with the single-host stack.

Each row of the matrix (`SPEC.md` §8.2) stops a process at one transition and checks that every book still ends `PUBLISHED`, searchable exactly once and in a single published batch:

```bash
scripts/recovery/07-indexer-during-batch-write.sh
TARANTINO_INDEX_DESIGN=grid scripts/recovery/07-indexer-during-batch-write.sh
```

The scripts bring up `docker-compose.dev.yml` with three workers and two `control` instances and the short timing of `scripts/recovery/recovery.env`, and take the design from `TARANTINO_INDEX_DESIGN`, `local` by default. Rows 01 to 09 use a crash point, `TARANTINO_CRASH_AT=<role>:<point>`; row 10 pauses a worker for longer than its leases; row 11 kills the `control` leader; row 12 stops MongoDB; row 13 kills a worker while `bench` searches.

Rows 05 and 06 take their messages inside a transaction, the default; `TARANTINO_QUEUE_TAKE=plain` runs them with Stage 2's take. CI runs the thirteen rows in each design on every pull request to `main`, and rows 05 and 06 with both takes. These are test runs: they are kept as workflow artifacts and never committed. The failures of whole nodes, on the lab and under load, are in [scripts/chaos/README.md](../chaos/README.md).
