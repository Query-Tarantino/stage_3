# Deploying on the lab

The lab keeps nothing between sessions, so every session starts from nothing and ends by pushing its runs (`SPEC.md` §2.4). Every command runs on the operator machine, a lab computer that is not a node, from the root of the clone `session.sh` makes.

## The computers

A session gives its computers, in the order `n1`, `n2`, …, to `session.sh start`, which writes `cluster/inventory.env` for that session only. Four nodes and the operator machine make a measured session; `F3-three-nodes` needs six nodes.

| Computer | Runs |
| --- | --- |
| `n1` | worker, `control` (the leader), `mongod` (the primary), Consul server, gateway |
| `n2` | worker, `control`, `mongod`, Consul server, gateway |
| `n3` | worker, `control`, `mongod`, Consul server |
| `n4` | worker, Consul client |
| `n5`, `n6` | worker and Consul client, only in `W6`, for `F3-three-nodes` |
| Operator machine | `cluster/` scripts, and `bench` and `checks` from `tarantino-bench`; never a node under test |

The lab's computer model, filled in F0, in session S1. `session.sh start` refuses a computer of any other model:

| CPU and cores | Architecture | RAM | Disk free for Docker | OS | Docker, and whether rootless | Compose | Public port |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |

The lab's firewall is disabled, so every port is open between its computers: the public port and 8081 on `n1` and `n2`; 7000 and 5701 on every node with a worker, and 7010 and 5702 in `W2P4`; 7004 and 27017 on `n1` to `n3`; 8300 to 8302 and 8500 between the nodes.

```bash
# cluster/resources.env, versioned, written once in F0
TARANTINO_WORKER_HEAP=<a quarter of the model's RAM, in whole GiB and at least 2, as 4g>
TARANTINO_PUBLIC_PORT=80   # 8080 if a container cannot publish port 80 on the lab computers
```

## Starting a session

On the operator machine, with a commit of `dev` whose images CI has pushed to Docker Hub:

```bash
curl -fsSLO https://raw.githubusercontent.com/Query-Tarantino/stage_3/<commit>/cluster/session.sh
bash session.sh start <commit> <host of n1> <host of n2> <host of n3> <host of n4>
cd ~/tarantino/stage_3
```

It clones the repository at that commit; installs an SSH key on each node where key access fails, which asks the member's password once per node; checks every computer; installs Compose in `~/.docker/cli-plugins/` where it is missing; writes `cluster/inventory.env`; pulls every image once and loads it on every node; fills and checks the corpus mirror of every node; and writes the session's first run, `lab-network`. The nodes never pull from Docker Hub. If Docker Hub refuses a pull because of its limit for the lab's address:

```bash
docker login -u querytarantino      # the team's read-only token, from the shared folder
bash session.sh start <commit> <host of n1> <host of n2> <host of n3> <host of n4>
```

## Deploying a design

```bash
cluster/teardown.sh                 # always first: a design never starts over another's data
cluster/deploy.sh local W4          # or grid; and W1, W2, W3, W4, W2P4 or W6
cluster/status.sh
```

`deploy.sh` takes the design from `cluster/designs/<design>.env`, the nodes that run a worker from `cluster/configurations/<configuration>.env`, the timing from `cluster/timing.env` and the heap and public port from `cluster/resources.env`. It writes each node's environment and profiles to `~/tarantino/node.env` on that node, then starts, waiting for each step: MongoDB on `n1` to `n3` and the replica set; Consul; `control` on `n1` until it is the leader, then on `n2` and `n3`; the workers of the configuration; the gateways. It returns when every worker of the configuration is alive in `GET /control/workers`.

`status.sh` prints, for every node, its containers, and then the Hazelcast members, the replica set, the Consul members and the worker registry with each worker's node and roles.

The leader seeds `workload/sample_ids.txt` unless `TARANTINO_CANDIDATES` says otherwise: `mirror` for the whole corpus, a file of `workload/`, or `none`.

