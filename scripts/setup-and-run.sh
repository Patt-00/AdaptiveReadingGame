#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
install_only=false

if [[ "${1:-}" == "--install-only" ]]; then
    install_only=true
elif [[ "$#" -gt 0 ]]; then
    printf 'Usage: %s [--install-only]\n' "$0" >&2
    exit 2
fi

fail() {
    printf 'Setup could not continue: %s\n' "$1" >&2
    exit 1
}

read_java_major() {
    if ! command -v java >/dev/null 2>&1; then
        return 1
    fi
    java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p'
}

read_javac_major() {
    if ! command -v javac >/dev/null 2>&1; then
        return 1
    fi
    javac -version 2>&1 | sed -n '1s/.*javac \([0-9][0-9]*\).*/\1/p'
}

java_major="$(read_java_major || true)"
javac_major="$(read_javac_major || true)"
need_jdk=false
if [[ -z "$java_major" || -z "$javac_major" ]] \
        || (( java_major < 21 )) \
        || (( javac_major < 21 )); then
    need_jdk=true
fi
need_maven=false
command -v mvn >/dev/null 2>&1 || need_maven=true

run_privileged() {
    if (( EUID == 0 )); then
        "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        fail "Administrator access is needed to install packages. Install JDK 21+ and Maven manually, then run scripts/run-game.sh."
    fi
}

if [[ "$need_jdk" == true || "$need_maven" == true ]]; then
    os_name="$(uname -s)"
    case "$os_name" in
        Darwin)
            command -v brew >/dev/null 2>&1 \
                || fail "Homebrew is required to install missing tools automatically. Install Homebrew, then rerun $0."
            if [[ "$need_jdk" == true ]]; then brew install openjdk@21; fi
            if [[ "$need_maven" == true ]]; then brew install maven; fi
            if [[ "$need_jdk" == true ]]; then
                java_home="$(brew --prefix openjdk@21)"
                export JAVA_HOME="$java_home"
                export PATH="$JAVA_HOME/bin:$PATH"
            fi
            if [[ "$need_maven" == true ]]; then
                maven_home="$(brew --prefix maven)"
                export PATH="$maven_home/bin:$PATH"
            fi
            ;;
        Linux)
            if [[ -r /etc/os-release ]]; then
                . /etc/os-release
            else
                ID=unknown
            fi
            if command -v dnf >/dev/null 2>&1; then
                packages=()
                [[ "$need_jdk" != true ]] || packages+=(java-21-openjdk-devel)
                [[ "$need_maven" != true ]] || packages+=(maven)
                run_privileged dnf install -y "${packages[@]}"
            elif command -v apt-get >/dev/null 2>&1; then
                packages=()
                [[ "$need_jdk" != true ]] || packages+=(openjdk-21-jdk)
                [[ "$need_maven" != true ]] || packages+=(maven)
                run_privileged apt-get update
                run_privileged apt-get install -y "${packages[@]}"
            else
                fail "No supported package manager was found for $ID. Install JDK 21+ and Maven manually, then rerun scripts/run-game.sh."
            fi
            ;;
        *)
            fail "Automatic installation is supported on Linux and macOS here. On $os_name, install JDK 21+ and Maven manually, then run scripts/run-game.sh."
            ;;
    esac
    hash -r
fi

"$script_dir/check-prerequisites.sh" --check-only

if [[ "$install_only" == true ]]; then
    printf 'Setup complete. Run scripts/run-game.sh to launch the game.\n'
    exit 0
fi

cd "$project_root"
exec "$script_dir/run-game.sh"
