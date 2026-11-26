#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

source scripts/bench/comparison-block.sh

state_hypotheses() {
    echo "Variant: both designs at W4 with TARANTINO_INDEX_BATCH=100 and no search phases, against the W4 trials of design-comparison, which have 250; with the assumption of design-comparison that ingestion outruns indexing"
    echo "H1: with 250, local indexes 10 to 20 % more books/s than with 100, as Stage 2 measured 17 % on one host: 31 % fewer postings documents (1,850,575 against 2,687,611 entries) in fewer bulkWrites, each acknowledged by a majority. Grid gains 0 to 10 %, because reading and tokenizing, which cost the same per book, set its pace (design-comparison Indexing H2)"
    echo "H2: the time from a book's claim to its batch's publication, the indexing column of coordination.csv, grows 2.1 to 2.3 times in local and 2.3 to 2.5 times in grid with 250: 2.5 times the books at the rate of H1. time_to_publication changes by 1.17 / (1 + the gain of H1), since 250 publishes in about 3 rounds of four batches instead of 7: within 10 % in local, no difference in pairs.csv, and 6 to 17 % longer in grid"
    echo "H3: process_memory_max of the workers during ingestion and indexing is 50 to 150 MiB higher with 250, about 0.7 MB per book in flight, and no worker runs out of memory"
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
