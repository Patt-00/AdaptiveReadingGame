#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
install_only=false

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
    printf 'Usage: scripts/setup-termux.sh [--install-only] [--project-dir PATH]\n'
    printf 'Prepares Termux:X11, Debian, JavaFX, and the optional model helper.\n'
    printf 'Without --install-only, it launches the game after setup.\n'
    exit 0
fi

if [[ "${1:-}" == "--install-only" ]]; then
    install_only=true
    shift
fi

"$script_dir/termux-setup-x11.sh" "$@"
if [[ "$install_only" == true ]]; then exit 0; fi
exec "$script_dir/termux-run-x11.sh"
