#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

source scripts/bench/comparison-block.sh

state_hypotheses() {
    echo "Variant: both designs at W4 with TARANTINO_INDEX_BATCH=100 and no search phases, against the W4 trials of design-comparison, which have 250"
    echo "H1: batches of 250 index at least 10 % more books/s than 100 in both designs, as Stage 2 measured 17 % on one host; grid gains more, 15 to 30 %, than local, 10 to 20 %, because 250 writes 31 % fewer entries (1,850,575 against 2,687,611) in fewer and larger putAlls, while local writes one postings document per entry either way"
    echo "H2: the time from a book's claim to its batch's publication, the indexing column of coordination.csv, grows about 2.5 times with 250, the size of the batch it waits for; time_to_publication, mostly the wait in books-to-index while ingestion outruns indexing, falls by about the gain of H1"
    echo "H3: the worker's heap peaks higher with 250, about 0.7 MB per book in flight, 175 MB against 70 MB, and no worker runs out of memory"
}

trial_names() {
    local repetition design
    for repetition in $(repetitions); do
        for design in "${KNOWN_DESIGNS[@]}"; do
            echo "batch-100-$design-rep-$repetition"
        done
    done
}

main() {
    read_block_flags "$@"
    print_plan
}

main "$@"
