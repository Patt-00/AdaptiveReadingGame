#!/usr/bin/env bash
set -euo pipefail

mode="${1:-run}"
shift || true
case "$mode" in
    setup|run) ;;
    *) printf 'Usage: termux-database-session.sh {setup [--reconfigure] | run}\n' >&2; exit 2 ;;
esac

project_root="${ARG_PROJECT_ROOT:-/workspace/AdaptiveReadingGame}"
[[ -f "$project_root/pom.xml" ]] || { printf 'The project checkout is missing inside Debian PRoot.\n' >&2; exit 1; }
command -v mariadbd >/dev/null 2>&1 || { printf 'MariaDB is not installed in Debian. Run scripts/termux-setup-x11.sh first.\n' >&2; exit 1; }
command -v mariadb >/dev/null 2>&1 || { printf 'The MariaDB command-line client is missing in Debian.\n' >&2; exit 1; }

socket="/run/mysqld/mysqld.sock"
data_dir="/var/lib/mysql"
mkdir -p /run/mysqld "$data_dir" /root/.local/state/adaptive-reading-game

database_is_ready() {
    mariadb-admin --no-defaults --protocol=socket --socket="$socket" --user=root ping >/dev/null 2>&1
}

if [[ ! -d "$data_dir/mysql" ]]; then
    if [[ -n "$(find "$data_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
        printf 'MariaDB data files exist but its system tables are missing; refusing to initialize over them.\n' >&2
        exit 1
    fi
    mariadb-install-db --no-defaults --user=root --datadir="$data_dir" \
        --auth-root-authentication-method=socket --skip-test-db
fi

database_pid=""
started_here=false
cleanup_database() {
    if [[ "$started_here" == true ]]; then
        if [[ -n "$database_pid" ]]; then
            if mariadb-admin --no-defaults --protocol=socket --socket="$socket" --user=root shutdown >/dev/null 2>&1; then
                for _ in {1..10}; do
                    kill -0 "$database_pid" 2>/dev/null || break
                    sleep 0.2
                done
            fi
            if kill -0 "$database_pid" 2>/dev/null; then kill "$database_pid" 2>/dev/null || true; fi
            wait "$database_pid" 2>/dev/null || true
        fi
        started_here=false
    fi
}
trap cleanup_database EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if ! database_is_ready; then
    if pgrep -x mariadbd >/dev/null 2>&1; then
        printf 'MariaDB is already running but its local socket did not answer.\n' >&2
        exit 1
    fi
    mariadbd --no-defaults --user=root --datadir="$data_dir" --socket="$socket" \
        --pid-file=/run/mysqld/mysqld.pid --bind-address=127.0.0.1 --port=3306 \
        > /root/.local/state/adaptive-reading-game/mariadb.log 2>&1 &
    database_pid=$!
    started_here=true
    ready=false
    for _ in {1..30}; do
        if database_is_ready; then ready=true; break; fi
        if ! kill -0 "$database_pid" 2>/dev/null; then break; fi
        sleep 1
    done
    if [[ "$ready" != true ]]; then
        printf 'MariaDB did not start. Recent output:\n' >&2
        tail -n 40 /root/.local/state/adaptive-reading-game/mariadb.log >&2 || true
        exit 1
    fi
fi

export ARG_DB_ADMIN_MODE=socket
export ARG_DB_ADMIN_CLIENT=mariadb
cd "$project_root"

if [[ -n "${ARG_DB_CONFIG_FILE:-}" ]]; then
    config_file="$ARG_DB_CONFIG_FILE"
elif [[ -n "${XDG_CONFIG_HOME:-}" ]]; then
    config_file="$XDG_CONFIG_HOME/adaptive-reading-game/database.properties"
else
    config_file="/root/.config/adaptive-reading-game/database.properties"
fi
if [[ "$config_file" != /* ]]; then config_file="$project_root/$config_file"; fi
if [[ "$mode" == setup ]]; then
    if (($# > 0)); then
        mvn -q -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlLocalDatabaseSetup \
            "-Dexec.args=$*" exec:java
    else
        mvn -q -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlLocalDatabaseSetup exec:java
    fi
elif [[ -f "$config_file" ]]; then
    ./scripts/termux-run-javafx.sh
else
    printf 'Persistent database setup is missing. Run scripts/setup-termux.sh or scripts/termux-setup-database.sh first.\n' >&2
    exit 1
fi
