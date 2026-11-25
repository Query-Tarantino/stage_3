#!/usr/bin/env bash

readonly WRONG_USAGE=2
readonly REPETITIONS="${TARANTINO_REPETITIONS:-3}"
readonly KNOWN_DESIGNS=(local grid)

dry_run=false
only_repetition=""

fail_usage() {
    echo "Usage: $0 --dry-run [--repetition <1 to $REPETITIONS>]" >&2
    exit "$WRONG_USAGE"
}

read_block_flags() {
    while (( $# > 0 )); do
        case $1 in
            --dry-run) dry_run=true ;;
            --repetition)
                if (( $# < 2 )) || ! is_repetition "$2"; then
                    fail_usage
                fi
                only_repetition=$2
                shift
                ;;
            *) fail_usage ;;
        esac
        shift
    done
    $dry_run || fail_usage
}

is_repetition() {
    [[ $1 =~ ^[1-9][0-9]*$ ]] && (( $1 <= REPETITIONS ))
}

repetitions() {
    if [[ -n $only_repetition ]]; then
        echo "$only_repetition"
    else
        seq 1 "$REPETITIONS"
    fi
}

require_one_of() {
    local label=$1 known=$2 value
    shift 2
    for value in "$@"; do
        [[ " $known " == *" $value "* ]] || { echo "Unknown $label $value, expected one of $known" >&2; exit "$WRONG_USAGE"; }
    done
}

require_designs() {
    require_one_of design "${KNOWN_DESIGNS[*]}" "$@"
}

print_plan() {
    local trial number=0
    state_hypotheses
    while read -r trial; do
        number=$((number + 1))
        echo "Trial $number: $trial"
    done < <(trial_names)
}
