# Experiments

Every command runs from the root of the clone, on the operator machine, in a session that `cluster/session.sh start` began at a clean commit of `dev`, which loaded the images and checked the mirror on every node ([docs/deployment.md](../../docs/deployment.md)). Each script deploys what it needs, measures and writes its own `benchmarks/runs/<run_id>/` (`SPEC.md` §9).

## Corpus and expected answers

`scripts/fill_cache.sh` copies the benchmark corpus, the first 2,800 downloadable books of `workload/book_ids.txt`, into `benchmarks/cache/` and leaves it in `benchmarks/mirror/`, as in Stage 2. CI packs that mirror into the image `tarantino-corpus`, and `cluster/sync-mirror.sh` fills every node's mirror from it in each session.

```bash
scripts/fill_cache.sh                              # with rsync
TARANTINO_CACHE_SOURCE=http scripts/fill_cache.sh  # one HTTP request per book, where port 873 is blocked
```

`workload/tranche1_ids.txt` is the first 2,000 ids of `workload/corpus.sha256`, in its order, and `workload/tranche2_ids.txt` the other 800. The expected answers come from the corpus, not from a running system. They are generated once, reviewed by hand and committed, and only regenerated when `workload/corpus.sha256` changes:

```bash
java -jar bench/target/bench-1.0-SNAPSHOT.jar corpus documents benchmarks/cache workload/documents.sha256
java -jar bench/target/bench-1.0-SNAPSHOT.jar oracle benchmarks/cache workload workload/expected
```

| File | Content |
| --- | --- |
| `workload/documents.sha256` | The digest every stored document must have |
| `workload/expected/corpus-queries.json` | The `total` and the complete ordered list of books for each query of `workload/queries.txt`, over the 2,800 books |
| `workload/expected/tranche1-queries.json` | The same over the 2,000 books of tranche 1, the baseline of the fault tests |
| `workload/expected/sample-queries.json` | The same over the 20 books of `workload/sample_ids.txt`, for CI |
| `workload/expected/probes.json` | The 20 terms found in 3 to 50 books of tranche 1 and in none of tranche 2 with the lowest SHA-256 of their text, each with its `total` and its first page of 20 books |
| `workload/expected/book-terms.json` | For each book of tranche 2, its term in the fewest books of the corpus, which the freshness probe searches, and in how many books it is |

## Experiments

| Experiment | Command | Official run |
| --- | --- | --- |
| Both designs on `W1` to `W4` and `W2P4`: ingestion, indexing, search, resources and the cost of the copies | `scripts/bench/design-comparison.sh` | |
| Design `grid` at `W4` without backups, against the `W4` grid trials of `design-comparison` | `scripts/bench/grid-backups.sh` | |
| Both designs at `W4` with batches of 100 books instead of 250, without search phases | `scripts/bench/index-batch.sh` | |
| Design `local` at `W4` with one copy of each document instead of two, without search phases | `scripts/bench/doc-replicas.sh` | |
| From an acknowledged update to correct answers on every eligible backend | `scripts/bench/index-freshness.sh <design>` | |
| An instance joining, leaving and recovering, a role moving and, in `grid`, each migration | `scripts/bench/routing-trace.sh <design>` | |
| The same, routing by liveness instead of readiness | `scripts/bench/routing-readiness.sh <design>` | |
| The same, without the gateway's retry | `scripts/bench/gateway-retry.sh <design>` | |
| The node-by-task matrix, in four rounds | `scripts/bench/role-rotation.sh <design>` | |
| The course load client for its whole ramp, then `checks documents` on its answers | `scripts/bench/course-client.sh <design> <path to load-test.jar>` | |
| The network between every pair of the session's computers | `cluster/session.sh start`, which runs `cluster/bench.sh network` | One per session |
| The campaign: everything above and the fault scenarios, in four blocks | `scripts/bench/all.sh --dry-run`, then `scripts/bench/all.sh --only comparison`, `behaviour`, `faults` or `six` | |

Each owner fills the last column with the `run_id` of the official run when it is committed. The node-loss scenarios are in [scripts/chaos/README.md](../chaos/README.md), and [benchmarks/README.md](../../benchmarks/README.md) reads the results.

`scripts/bench/queue-take.sh <take>` is the one experiment that runs on the development stack, on one computer and outside `all.sh`: rows 05 and 06 of the process-failure matrix with each take, as `plan-variants-stage-3.md` says. The variants and their reasons are in that file.

While a script measures, `bench record` streams `docker stats` and `/proc` from every node over SSH into `nodes/<node>/` of the run; nothing has to be collected from the nodes afterwards except the logs.

To run fewer trials while developing a script, never for an official run:

```bash
TARANTINO_DESIGNS=local TARANTINO_CONFIGURATIONS="W1 W2" TARANTINO_REPETITIONS=1 TARANTINO_CLIENTS_LIST=8 scripts/bench/design-comparison.sh
```

`bench comparison` reads the runs of block `comparison` and writes a run of its own:

```bash
java -jar bench/target/bench-1.0-SNAPSHOT.jar comparison benchmarks/runs/<run_id>…
```

To summarise an existing run again, delete its `summary.csv` and run its command on the run folder:

```bash
java -jar bench/target/bench-1.0-SNAPSHOT.jar freshness benchmarks/runs/<run_id>
java -jar bench/target/bench-1.0-SNAPSHOT.jar trace benchmarks/runs/<run_id>
java -jar bench/target/bench-1.0-SNAPSHOT.jar matrix benchmarks/runs/<run_id>
java -jar bench/target/bench-1.0-SNAPSHOT.jar charts benchmarks/runs/<run_id>
```

Before the report is built, check that every figure it cites has its run:

```bash
java -jar bench/target/bench-1.0-SNAPSHOT.jar figures benchmarks/figures.csv benchmarks/runs
```
