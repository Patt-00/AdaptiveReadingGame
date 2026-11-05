#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
reconfigure=false

while (($# > 0)); do
    case "$1" in
        --reconfigure) reconfigure=true; shift ;;
        --help|-h)
            printf 'Usage: scripts/termux-setup-database.sh [--reconfigure]\n'
            exit 0
            ;;
        *) printf 'Unknown option: %s\n' "$1" >&2; exit 2 ;;
    esac
done

[[ -n "${PREFIX:-}" && "$PREFIX" == */com.termux/files/usr ]] \
    || { printf 'Run this script from Termux on Android.\n' >&2; exit 1; }
command -v proot-distro >/dev/null 2>&1 \
    || { printf 'Run scripts/termux-setup-x11.sh first to install Debian PRoot.\n' >&2; exit 1; }
[[ -f "$PREFIX/var/lib/proot-distro/containers/debian/rootfs/etc/debian_version" ]] \
    || { printf 'The Debian PRoot environment is missing. Run scripts/termux-setup-x11.sh first.\n' >&2; exit 1; }

database_arguments=(setup)
if [[ "$reconfigure" == true ]]; then database_arguments+=(--reconfigure); fi
proot_arguments=(login --shared-tmp --bind "$project_root:/workspace/AdaptiveReadingGame")
if [[ -n "${ARG_DB_CONFIG_FILE:-}" ]]; then
    proot_arguments+=(--env "ARG_DB_CONFIG_FILE=$ARG_DB_CONFIG_FILE")
fi
proot-distro "${proot_arguments[@]}" debian \
    -- /bin/bash -lc 'cd /workspace/AdaptiveReadingGame && ./scripts/termux-database-session.sh "$@"' \
    bash "${database_arguments[@]}"
