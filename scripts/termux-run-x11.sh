#!/usr/bin/env bash
set -euo pipefail

fail() {
    printf 'Termux:X11 launch stopped: %s\n' "$1" >&2
    exit 1
}

[[ -n "${PREFIX:-}" && "$PREFIX" == */com.termux/files/usr ]] \
    || fail 'Run this script from Termux on Android.'
command -v proot-distro >/dev/null 2>&1 \
    || fail 'Run scripts/termux-setup-x11.sh first to install the launcher requirements.'
command -v termux-x11 >/dev/null 2>&1 \
    || fail 'Run scripts/termux-setup-x11.sh first to install the Termux:X11 package.'
command -v pm >/dev/null 2>&1 \
    && pm path com.termux.x11 2>/dev/null | grep -q '^package:' \
    || fail 'Install the separate Termux:X11 Android app before launching the game: https://github.com/termux/termux-x11/releases'

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
[[ -f "$project_root/pom.xml" && -d "$project_root/src/main/java" ]] \
    || fail "Run this script from a complete project checkout. Expected pom.xml under $project_root."

display="${ARG_TERMUX_X11_DISPLAY:-}"
if [[ -z "$display" ]]; then
    for display_number in 0 1; do
        if [[ -S "$PREFIX/tmp/.X11-unix/X$display_number" ]]; then
            display=":$display_number"
            break
        fi
    done
fi
display="${display:-:0}"
[[ "$display" =~ ^:[0-9]+$ ]] \
    || fail 'ARG_TERMUX_X11_DISPLAY must use a display such as :0 or :1.'

display_number="${display#:}"
display_socket="$PREFIX/tmp/.X11-unix/X$display_number"
log_dir="$HOME/.local/state/adaptive-reading-game"
mkdir -p "$log_dir"

running_display=""
if command -v pidof >/dev/null 2>&1; then
    for pid in $(pidof termux-x11 2>/dev/null || true); do
        [[ -r "/proc/$pid/cmdline" ]] || continue
        while IFS= read -r argument; do
            if [[ "$argument" =~ ^:[0-9]+$ ]]; then
                running_display="$argument"
                break
            fi
        done < <(tr '\0' '\n' < "/proc/$pid/cmdline")
        [[ -n "$running_display" ]] && break
    done
fi

if [[ ! -S "$display_socket" ]]; then
    if [[ -n "$running_display" && "$running_display" != "$display" ]]; then
        fail "Termux:X11 is already running on $running_display. Set ARG_TERMUX_X11_DISPLAY=$running_display or stop that X11 session."
    fi
    if [[ -z "$running_display" ]]; then
        printf 'Starting Termux:X11 on display %s.\n' "$display"
        termux-x11 "$display" >"$log_dir/x11.log" 2>&1 &
    fi

    for ((attempt = 0; attempt < 20; attempt++)); do
        [[ -S "$display_socket" ]] && break
        sleep 1
    done
fi
[[ -S "$display_socket" ]] \
    || fail "X11 did not create $display_socket. Check $log_dir/x11.log and open the Termux:X11 app."

if command -v am >/dev/null 2>&1; then
    am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1 || true
fi

if [[ ! -f "$PREFIX/var/lib/proot-distro/containers/debian/rootfs/etc/debian_version" ]]; then
    fail 'The Debian PRoot environment is missing. Run scripts/termux-setup-x11.sh first.'
fi

if head -n 1 "$project_root/src/main/resources/com/adaptivereadinggame/audio/comfortable-mystery.wav" \
        2>/dev/null | grep -q 'git-lfs.github.com/spec/v1'; then
    fail 'Game media is still a Git LFS pointer. Run scripts/termux-setup-x11.sh to download the required assets.'
fi

prism_order="${ARG_TERMUX_PRISM_ORDER:-sw}"
[[ "$prism_order" == sw || "$prism_order" == es2 ]] \
    || fail 'ARG_TERMUX_PRISM_ORDER must be sw or es2.'

printf 'Launching Adaptive Reading Game on Termux:X11 display %s. Close the game window to stop the launcher.\n' "$display"
exec proot-distro login debian --shared-tmp \
    --bind "$project_root:/workspace/AdaptiveReadingGame" \
    --env "DISPLAY=$display" \
    --env TMPDIR=/tmp \
    --env XDG_RUNTIME_DIR=/tmp \
    --env "MAVEN_OPTS=-Dprism.order=$prism_order" \
    debian -- /bin/bash -lc 'cd /workspace/AdaptiveReadingGame && exec mvn javafx:run'
