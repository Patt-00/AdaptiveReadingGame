#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
"$script_dir/check-prerequisites.sh" --check-only
project_root="$(cd -- "$script_dir/.." && pwd)"
cd "$project_root"
exec mvn javafx:run
