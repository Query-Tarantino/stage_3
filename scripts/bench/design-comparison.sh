#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

source scripts/bench/comparison-block.sh

read -r -a DESIGNS <<< "${TARANTINO_DESIGNS:-local grid}"
read -r -a CONFIGURATIONS <<< "${TARANTINO_CONFIGURATIONS:-W1 W2 W3 W4 W2P4}"
readonly DESIGNS CONFIGURATIONS
readonly KNOWN_CONFIGURATIONS=(W1 W2 W3 W4 W2P4)

state_hypotheses() {
    echo "Corpus: 2,800 books with 17,024,046 postings over 755,875 terms; batches of 250 write 1,850,575 entries, one per term and batch"
    echo "Stage 2: one indexer published 28.5 books/s with batches of 100 and 33.3 with 250, on one host, and spent its time writing postings to MongoDB (2.81 s per batch of 100 in JMH)"
    echo "Indexing H1: W1 local indexes 20 to 30 books/s, 120,000 to 180,000 postings/s: Stage 2's 33.3, minus postings writes that now wait for a majority of three mongod over the lab network"
    echo "Indexing H2: W1 grid indexes at least twice as many books/s as W1 local, 50 to 80: its postings go to the maps of the worker's own member and MongoDB only gets the batch document, so reading and tokenizing set the pace"
    echo "Indexing H3: local scales from W1 by x1.6 to 1.8 at W2, x1.9 to 2.2 at W3 and x2.0 to 2.5 at W4, as Stage 2's x1.70 and x2.10 with 2 and 3 indexers: every indexer still writes its postings to the one primary on n1"
    echo "Indexing H4: grid scales from W1 by x1.7 to 1.9 at W2, x2.3 to 2.7 at W3 and x2.8 to 3.4 at W4: each putAll sends (N-1)/N of its entries to other nodes and waits for one synchronous backup, so it stays under linear, but above local in every configuration"
    echo "Indexing H5: W2P4 against W4, local within 10 % because the primary limits both, and grid 15 to 30 % lower because four indexing JVMs share the CPUs and the network of two nodes"
    echo "Index memory H6: index_memory plus index_backup_memory per worker is 250 to 450 MiB in local, the whole index in every search instance, flat from W1 to W4; in grid it is (1 + backups) x 350 to 600 MiB / N, 175 to 300 MiB at W4, the only configuration where grid holds less per worker than local"
}

trial_names() {
    local repetition configuration design
    for repetition in $(repetitions); do
        for configuration in "${CONFIGURATIONS[@]}"; do
            for design in "${DESIGNS[@]}"; do
                echo "design-$design-config-$configuration-rep-$repetition"
            done
        done
    done
}

main() {
    read_block_flags "$@"
    require_designs "${DESIGNS[@]}"
    require_one_of configuration "${KNOWN_CONFIGURATIONS[*]}" "${CONFIGURATIONS[@]}"
    print_plan
}

main "$@"
