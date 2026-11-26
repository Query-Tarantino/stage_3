#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

readonly WRONG_USAGE=2
readonly BOOKS=30
readonly MAX_BOOKS_WITH_TERM=100
readonly POLL_MILLIS=10
readonly GIVE_UP_SECONDS=60

fail_usage() {
    echo "Usage: $0 <local|grid> --dry-run" >&2
    exit "$WRONG_USAGE"
}

state_hypotheses() {
    case $1 in
        local)
            echo "H1: index_freshness has a p50 of 100 to 400 ms and a p95 under 1 s: after BatchPublished every instance reads the batch's postings, about 6,000 one-posting documents for one book, and its metadata from MongoDB, which load_ms of its snapshot line shows, before its state changes. Stage 2 has no measure of this case, because its query never loaded postings"
            echo "H2: index_freshness_all has a p95 under 1.5 s, the slowest of the four instances to load"
            ;;
        grid)
            echo "H1: index_freshness has a p50 of 20 to 60 ms and a p95 under 150 ms: the entries are in the grid before the batch is published, so an instance only reads the batch document and the book's metadata entry, and load_ms is 0. Stage 2's query, which also read only batch ids on a refresh, showed a batch 37 ms after BatchPublished at p50 and 54 ms at p95, plus here the 10 ms of the polling"
            echo "H2: index_freshness_all has a p95 under 200 ms"
            ;;
    esac
    echo "H3: freshness_failures is 0; a lost BatchPublished would add at most TARANTINO_SNAPSHOT_SECONDS, 2 s, to that book"
}

print_plan() {
    local design=$1
    state_hypotheses "$design"
    echo "Deploy: design $design at W4 with TARANTINO_CANDIDATES=tranche1_ids.txt, and wait for tranche 1 to be published"
    echo "Books: the first $BOOKS of workload/tranche2_ids.txt whose term in workload/expected/book-terms.json is in at most $MAX_BOOKS_WITH_TERM books, one at a time"
    echo "Each book: the eligible backends from GET /ready/query; POST /ingest through the gateway of n1; GET /index/status/{book_id} every $POLL_MILLIS ms until PUBLISHED; GET /search?q=<term>&limit=100 at each eligible backend every $POLL_MILLIS ms until the book is in the answer, or not_visible after $GIVE_UP_SECONDS s"
}

main() {
    if (( $# != 2 )) || [[ $2 != --dry-run ]]; then
        fail_usage
    fi
    case $1 in
        local | grid) print_plan "$1" ;;
        *) fail_usage ;;
    esac
}

main "$@"
