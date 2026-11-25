# Running on one machine

The single-host stack is for development, CI and the process-failure matrix. It never produces a result: several containers on one computer do not demonstrate deployment across machines. The lab deployment is in [deployment.md](deployment.md). Every command runs from the root of the repository.

```bash
mvn -DskipTests package
docker compose -f docker-compose.dev.yml up -d --build --scale worker=3     # design local, books from benchmarks/mirror
curl -i 'http://localhost:8080/documents/pride+prejudice'
curl 'http://localhost:8080/search?q=pride%20prejudice'
curl 'http://localhost:8080/books/1342'
curl 'http://localhost:8080/search/filters'
curl 'http://localhost:8081/control/workers'                                 # the three workers and their roles
curl 'http://localhost:8081/index/status'                                    # design, generation and index version
java -jar checks/target/checks-1.0-SNAPSHOT.jar                              # the functional checks
java -jar checks/target/checks-1.0-SNAPSHOT.jar equivalence workload/expected/sample-queries.json
docker compose -f docker-compose.dev.yml down -v                             # stop and delete the data
```

The other design, always after a `down -v`:

```bash
TARANTINO_INDEX_DESIGN=grid docker compose -f docker-compose.dev.yml up -d --build --scale worker=3
```

`control` seeds `workload/sample_ids.txt`, and a book shows up in the searches once its batch is published: at most `TARANTINO_BATCH_TIMEOUT_SECONDS` after the first download of the batch.

| Setting | How |
| --- | --- |
| Entry ports | The gateway publishes 8080, the public search API, and 8081, operations, on `127.0.0.1`; on the lab they are 80 and 8081 |
| Worker and `control` ports | 7000 and 7004 inside the Compose network, with `/health`, `/ready/query` and `/internal/`: `docker compose -f docker-compose.dev.yml exec worker curl -s localhost:7000/ready/query` |
| Workers | `--scale worker=3`; each gets its own datalake volume and uses its hostname as its node |
| Roles | `TARANTINO_ROLES` for the first assignment; afterwards `PUT /control/workers/{worker_id}/roles` |
| Index design | `TARANTINO_INDEX_DESIGN`, `local` by default or `grid`; one per deployment |
| Timing | The `TARANTINO_*_SECONDS` variables of `SPEC.md` §4.7, read from the shell and the same for every process |
| JVM | `JAVA_TOOL_OPTIONS`: `-Xmx2g -XX:+ExitOnOutOfMemoryError` for a worker, the development value of `TARANTINO_WORKER_HEAP` |
| Crash points | `TARANTINO_CRASH_AT=<role>:<point>` on the `worker` service, for the process-failure tests (`SPEC.md` §8.2) |
| Consul | One server; every worker and `control` register with it at `TARANTINO_CONSUL=http://consul:8500`, with their container address. Its UI at `http://localhost:8500` shows the three services and the state of each check |
| Tools | `docker compose -f docker-compose.dev.yml run --rm bench <command>`; under the `tools` profile, so `up` never starts it |

Colima's default VM has 2 GiB, which does not fit three workers; give it more first, for example `colima start --memory 8`.
