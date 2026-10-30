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
command -v xdpyinfo >/dev/null 2>&1 \
    || fail 'Run scripts/termux-setup-x11.sh first to install the X11 display checker.'
command -v am >/dev/null 2>&1 \
    || fail 'Android Activity Manager am is unavailable in this Termux session.'

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$script_dir/termux-audio-bridge.sh"
project_root="$(cd -- "$script_dir/.." && pwd)"
[[ -f "$project_root/pom.xml" && -d "$project_root/src/main/java" ]] \
    || fail "Run this script from a complete project checkout. Expected pom.xml under $project_root."

display="${ARG_TERMUX_X11_DISPLAY:-}"
display_ready=false
if [[ -z "$display" ]]; then
    for display_number in 0 1; do
        candidate_display=":$display_number"
        if [[ -S "$PREFIX/tmp/.X11-unix/X$display_number" ]] \
                && DISPLAY="$candidate_display" xdpyinfo >/dev/null 2>&1; then
            display=":$display_number"
            display_ready=true
            break
        fi
    done
fi
display="${display:-:0}"
[[ "$display" =~ ^:[0-9]+$ ]] \
    || fail 'ARG_TERMUX_X11_DISPLAY must use a display such as :0 or :1.'

display_number="${display#:}"
display_socket="$PREFIX/tmp/.X11-unix/X$display_number"
if [[ "$display_ready" != true ]] && [[ -S "$display_socket" ]] \
        && DISPLAY="$display" xdpyinfo >/dev/null 2>&1; then
    display_ready=true
fi
log_dir="$HOME/.local/state/adaptive-reading-game"
mkdir -p "$log_dir"
x11_log_file="$log_dir/x11.log"

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

if [[ -n "$running_display" && "$running_display" != "$display" ]]; then
    fail "Termux:X11 is already running on $running_display. Set ARG_TERMUX_X11_DISPLAY=$running_display or stop that X11 session."
fi
if [[ "$display_ready" != true && -z "$running_display" ]]; then
    if [[ -S "$display_socket" ]]; then
        printf 'Removing stale Termux:X11 socket %s.\n' "$display_socket"
        rm -f -- "$display_socket"
    fi
    printf 'Starting Termux:X11 on display %s.\n' "$display"
    : >"$x11_log_file"
    TERMUX_X11_DEBUG=1 termux-x11 "$display" >"$x11_log_file" 2>&1 &
fi

activity_start_result=""
for ((attempt = 0; attempt < 5; attempt++)); do
    activity_start_result="$(am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity 2>&1 || true)"
    if [[ "$activity_start_result" == *"Starting: Intent"* \
            || "$activity_start_result" == *"Warning: Activity not started"* ]]; then
        break
    fi
    if [[ "$activity_start_result" == *"Error type 3"* \
            || "$activity_start_result" == *"does not exist"* \
            || "$activity_start_result" == *"unable to resolve Intent"* ]]; then
        fail 'Install the separate Termux:X11 Android app before launching the game: https://github.com/termux/termux-x11/releases'
    fi
    sleep 1
done
if [[ "$activity_start_result" != *"Starting: Intent"* \
        && "$activity_start_result" != *"Warning: Activity not started"* ]]; then
    printf 'Warning: Android did not confirm Termux:X11 activity startup. Continuing with the active X11 display.\n' >&2
fi

# The Android app must be open before waiting for the display. A stale socket
# can remain after Termux:X11 exits, so readiness requires an X11 client reply.
for ((attempt = 0; attempt < 30; attempt++)); do
    if [[ -S "$display_socket" ]] && DISPLAY="$display" xdpyinfo >/dev/null 2>&1; then
        display_ready=true
        break
    fi
    sleep 1
done
if [[ "$display_ready" != true ]]; then
    if [[ -S "$display_socket" ]]; then
        printf 'Termux:X11 created %s, but the display did not answer an X11 request.\n' "$display_socket" >&2
    else
        printf 'Termux:X11 did not create the expected display socket: %s\n' "$display_socket" >&2
    fi
    printf 'Activity Manager response: %s\n' "${activity_start_result:-none}" >&2
    printf 'Termux:X11 log: %s\n' "$x11_log_file" >&2
    if [[ -s "$x11_log_file" ]]; then
        printf '%s\n' 'Recent Termux:X11 output:' >&2
        tail -n 40 "$x11_log_file" >&2
    else
        printf '%s\n' 'The Termux:X11 log is empty. Confirm that the separate Termux:X11 Android app is installed and open.' >&2
    fi
    fail 'Display startup failed. Open Termux:X11, then rerun this script. If the app is open, use the log above to identify the server error.'
fi

if prepare_termux_audio_bridge; then
    printf 'Termux PulseAudio output is ready.\n'
else
    printf 'Warning: Termux PulseAudio has no usable Android sink; the game will run without sound.\n' >&2
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

proot_arguments=(
    login --detach --shared-tmp
    --bind "$project_root:/workspace/AdaptiveReadingGame"
    --env "DISPLAY=$display"
    --env TMPDIR=/tmp
    --env XDG_RUNTIME_DIR=/tmp
    --env PULSE_SERVER=tcp:127.0.0.1:4713
    --env ALSA_CONFIG_PATH=/workspace/AdaptiveReadingGame/scripts/termux-asoundrc
    --env ARG_FULL_SCREEN=true
    --env "ARG_TERMUX_PRISM_ORDER=$prism_order"
    --env "ARG_TERMUX_MEDIA_DEBUG=${ARG_TERMUX_MEDIA_DEBUG:-false}"
)

xauthority_file="${XAUTHORITY:-$HOME/.Xauthority}"
if [[ -f "$xauthority_file" ]]; then
    proot_arguments+=(--bind "$xauthority_file:/root/.Xauthority" --env XAUTHORITY=/root/.Xauthority)
fi

pulse_cookie_file="$HOME/.config/pulse/cookie"
if [[ -f "$pulse_cookie_file" ]]; then
    proot_arguments+=(
        --bind "$pulse_cookie_file:/tmp/adaptive-reading-game-pulse-cookie"
        --env PULSE_COOKIE=/tmp/adaptive-reading-game-pulse-cookie
    )
fi

printf 'Launching Adaptive Reading Game on Termux:X11 display %s. Close the game window to stop the launcher.\n' "$display"
mkdir -p "$project_root/target"
log_name="termux-game-$(date +%Y%m%d-%H%M%S)-$$.log"
log_file="$project_root/target/$log_name"
: >"$log_file"
ln -sfn "$log_name" "$project_root/target/termux-game.log"
proot_arguments+=(--env "ARG_GAME_LOG_NAME=$log_name")
proot-distro "${proot_arguments[@]}" debian -- \
    /bin/bash -lc 'cd /workspace/AdaptiveReadingGame && exec ./scripts/termux-run-javafx.sh > "target/${ARG_GAME_LOG_NAME:?}" 2>&1'

for ((attempt = 0; attempt < 60; attempt++)); do
    if grep -Eq 'Loaded [0-9]+ story chapter' "$log_file" 2>/dev/null; then
        printf 'Game is ready in Termux:X11 full screen.\n'
        printf 'Log: %s\n' "$log_file"
        exit 0
    fi
    if grep -Eq '\[ERROR\]|Exception in Application|Error occurred during initialization|Unable to open DISPLAY' "$log_file" 2>/dev/null; then
        tail -n 40 "$log_file" >&2
        fail "The game exited during startup. Check $log_file for details."
    fi
    sleep 1
done

printf 'Game startup is still in progress in Termux:X11.\n'
printf 'Log: %s\n' "$log_file"
