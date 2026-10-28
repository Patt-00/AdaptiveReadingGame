#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
check_only=false

if [[ "${1:-}" == "--check-only" ]]; then
    check_only=true
elif [[ "$#" -gt 0 ]]; then
    printf 'Usage: %s [--check-only]\n' "$0" >&2
    exit 2
fi

fail() {
    printf 'Prerequisite check failed: %s\n' "$1" >&2
    exit 1
}

command -v java >/dev/null 2>&1 || fail 'Install JDK 21 or newer and add java to PATH.'
java_version="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
[[ -n "$java_version" ]] || fail 'Could not read the Java version. Run java -version to check your JDK.'
(( java_version >= 21 )) || fail "JDK 21 or newer is required; found Java $java_version."
printf 'OK: Java %s\n' "$java_version"

command -v javac >/dev/null 2>&1 || fail 'A JDK compiler is required. Install JDK 21 or newer; a JRE alone is not enough.'
javac_version="$(javac -version 2>&1 | sed -n '1s/.*javac \([0-9][0-9]*\).*/\1/p')"
[[ -n "$javac_version" ]] || fail 'Could not read the Java compiler version. Run javac -version to check your JDK.'
(( javac_version >= 21 )) || fail "JDK 21 or newer is required; found javac $javac_version."
printf 'OK: javac %s\n' "$javac_version"

command -v mvn >/dev/null 2>&1 || fail 'Install Maven and add mvn to PATH.'
maven_info="$(mvn -version 2>&1)" || fail 'Maven could not start. Check its Java setup.'
printf 'OK: %s\n' "$(printf '%s\n' "$maven_info" | sed -n '1p')"

[[ -f "$project_root/pom.xml" ]] || fail 'pom.xml is missing. Run this script from a project checkout.'

configured_pack="${ARG_CONTENT_PACK:-}"
if [[ -n "$configured_pack" ]]; then
    if [[ "$configured_pack" != /* ]]; then
        configured_pack="$project_root/$configured_pack"
    fi
    [[ -f "$configured_pack" ]] || fail "ARG_CONTENT_PACK does not point to a file: $configured_pack"
    printf 'OK: Story pack: %s\n' "$configured_pack"
else
    default_pack="$project_root/assets/content/hailey-investigation-complete.json"
    [[ -f "$default_pack" ]] || fail "Default story pack is missing: $default_pack. Restore it or set ARG_CONTENT_PACK."
    printf 'OK: Default story pack: %s\n' "$default_pack"
fi

db_values=("${ARG_DB_URL:-}" "${ARG_DB_USER:-}" "${ARG_DB_PASSWORD:-}")
db_set=0
for value in "${db_values[@]}"; do
    [[ -z "$value" ]] || ((db_set += 1))
done
if (( db_set == 0 )); then
    printf 'OK: No MySQL settings found; the game will use temporary in-memory storage.\n'
elif (( db_set == 3 )); then
    printf 'OK: MySQL settings are present. Confirm migrations V001 through V005 have been applied before launching.\n'
else
    fail 'Set all three variables ARG_DB_URL, ARG_DB_USER, and ARG_DB_PASSWORD, or clear all three to use temporary storage.'
fi

if [[ "$check_only" == true ]]; then
    printf 'All startup prerequisites passed.\n'
    exit 0
fi

cd "$project_root"
exec mvn javafx:run
