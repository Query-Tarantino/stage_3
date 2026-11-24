# Benchmark results

What the official runs of `SPEC.md` §9 show. This file is written in F5 and F6, section by section, as each official run is committed; until then each section says what it must contain. The Stage 2 results stay in `stage_2`.

Rules for every section, as in Stage 2:

- Every figure comes from the `summary.csv` of the run named next to it, unless the text names another file of that run: the mean ± the half-width of its 95 % CI over the repetitions of each configuration, and p50 or p95 where the tail matters.
- Each section gives the command that made the run and where its raw data is, and checks, one by one, the hypotheses the script printed before measuring. A hypothesis that failed stays, with what was measured instead.
- Both designs are always side by side, with the same units. A difference smaller than its CI is called inconclusive, with the experiment that would settle it.
- `W1` is the baseline and is never presented as a deployed design. `W2P4` is process scaling and is labelled so in every table and chart.

## The cluster

To write from the `manifest.json` of the official `W4` deployment of each design: the nodes with their hardware, OS and Docker version; what each node runs; each worker with its node, its roles and its thread counts; the network between the nodes; the clock offsets; the data placement and backups. Physical computers, worker processes and threads are three separate columns.

## The lab network

To write from every session's `lab-network` run: the round trip and throughput between every pair of computers, how much they changed between sessions, and, next to every result that moved data between nodes, how close its network use came to them. The professors warned that the lab network is slow: this section says where that decided a result.

## The campaign

To write from the campaign's `runs.txt`: the commits, the four blocks and the sessions and clusters each ran in, how long each took, every step with a non-zero exit code and what was run again, and every cut that was applied from the list of `SPEC.md` §9.

## Query equivalence

To write from the `equivalence.json` of every `design-comparison` trial: that both designs returned the expected books, in the expected order, for every query, on every instance, before any search was timed.

## Ingestion and indexing

From `comparison.csv`, `pairs.csv` and `scaling.csv` of `design-comparison`: `ingest_throughput`, `index_throughput` and `index_postings_throughput` for each design and configuration, the speedup over `W1`, and where the time goes in each design.

## Search

From the same run: `search_throughput`, `search_latency_mean`, `search_latency_p95`, `search_latency_max`, `search_errors` and `search_timeouts` per clients value, for each design and configuration; what the network round trip of design `grid` costs; and the course load client's own lines from the `course-client` runs, with what `checks documents` found, reported apart.

## Replication in design `grid`

From `grid-backups` against the `W4` grid trials of `design-comparison`: what one synchronous backup costs indexing, memory and network, and what it changes in search; the PDF's effect of replication.

## Variants

To write from `plan-variants-stage-3.md` and the runs of each variant: `index-batch` and `doc-replicas` from their `pairs.csv` against the `W4` trials of `design-comparison`; `routing-readiness` and `gateway-retry` next to the default `routing-trace`, case by case; and `recovery-delay.csv` of `queue-take` next to Stage 2's `activemq-persistent` and `hazelcast` runs. Each variant answers one question: say which way it went, and whether the difference was inconclusive.

## Index freshness

From `freshness.csv` of `index-freshness`: `index_freshness` and `index_freshness_all` for each design, with the measurement's resolution, and the `load_ms` of design `local`.

## Resources and the cost of the copies

From `nodes/<node>/` and `placement/`: CPU, memory and network per node and per phase; the supporting processes next to the workers; `index_memory`, `index_backup_memory`, `index_store` and `document_bytes`. How many copies of the index and of the documents each design keeps, and what each copy costs.

## Discovery and rerouting

From `trace.csv`, `health.csv` and `routing.csv` of `routing-trace`: how long a new instance takes to get its first search, how long a killed one stays in the pool and how many requests fail meanwhile, and a role moved without a restart. In design `grid`, how long each migration took and what it did to the search p95: the effect of rebalancing.

## The node-by-task matrix

`matrix.csv` of `role-rotation`, as a table, for each design, with the supporting infrastructure listed apart.

## Node loss

From the fault runs and `fault-summary.csv`, for each design and scenario: what was stopped and what it hosted; whether it was within the declared tolerance; `unavailable_seconds`, `detection_seconds`, `takeover_seconds`, `rejoin_seconds` and `recovery_seconds`; who took over which task; and what `integrity.json` found. Stated apart for each design: how many concurrent node losses were tested, which runs had no observed interruption and which had no observed data loss. Limit tests are labelled, and scenarios that were not run have their reason.

## The choice

The decision rule of `SPEC.md` §9, step by step, with the run behind each step; the design chosen for this workload; its limits; and what would change the decision.

## Figures of the report

`benchmarks/figures.csv` has one row per table and figure of the report: `item`, `caption`, `run_id`, `file` and `command`. `bench figures` fails if a run or a file it names is missing.
