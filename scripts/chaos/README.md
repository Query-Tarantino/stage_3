# Node-loss scenarios

Every command runs from the root of the repository, on the operator machine, against the lab cluster. These are the chaos tests of `SPEC.md` §8.3 to §8.5: real node failures while queries and ingestion are running, with availability and data integrity measured apart.

```bash
scripts/chaos/F1-one-node.sh local
scripts/chaos/F1-one-node.sh grid
```

| Scenario | Configuration | Stops, at the same instant | Within the declared tolerance |
| --- | --- | --- | --- |
| `F1-one-node.sh <design>` | `W4` | `n3`: a worker, a MongoDB secondary, a Consul server and a `control` follower | Yes, in both designs |
| `F1p-one-node-paused.sh <design>` | `W4` | `n3`, every process paused instead of killed | Yes, in both designs |
| `F2-two-nodes.sh <design>` | `W4` | `n3` and `n4` | No: a limit test in both designs |
| `F3-three-nodes.sh <design>` | `W6` | `n4`, `n5` and `n6`; only in a session with six nodes | No: a limit test in both designs |
| `L1-majority-loss.sh <design>` | `W4` | `n2` and `n3`: two of the three MongoDB members and Consul servers | No: a limit test in both designs |

The supported failure limit is one node, in both designs (`SPEC.md` §8.1). What each scenario is expected to do in each design is the table of `SPEC.md` §8.3, and each script prints its line of it before the fault.

Each script does the eight steps of `SPEC.md` §8.4 on a fresh deployment: a checked healthy baseline with tranche 1, 2,000 books, published; an open-loop load with exact probes; tranche 2, 800 more books, in flight; `held.json` with what the nodes about to stop hold; the fault; `TARANTINO_FAULT_SECONDS` with the nodes down together; the restore; the wait for the declared recovery condition; and the final integrity and equivalence checks.

A run leaves, besides the usual files:

| File | Content |
| --- | --- |
| `held.json` | The books and batches the stopped workers held at the fault, and the processes and roles of the stopped nodes |
| `timeline.csv` | One row per second: answers, failures, timeouts, retries, stale and incorrect answers, the instances the gateway routes to, the Consul state of each, and the events of that second |
| `summary.csv` | The metrics of `SPEC.md` §8.4, per period before, during and after the fault: `unavailable_seconds`, `detection_seconds`, `takeover_seconds`, `rejoin_search_seconds`, `recovery_seconds` and the rest |
| `integrity.json` | Documents, states, index and work: what is `ok`, `inaccessible`, `corrupt`, `lost`, `missing`, `duplicated` or `stale`, and how each held book and batch ended |

The table the report needs, one row per run with the losses tested, the interruption and the data loss:

```bash
java -jar bench/target/bench-1.0-SNAPSHOT.jar fault-summary benchmarks/runs/*-F*-local-* benchmarks/runs/*-L1-*-local-*
```

The commands the scenarios are built from, to stop and restore a node or one process by hand:

```bash
scripts/chaos/kill-node.sh n3                 # SIGKILL to every container of the node at once
scripts/chaos/restore-node.sh n3              # start them again from the node's ~/tarantino/node.env
scripts/chaos/pause-node.sh n3                # docker pause of every container of the node at once
scripts/chaos/resume-node.sh n3
scripts/chaos/kill-process.sh n3 worker       # one process: worker, worker2, control, mongo, consul or gateway
scripts/chaos/start-process.sh n3 worker
```

A killed process refuses connections at once, while a paused one keeps them open and never answers, so the two kinds of failure are detected at different speeds. Every run records which one it induced, and the report never mixes them in one figure. Killing the containers is the node failure the professors accept, and nothing here needs root.
