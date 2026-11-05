#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
"$script_dir/check-prerequisites.sh" --check-only
project_root="$(cd -- "$script_dir/.." && pwd)"
cd "$project_root"

model_pid=""
model_python="$project_root/model-tool/.venv/bin/python"
cleanup_model_helper() {
    if [[ -n "$model_pid" ]] && kill -0 "$model_pid" 2>/dev/null; then
        kill "$model_pid" 2>/dev/null || true
        wait "$model_pid" 2>/dev/null || true
    fi
}
trap cleanup_model_helper EXIT INT TERM

if [[ "${ARG_DISABLE_MODEL_HELPER:-false}" != true && -x "$model_python" ]] \
        && "$model_python" -c 'import torch, transformers, huggingface_hub' >/dev/null 2>&1; then
    mkdir -p target
    PYTHONUNBUFFERED=1 "$model_python" model-tool/server.py >target/model-helper.log 2>&1 &
    model_pid=$!
    printf 'Starting the optional local model helper. Log: %s/target/model-helper.log\n' "$project_root"
else
    printf 'Model helper is not ready; the game will use Java difficulty fallback.\n'
fi

mvn javafx:run
