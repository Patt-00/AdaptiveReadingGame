#!/usr/bin/env bash

termux_model_venv_for_proot() {
    local project_root="${1:?Project root is required}"
    local configured="${ARG_MODEL_VENV_DIR:-model-tool/.venv}"

    case "$configured" in
        "$project_root"/*)
            configured="/workspace/AdaptiveReadingGame/${configured#"$project_root"/}"
            ;;
        /workspace/AdaptiveReadingGame/*)
            ;;
        /*)
            ;;
        *)
            configured="/workspace/AdaptiveReadingGame/$configured"
            ;;
    esac
    printf '%s\n' "$configured"
}
