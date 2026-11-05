#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
install_only=false
project_dir="${ARG_PROJECT_DIR:-}"

usage() {
    printf 'Usage: scripts/setup-termux.sh [--install-only] [--project-dir PATH]\n'
    printf 'Prepares Termux:X11, Debian, JavaFX, and the optional model helper.\n'
    printf 'Without --install-only, it launches the game after setup.\n'
}

is_project_root() {
    [[ -f "$1/pom.xml" && -d "$1/src/main/java" && -e "$1/.git" ]]
}

while (($# > 0)); do
    case "$1" in
        --install-only)
            install_only=true
            shift
            ;;
        --project-dir)
            (($# >= 2)) || { usage >&2; printf 'Pass a path after --project-dir.\n' >&2; exit 2; }
            project_dir="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            usage >&2
            printf 'Unknown option: %s\n' "$1" >&2
            exit 2
            ;;
    esac
done

if [[ -z "$project_dir" ]]; then
    if is_project_root "$PWD"; then
        project_dir="$PWD"
    elif is_project_root "$script_dir/.."; then
        project_dir="$(cd -- "$script_dir/.." && pwd)"
    else
        project_dir="$HOME/AdaptiveReadingGame-Termux"
    fi
fi
if [[ "$project_dir" != /* ]]; then project_dir="$PWD/$project_dir"; fi

"$script_dir/termux-setup-x11.sh" --project-dir "$project_dir"
if [[ "$install_only" == true ]]; then exit 0; fi
exec "$project_dir/scripts/termux-run-x11.sh"
