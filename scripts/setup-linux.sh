#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    printf 'Usage: scripts/setup-linux.sh [--install-only]\n'
    printf 'Checks prerequisites, prepares the optional model helper, and starts the game.\n'
    exit 0
fi
[[ "$(uname -s)" == Linux ]] || { printf 'Run this script on Linux.\n' >&2; exit 2; }
exec "$script_dir/setup-and-run.sh" "$@"
