#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

# Do not let Android-side native library overrides leak into Debian's JVM.
unset LD_LIBRARY_PATH LD_PRELOAD

command -v java >/dev/null 2>&1 || { printf 'Java is unavailable in Debian.\n' >&2; exit 1; }
command -v mvn >/dev/null 2>&1 || { printf 'Maven is unavailable in Debian.\n' >&2; exit 1; }

mkdir -p target
printf 'Checking and compiling changed Java sources...\n'
mvn -q -DskipTests compile

runtime_classpath_file="$project_root/target/termux-runtime-classpath.txt"
refresh_classpath=false
if [[ ! -s "$runtime_classpath_file" || "$project_root/pom.xml" -nt "$runtime_classpath_file" ]]; then
    refresh_classpath=true
else
    cached_classpath="$(<"$runtime_classpath_file")"
    IFS=: read -r -a cached_dependencies <<< "$cached_classpath"
    for dependency in "${cached_dependencies[@]}"; do
        [[ -f "$dependency" ]] || { refresh_classpath=true; break; }
    done
fi

if [[ "$refresh_classpath" == true ]]; then
    printf 'Refreshing the Maven runtime classpath cache...\n'
    mvn -q -DincludeScope=runtime \
        "-Dmdep.outputFile=$runtime_classpath_file" \
        dependency:build-classpath
fi

runtime_classpath="$(<"$runtime_classpath_file")"
[[ -n "$runtime_classpath" ]] || { printf 'Maven produced an empty runtime classpath.\n' >&2; exit 1; }

module_path=""
application_classpath="$project_root/target/classes"
IFS=: read -r -a dependencies <<< "$runtime_classpath"
for dependency in "${dependencies[@]}"; do
    [[ -f "$dependency" ]] || { printf 'Runtime dependency is missing: %s\n' "$dependency" >&2; exit 1; }
    if [[ "$dependency" == */org/openjfx/javafx-*/javafx-*.jar ]]; then
        module_path="${module_path:+$module_path:}$dependency"
    else
        application_classpath="$application_classpath:$dependency"
    fi
done

[[ -n "$module_path" ]] || { printf 'JavaFX modules were not found in the Maven runtime dependencies.\n' >&2; exit 1; }
[[ -f "$project_root/target/classes/com/adaptivereadinggame/AdaptiveReadingGameApp.class" ]] \
    || { printf 'The game entry point was not compiled.\n' >&2; exit 1; }

java_command=(java "-Dprism.order=${ARG_TERMUX_PRISM_ORDER:-sw}")
if [[ "${ARG_TERMUX_MEDIA_DEBUG:-false}" == true ]]; then
    java_command+=("-Djfxmedia.loglevel=debug")
fi
java_command+=(
    --module-path "$module_path"
    --add-modules javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media
    -classpath "$application_classpath"
    com.adaptivereadinggame.AdaptiveReadingGameApp
)

if command -v pactl >/dev/null 2>&1; then
    if pactl info >/dev/null 2>&1; then
        printf 'Debian can reach the Termux PulseAudio server.\n'
    else
        printf 'Warning: Debian cannot reach the Termux PulseAudio server; the game may be silent.\n' >&2
    fi
fi

printf 'Starting Adaptive Reading Game with the JavaFX version from pom.xml.\n'
exec "${java_command[@]}"
