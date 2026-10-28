#!/usr/bin/env bash
set -euo pipefail

repo_url="https://github.com/Patt-00/AdaptiveReadingGame.git"
project_dir="${ARG_PROJECT_DIR:-$HOME/AdaptiveReadingGame-Termux}"
project_dir_explicit=false

usage() {
    cat <<'EOF'
Usage: scripts/termux-setup-x11.sh [--project-dir PATH]

Install the Termux:X11 and Debian PRoot prerequisites for Adaptive Reading Game.
The setup compiles the Java sources and downloads game artwork and menu music.
The optional Qwen model and MySQL server are not required to launch the game.

Options:
  --project-dir PATH  Use or create a project checkout at PATH.
  --help              Show this help.
EOF
}

fail() {
    printf 'Termux setup stopped: %s\n' "$1" >&2
    exit 1
}

is_project_root() {
    [[ -f "$1/pom.xml" && -d "$1/src/main/java" && -e "$1/.git" ]]
}

package_installed() {
    dpkg-query -W -f='${Status}' "$1" 2>/dev/null | grep -qx 'install ok installed'
}

while (($# > 0)); do
    case "$1" in
        --project-dir)
            (($# >= 2)) || fail 'Pass a path after --project-dir.'
            project_dir="$2"
            project_dir_explicit=true
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
    || fail 'Run this script from Termux on Android.'
command -v pkg >/dev/null 2>&1 || fail 'Termux package command pkg is unavailable.'
[[ "$(uname -m)" == aarch64 ]] \
    || fail "This launcher setup expects an ARM64 Android device; found $(uname -m)."

if [[ "$project_dir_explicit" == true && "$project_dir" != /* ]]; then
    project_dir="$PWD/$project_dir"
elif [[ -n "${ARG_PROJECT_DIR:-}" && "$project_dir" != /* ]]; then
    project_dir="$PWD/$project_dir"
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ "$project_dir_explicit" == true || -n "${ARG_PROJECT_DIR:-}" ]]; then
    if is_project_root "$project_dir"; then
        project_root="$(cd -- "$project_dir" && pwd)"
    elif [[ -e "$project_dir" ]] \
            && [[ -n "$(find "$project_dir" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
        fail "The selected directory has files but is not an Adaptive Reading Game checkout: $project_dir"
    else
        project_root="$project_dir"
    fi
elif is_project_root "$PWD"; then
    project_root="$(pwd)"
elif is_project_root "$script_dir/.."; then
    project_root="$(cd -- "$script_dir/.." && pwd)"
else
    project_root="$project_dir"
fi

if ! is_project_root "$project_root"; then
    if [[ -e "$project_root" ]] \
            && [[ -n "$(find "$project_root" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
        fail "The project directory exists but has no checkout: $project_root"
    fi

    host_packages=()
    package_installed git || host_packages+=(git)
    if ((${#host_packages[@]} > 0)); then
        pkg update -y
        pkg install -y "${host_packages[@]}"
        hash -r
    fi

    mkdir -p "$(dirname -- "$project_root")"
    printf 'Cloning the main branch into %s. Large Git LFS files will be downloaded in Debian.\n' "$project_root"
    GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 --branch main "$repo_url" "$project_root"
    project_root="$(cd -- "$project_root" && pwd)"
fi

if ! package_installed x11-repo; then
    pkg install -y x11-repo
    pkg update -y
fi

host_packages=()
for package in git proot-distro procps termux-x11-nightly; do
    package_installed "$package" || host_packages+=("$package")
done
if ((${#host_packages[@]} > 0)); then
    pkg update -y
    printf 'Installing Termux packages: %s\n' "${host_packages[*]}"
    pkg install -y "${host_packages[@]}"
    hash -r
fi

command -v proot-distro >/dev/null 2>&1 || fail 'proot-distro is unavailable after installation.'
command -v termux-x11 >/dev/null 2>&1 || fail 'termux-x11 is unavailable after installation.'

x11_app_installed=false
if command -v pm >/dev/null 2>&1 \
        && pm path com.termux.x11 2>/dev/null | grep -q '^package:'; then
    x11_app_installed=true
fi

if ! proot-distro list | awk '
    /Installed containers:/ { in_installed = 1; next }
    /Log in with:/ { in_installed = 0 }
    in_installed {
        gsub(/^[[:space:]]*\*[[:space:]]*/, "")
        gsub(/^[[:space:]]+|[[:space:]]+$/, "")
        if ($0 == "debian") found = 1
    }
    END { exit !found }
'; then
    printf 'Installing the Debian Linux environment.\n'
    proot-distro install debian
fi

printf 'Checking Debian packages and preparing JavaFX for Linux ARM64.\n'
proot-distro login debian --shared-tmp \
    --bind "$project_root:/workspace/AdaptiveReadingGame" \
    -- /bin/bash -s <<'DEBIAN_SETUP'
set -euo pipefail

apt-get update -qq

package_available() {
    apt-cache show "$1" 2>/dev/null | grep -qm1 '^Package:'
}

gtk_package=libgtk-3-0
alsa_package=libasound2
package_available libgtk-3-0t64 && gtk_package=libgtk-3-0t64
package_available libasound2t64 && alsa_package=libasound2t64

packages=(
    openjdk-21-jdk maven git git-lfs
    "$gtk_package" "$alsa_package"
    libfontconfig1 libgl1 libegl1 libxtst6 libxi6 libxrender1
    libxrandr2 libxinerama1 libxxf86vm1
    gstreamer1.0-plugins-base gstreamer1.0-plugins-good
)
missing_packages=()
for package in "${packages[@]}"; do
    dpkg-query -W -f='${Status}' "$package" 2>/dev/null \
        | grep -qx 'install ok installed' || missing_packages+=("$package")
done
if ((${#missing_packages[@]} > 0)); then
    printf 'Installing Debian packages: %s\n' "${missing_packages[*]}"
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "${missing_packages[@]}"
fi

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
javac_major="$(javac -version 2>&1 | sed -n '1s/.*javac \([0-9][0-9]*\).*/\1/p')"
[[ -n "$java_major" && -n "$javac_major" ]] \
    || { printf 'Could not read the Java runtime or compiler version.\n' >&2; exit 1; }
(( java_major >= 21 && javac_major >= 21 )) \
    || { printf 'JDK 21 or newer is required; found java %s and javac %s.\n' "$java_major" "$javac_major" >&2; exit 1; }

command -v git-lfs >/dev/null 2>&1 \
    || { printf 'Git LFS is unavailable after installation.\n' >&2; exit 1; }
git -C /workspace/AdaptiveReadingGame lfs install --local
git -C /workspace/AdaptiveReadingGame lfs pull \
    --include='assets/**,src/main/resources/com/adaptivereadinggame/audio/comfortable-mystery.wav'

cd /workspace/AdaptiveReadingGame
mvn -B -DskipTests compile

printf '\nDebian JavaFX setup is ready.\n'
printf 'Java: %s, javac: %s\n' "$java_major" "$javac_major"
printf 'Maven: %s\n' "$(mvn -version 2>&1 | sed -n '1p')"
DEBIAN_SETUP

if [[ "$x11_app_installed" != true ]]; then
    printf '\nTermux packages and Debian dependencies are ready. Install the separate Termux:X11 Android app before launch:\n' >&2
    printf 'https://github.com/termux/termux-x11/releases\n' >&2
    exit 1
fi

printf '\nSetup complete for %s. Run scripts/termux-run-x11.sh from the project root to open the game.\n' "$project_root"
