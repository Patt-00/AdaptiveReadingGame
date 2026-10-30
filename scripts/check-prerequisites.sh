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
java_version="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
[[ -n "$java_version" ]] || fail 'Could not read the Java version. Run java -version to check your JDK.'
(( java_version >= 21 )) || fail "JDK 21 or newer is required; found Java $java_version."
printf 'OK: Java %s\n' "$java_version"

command -v javac >/dev/null 2>&1 || fail 'A JDK compiler is required. Install JDK 21 or newer; a JRE alone is not enough.'
javac_version="$(javac -version 2>&1 | sed -n 's/.*javac \([0-9][0-9]*\).*/\1/p' | head -n 1)"
[[ -n "$javac_version" ]] || fail 'Could not read the Java compiler version. Run javac -version to check your JDK.'
(( javac_version >= 21 )) || fail "JDK 21 or newer is required; found javac $javac_version."
printf 'OK: javac %s\n' "$javac_version"

command -v mvn >/dev/null 2>&1 || fail 'Install Maven and add mvn to PATH.'
maven_info="$(mvn -version 2>&1)" || fail 'Maven could not start. Check its Java setup.'
maven_line="$(printf '%s\n' "$maven_info" | sed -n '/^Apache Maven /{p;q;}')"
maven_java="$(printf '%s\n' "$maven_info" | sed -n 's/^Java version: \([0-9][0-9]*\).*/\1/p' | head -n 1)"
maven_runtime="$(printf '%s\n' "$maven_info" | sed -n 's/^Java version: .*runtime: //p' | head -n 1)"
[[ -n "$maven_line" && -n "$maven_java" ]] || fail 'Could not identify the Java runtime Maven uses. Run mvn -version.'
(( maven_java >= 21 )) || fail "Maven uses Java $maven_java; configure JAVA_HOME to a JDK 21 or newer."
[[ -n "$maven_runtime" && -x "$maven_runtime/bin/javac" ]] || fail 'Maven must use a JDK with javac. Check JAVA_HOME and mvn -version.'
printf 'OK: %s (Maven uses JDK %s)\n' "$maven_line" "$maven_java"

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

db_url="${ARG_DB_URL:-}"
db_user="${ARG_DB_USER:-}"
if [[ -z "${db_url//[[:space:]]/}" && -z "${db_user//[[:space:]]/}" && -z "${ARG_DB_PASSWORD+x}" ]]; then
    printf 'OK: No MySQL settings found; the game will use temporary in-memory storage.\n'
else
    [[ "$db_url" == jdbc:mysql://* ]] || fail 'ARG_DB_URL must be a MySQL JDBC URL beginning jdbc:mysql://.'
    [[ -n "${db_user//[[:space:]]/}" ]] || fail 'ARG_DB_USER is required when MySQL is configured.'
    [[ -n "${ARG_DB_PASSWORD+x}" ]] || fail 'ARG_DB_PASSWORD must be set when MySQL is configured.'
    printf 'OK: MySQL settings are present. Connectivity and migrations were not checked; apply V001 through V005 before launching.\n'
fi

asset_root="${ARG_ASSET_DIR:-$project_root/assets}"
[[ "$asset_root" == /* ]] || asset_root="$project_root/$asset_root"
pointer_count=0
for asset in "$asset_root"/characters/*.png "$asset_root"/scenes/*.jpg "$asset_root"/cg/*.png \
        "$project_root/src/main/resources/com/adaptivereadinggame/audio/comfortable-mystery.wav"; do
    [[ -f "$asset" ]] || continue
    (( $(wc -c < "$asset") <= 512 )) || continue
    if [[ "$(head -n 1 "$asset")" == 'version https://git-lfs.github.com/spec/v1' ]]; then
        ((pointer_count += 1))
    fi
done
if (( pointer_count > 0 )); then
    printf 'Warning: %s artwork or music files are Git LFS pointers; fetch game assets with Git LFS for the full presentation.\n' "$pointer_count" >&2
fi
for track in "$asset_root"/local-music/investigation.wav "$asset_root"/local-music/tense.wav; do
    if [[ ! -f "$track" ]]; then
        printf 'Info: Optional story music is missing: %s\n' "$track"
    fi
done
model_weights="$project_root/model-tool/models/Qwen2.5-0.5B-Instruct/model.safetensors"
if [[ ! -f "$model_weights" ]] || (( $(wc -c < "$model_weights") <= 512 )); then
    printf 'Info: Optional Qwen weights are unavailable locally; Java will keep its fallback unless the helper can load the pinned model.\n'
fi
printf 'Info: The optional model helper is started separately; see model-tool/README.md.\n'

if [[ "$check_only" == true ]]; then
    printf 'All startup prerequisites passed.\n'
    exit 0
fi

cd "$project_root"
exec mvn javafx:run
