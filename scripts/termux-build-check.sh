#!/usr/bin/env bash
set -euo pipefail

repo_url="https://github.com/Patt-00/AdaptiveReadingGame.git"
project_dir="${ARG_PROJECT_DIR:-$HOME/AdaptiveReadingGame-Termux}"
install_only=false
project_dir_arg=false

usage() {
    cat <<'EOF'
Usage: scripts/termux-build-check.sh [--install-only] [--project-dir PATH]

Install missing Termux tools, then compile the Adaptive Reading Game sources.
The default project directory is $HOME/AdaptiveReadingGame-Termux.
This script checks compilation; it does not launch the JavaFX desktop UI.
EOF
}

fail() {
    printf 'Termux build check failed: %s\n' "$1" >&2
    exit 1
}

while (($# > 0)); do
    case "$1" in
        --install-only)
            install_only=true
            shift
            ;;
        --project-dir)
            (($# >= 2)) || fail 'Pass a path after --project-dir.'
            project_dir="$2"
            project_dir_arg=true
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            usage >&2
            fail "Unknown option: $1"
            ;;
    esac
done

[[ -n "${PREFIX:-}" && "$PREFIX" == */com.termux/files/usr ]] \
    || fail 'Run this script inside Termux on Android.'
command -v pkg >/dev/null 2>&1 \
    || fail 'Termux package command pkg is unavailable. Update or repair the Termux installation.'

java_major=""
if command -v java >/dev/null 2>&1; then
    java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
fi
javac_major=""
if command -v javac >/dev/null 2>&1; then
    javac_major="$(javac -version 2>&1 | sed -n '1s/.*javac \([0-9][0-9]*\).*/\1/p')"
fi

packages=()
command -v git >/dev/null 2>&1 || packages+=(git)
if [[ -z "$java_major" || -z "$javac_major" ]] \
        || (( java_major < 21 )) \
        || (( javac_major < 21 )); then
    packages+=(openjdk-21)
fi
command -v mvn >/dev/null 2>&1 || packages+=(maven)

if ((${#packages[@]} > 0)); then
    printf 'Installing missing Termux packages: %s\n' "${packages[*]}"
    pkg install -y "${packages[@]}"
    hash -r
fi

command -v git >/dev/null 2>&1 || fail 'Git is unavailable after package installation.'
command -v java >/dev/null 2>&1 || fail 'Java is unavailable after package installation.'
command -v javac >/dev/null 2>&1 || fail 'The Java compiler is unavailable after package installation.'
command -v mvn >/dev/null 2>&1 || fail 'Maven is unavailable after package installation.'

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
javac_major="$(javac -version 2>&1 | sed -n '1s/.*javac \([0-9][0-9]*\).*/\1/p')"
[[ -n "$java_major" && -n "$javac_major" ]] \
    || fail 'Could not read Java or javac version.'
(( java_major >= 21 && javac_major >= 21 )) \
    || fail "JDK 21 or newer is required; found java $java_major and javac $javac_major."

printf 'OK: Java %s and javac %s\n' "$java_major" "$javac_major"
printf 'OK: %s\n' "$(mvn -version 2>&1 | sed -n '1p')"

if [[ "$install_only" == true ]]; then
    printf 'Termux tools are ready. No project files were cloned or compiled.\n'
    exit 0
fi

if [[ "$project_dir_arg" == true && "$project_dir" != /* ]]; then
    project_dir="$PWD/$project_dir"
fi
if [[ -n "${ARG_PROJECT_DIR:-}" && "$project_dir" != /* ]]; then
    project_dir="$PWD/$project_dir"
fi

clone_project() {
    if [[ -e "$project_dir" ]] \
            && [[ -n "$(find "$project_dir" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
        fail "The project directory exists but has no project checkout: $project_dir"
    fi
    mkdir -p "$(dirname -- "$project_dir")"
    printf 'Cloning the main branch into %s (large Git LFS files are skipped).\n' "$project_dir"
    GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 --branch main "$repo_url" "$project_dir"
    project_root="$(cd -- "$project_dir" && pwd)"
}

is_checkout_root() {
    [[ -f "$1/pom.xml" && -d "$1/src/main/java" && -e "$1/.git" ]]
}

if [[ "$project_dir_arg" == true || -n "${ARG_PROJECT_DIR:-}" ]]; then
    if [[ -f "$project_dir/pom.xml" && -d "$project_dir/src/main/java" ]]; then
        project_root="$(cd -- "$project_dir" && pwd)"
    else
        clone_project
    fi
elif is_checkout_root "$PWD"; then
    project_root="$PWD"
elif is_checkout_root "${BASH_SOURCE[0]%/*}/.."; then
    project_root="$(cd -- "${BASH_SOURCE[0]%/*}/.." && pwd)"
elif [[ -f "$project_dir/pom.xml" && -d "$project_dir/src/main/java" ]]; then
    project_root="$(cd -- "$project_dir" && pwd)"
else
    clone_project
fi

[[ -f "$project_root/pom.xml" ]] || fail "pom.xml is missing from $project_root"
[[ -d "$project_root/src/main/java" ]] || fail "Java sources are missing from $project_root/src/main/java"

cd "$project_root"
printf 'Compiling project at %s\n' "$project_root"
if [[ "$(uname -m)" == "aarch64" ]]; then
    printf 'Using desktop Linux JavaFX classes for source compilation on ARM64.\n'
    printf 'This classifier is for compile checking only; its native libraries cannot run on this device.\n'
    export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }-Dos.arch=amd64"
fi
mvn -B -DskipTests compile

printf '\nJava source compilation passed on Termux with desktop Linux JavaFX classes.\n'
printf 'This check does not launch the game window or create an Android package.\n'
printf 'OpenJFX mobile guidance: https://openjfx.io/openjfx-docs/\n'
