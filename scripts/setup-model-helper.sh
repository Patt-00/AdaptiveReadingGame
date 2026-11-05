#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
venv_dir="${ARG_MODEL_VENV_DIR:-$project_root/model-tool/.venv}"
venv_python="$venv_dir/bin/python"

usage() {
    cat <<'EOF'
Usage: scripts/setup-model-helper.sh [--check-only]

Prepare the optional local model helper. If dependencies are unavailable,
the game can still use Java's built-in difficulty fallback.
Linux, including ARM64, uses CPU-only PyTorch.
EOF
}

model_ready() {
    [[ -x "$venv_python" ]] && "$venv_python" -c 'import torch, transformers, huggingface_hub' >/dev/null 2>&1
}

case "${1:-}" in
    --help|-h) usage; exit 0 ;;
    --check-only)
        if model_ready; then printf 'Model helper dependencies are ready.\n'; exit 0; fi
        printf 'Model helper dependencies are not ready.\n' >&2
        exit 1
        ;;
    "") ;;
    *) usage >&2; exit 2 ;;
esac

if [[ "${ARG_DISABLE_MODEL_HELPER:-false}" == true ]]; then
    printf 'Model helper setup skipped by ARG_DISABLE_MODEL_HELPER.\n'
    exit 1
fi
if model_ready; then
    printf 'Model helper dependencies are ready.\n'
    exit 0
fi

python_command="${ARG_MODEL_PYTHON:-python3}"
if ! command -v "$python_command" >/dev/null 2>&1; then
    printf 'Python 3 is unavailable; the game will use Java difficulty fallback.\n' >&2
    exit 1
fi

if [[ ! -x "$venv_python" ]]; then
    "$python_command" -m venv "$venv_dir"
fi
[[ -x "$venv_python" ]] || { printf 'Could not create the model helper environment.\n' >&2; exit 1; }

requirements_cpu="$project_root/model-tool/requirements-cpu.txt"
requirements="$project_root/model-tool/requirements.txt"
os_name="$(uname -s)"
if [[ "$os_name" == Darwin ]]; then
    "$venv_python" -m pip install --disable-pip-version-check --no-cache-dir -r "$requirements_cpu"
else
    "$venv_python" -m pip install --disable-pip-version-check --no-cache-dir \
        --index-url https://download.pytorch.org/whl/cpu -r "$requirements_cpu"
fi
"$venv_python" -m pip install --disable-pip-version-check --no-cache-dir -r "$requirements"

if ! model_ready; then
    printf 'Model helper dependencies could not be verified; Java fallback remains available.\n' >&2
    exit 1
fi
printf 'Model helper dependencies are ready.\n'

weights="$project_root/model-tool/models/Qwen2.5-0.5B-Instruct/model.safetensors"
if [[ ! -s "$weights" || "$(wc -c < "$weights")" -le 512 ]]; then
    printf 'Model weights are not materialized locally; they may download from the pinned source at first start.\n'
fi