```bash
TARANTINO_CANDIDATES=mirror cluster/deploy.sh grid W4
```

## Trying it

With the public port at 80; add `:8080` to the public addresses if `cluster/resources.env` moved it.

```bash
curl -i 'http://<address of n1>/documents/pride+prejudice'      # the public route, as the course load client calls it
curl -i 'http://<address of n1>/search?q=pride%20prejudice&language=en'
curl 'http://<address of n1>:8081/control/status'
curl 'http://<address of n1>:8081/control/workers'
curl 'http://<address of n3>:7000/ready/query'                   # a worker's own readiness, never through the gateway
curl 'http://<address of n3>:7000/internal/placement?term=love'  # in design grid, where a term lives
cluster/checks.sh                                                # the functional checks, from tarantino-bench
```

Every search answer names who served it, in `X-Tarantino-Worker`, `X-Tarantino-Node` and `X-Tarantino-Index-Version`, and every answer of the gateway carries its `X-Request-Id`.

Moving a role, without restarting anything:

```bash
curl -X PUT -H 'Content-Type: application/json' -d '{"roles": ["crawler"]}' 'http://<address of n1>:8081/control/workers/n2-w1/roles'
```

## Stopping and starting by hand

```bash
scripts/chaos/kill-node.sh n3                 # SIGKILL to every container of n3 at once
scripts/chaos/restore-node.sh n3              # start them again from n3's node.env
scripts/chaos/pause-node.sh n3                # docker pause of every container of n3 at once
scripts/chaos/resume-node.sh n3               # docker unpause of all of them
scripts/chaos/kill-process.sh n3 worker       # one process: worker, worker2, control, mongo, consul or gateway
scripts/chaos/start-process.sh n4 worker      # start one process, as a worker that joins later
```

None of them needs root. The scenarios that measure a failure are in [scripts/chaos/README.md](../scripts/chaos/README.md).

## Collecting, and ending the session

```bash
cluster/collect.sh benchmarks/runs/<run_id>     # logs of every process and gateway of every node, into a run
cluster/session.sh end                          # teardown, then commit and push the session's runs on runs/<session_id>
```

`session.sh end` asks for the operator's name, email and a GitHub token with write access to `stage_3` only, and nothing stores the token. Then open the pull request of `runs/<session_id>` to `dev`. A run that is not pushed before leaving is lost: the lab erases it. Keep the last 30 minutes of every session for this.

The resource samples are not collected afterwards: `bench record` streams them from every node while a run measures.

## When something does not come up

| Symptom | Look at |
| --- | --- |
| `session.sh start` asks for a password on every command | The key was not installed: run it again and type the password when `ssh-copy-id` asks |
| A pull fails with `toomanyrequests` | Docker Hub's limit for the lab's address: `docker login` with the team's read-only token, then start again |
| `session.sh start` refuses a computer | Its model, RAM or free disk differ from `n1`'s: use another free computer |
| A worker stays out of `GET /control/workers` | `docker compose logs worker` on its node: it waits for MongoDB or Hazelcast, or it exited because the deployment has the other design |
| The workers do not form one Hazelcast cluster | Port 5701 between the nodes, and `TARANTINO_ADVERTISE_HOST` and `TARANTINO_HAZELCAST` in the node's `~/tarantino/node.env` |
| The replica set has no primary | `mongosh --eval 'rs.status()'` on `n1`; port 27017 between `n1`, `n2` and `n3` |
| `deploy.sh` waits for the leader forever | `docker compose logs control` on `n1`: it waits for the replica set |
| The gateway does not start | Whether a container can publish the public port: `TARANTINO_PUBLIC_PORT=8080` in `cluster/resources.env`, in a PR |
| The gateway answers `503` to every search | `curl <address>:7000/ready/query` on each worker: `reasons` says why none is ready |
| A worker is alive and gets no searches | Its `tarantino-query` check in the Consul UI, `http://<address of n1>:8500` |
