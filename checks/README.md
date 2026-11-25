# Functional checks and query equivalence

Every command runs from the root of the repository, against whatever is deployed: the single-host stack by default, or the lab with the two URLs of a gateway.

Run the functional API checks and validate every response against `api/openapi.yaml`:

```bash
java -jar checks/target/checks-1.0-SNAPSHOT.jar
```

On the lab, from the operator machine, where the jar runs from the `tarantino-bench` image:

```bash
TARANTINO_PUBLIC_URL=http://<address of n1>:80 TARANTINO_OPERATIONAL_URL=http://<address of n1>:8081 cluster/checks.sh
```

They cover the public routes, `/documents/{words}` against `/search`, the filters alone and combined over the 20 sample books, the headers that name the worker, the node, the index version and the request, the operational routes, the roles API, and that nothing internal answers on the public port. Both designs pass the same checks against the same expected answers.

Check that every ready instance answers the query set exactly as expected, every one on the same index version, before any speed is compared (`SPEC.md` §9). It compares the `total` and the complete ordered list of books, page by page:

```bash
java -jar checks/target/checks-1.0-SNAPSHOT.jar equivalence workload/expected/sample-queries.json   # development stack and CI
cluster/checks.sh equivalence workload/expected/corpus-queries.json                                 # the lab, with the whole corpus
```

It asks each instance at its own address and then through the gateway, writes `equivalence.json` and exits non-zero on the first difference. The fault tests pass `workload/expected/tranche1-queries.json` for their baseline.

After a `course-client` run, check that what the course client got was the real search, complete, and not only a `200`:

```bash
cluster/checks.sh documents benchmarks/runs/<run_id>
```

It takes every distinct `/documents/` path from the run's gateway logs, asks it again through the gateway and as `/search?q=` at every ready instance's own address, writes `documents.json` and exits non-zero unless every answer is `200` and all of them are equal.

Regenerate `checks/expected.json`, the answers over the 20 sample books that the functional checks use, only when the corpus or the schema changes:

```bash
java -jar checks/target/checks-1.0-SNAPSHOT.jar --generate-expected
```

The files of `workload/expected/` are not generated from a running system: `bench oracle` computes them from the corpus cache ([scripts/bench/README.md](../scripts/bench/README.md)).
