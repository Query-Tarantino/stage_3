#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

source scripts/bench/comparison-block.sh

state_hypotheses() {
    echo "Variant: design grid at W4 with TARANTINO_GRID_BACKUPS=0, against the W4 grid trials of design-comparison, which keep 1"
    echo "H1: index_throughput is 10 to 25 % higher without the backup, because each putAll no longer waits for a synchronous copy on another node"
    echo "H2: index_backup_memory falls to 0, and index_memory plus index_backup_memory per worker halves, from 175 to 300 MiB to 90 to 150 MiB (design-comparison H6)"
    echo "H3: node_network_in_mib plus node_network_out_mib during ingestion and indexing fall 10 to 20 %, the share of the backups next to the document copies and the remote reads of books; node_network_in and node_network_out, in MiB/s, stay within 10 %, because the phase also shortens"
    echo "H4: search_latency_p95 and search_throughput stay within their 95 % intervals, because a search reads only the owners of its terms"
}

trial_names() {
    local repetition
    for repetition in $(repetitions); do
        echo "backups-0-rep-$repetition"
    done
}

main() {
    read_block_flags "$@"
    print_plan
}

main "$@"
