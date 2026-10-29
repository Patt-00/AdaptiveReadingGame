#!/usr/bin/env bash

# Shared host-side PulseAudio setup for the Termux setup and launch scripts.
prepare_termux_audio_bridge() {
    command -v pulseaudio >/dev/null 2>&1 || return 1
    command -v pactl >/dev/null 2>&1 || return 1

    if ! pactl info >/dev/null 2>&1; then
        pulseaudio --start --exit-idle-time=-1 >/dev/null 2>&1 || true
        for ((attempt = 0; attempt < 10; attempt++)); do
            pactl info >/dev/null 2>&1 && break
            sleep 1
        done
    fi
    pactl info >/dev/null 2>&1 || return 1

    if ! pactl list short modules 2>/dev/null \
            | awk '$2 == "module-native-protocol-tcp" { found = 1 } END { exit !found }'; then
        pulse_cookie="$HOME/.config/pulse/cookie"
        if [[ -f "$pulse_cookie" ]]; then
            pactl load-module module-native-protocol-tcp \
                auth-ip-acl=127.0.0.1 >/dev/null 2>&1 || return 1
        else
            pactl load-module module-native-protocol-tcp \
                auth-ip-acl=127.0.0.1 auth-anonymous=1 >/dev/null 2>&1 || return 1
        fi
    fi

    pactl list short modules 2>/dev/null \
        | awk '$2 == "module-native-protocol-tcp" { found = 1 } END { exit !found }' \
        || return 1
    pactl list short sinks 2>/dev/null \
        | awk '$2 != "auto_null" { found = 1 } END { exit !found }'
}
