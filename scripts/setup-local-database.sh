#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
reconfigure=false
check_only=false
temporary_directory=""

cleanup_temporary_directory() {
    if [[ -n "$temporary_directory" && -d "$temporary_directory" ]]; then
        rm -rf -- "$temporary_directory"
    fi
}
trap cleanup_temporary_directory EXIT

usage() {
    cat <<'EOF'
Usage: scripts/setup-local-database.sh [--reconfigure | --check-only]

Install or start the local MySQL server, then create the local app database.
The first setup asks for the administrator and application credentials.
EOF
}

fail() {
    printf 'Database setup failed: %s\n' "$1" >&2
    exit 1
}

while (($# > 0)); do
    case "$1" in
        --reconfigure) reconfigure=true; shift ;;
        --check-only) check_only=true; shift ;;
        --help|-h) usage; exit 0 ;;
        *) usage >&2; fail "Unknown option: $1" ;;
    esac
done

cd "$project_root"
if [[ "$check_only" == true ]]; then
    mvn -q -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlLocalDatabaseSetup \
        -Dexec.args=--check-only exec:java
    exit $?
fi

run_privileged() {
    if (( EUID == 0 )); then
        "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        fail 'Administrator access is required to install or start the local database server.'
    fi
}

start_linux_service() {
    local service_name="$1"
    if command -v systemctl >/dev/null 2>&1; then
        if run_privileged systemctl enable --now "$service_name"; then return 0; fi
    fi
    if command -v service >/dev/null 2>&1; then
        run_privileged service "$service_name" start
    else
        fail 'No supported service manager was found. Start the local MySQL service manually, then rerun setup.'
    fi
}

restart_linux_service() {
    local service_name="$1"
    if command -v systemctl >/dev/null 2>&1; then
        run_privileged systemctl enable "$service_name"
        run_privileged systemctl restart "$service_name"
    elif command -v service >/dev/null 2>&1; then
        run_privileged service "$service_name" restart
    else
        fail 'No supported service manager was found to apply the loopback-only MySQL setting.'
    fi
}

start_or_restart_linux_service() {
    local service_name="$1" configuration_changed="$2"
    if [[ "$configuration_changed" == true ]]; then
        if command -v systemctl >/dev/null 2>&1 \
                && run_privileged systemctl is-active --quiet "$service_name" >/dev/null 2>&1; then
            restart_linux_service "$service_name"
        elif command -v service >/dev/null 2>&1 \
                && run_privileged service "$service_name" status >/dev/null 2>&1; then
            run_privileged service "$service_name" restart
        else
            start_linux_service "$service_name"
        fi
    else
        start_linux_service "$service_name"
    fi
}

install_loopback_config() {
    local destination="$1" directory temporary_file
    directory="$(dirname -- "$destination")"
    [[ -d "$directory" ]] \
        || fail "The MySQL option include directory is missing: $directory"
    if [[ -e "$destination" ]]; then
        if cmp -s <(printf '[mysqld]\nbind-address=127.0.0.1\nmysqlx-bind-address=127.0.0.1\n') "$destination"; then
            return 1
        fi
        fail "Refusing to overwrite an existing MySQL option file: $destination"
    fi

    if [[ -z "$temporary_directory" ]]; then temporary_directory="$(mktemp -d)"; fi
    temporary_file="$temporary_directory/adaptive-reading-game-mysql.cnf"
    printf '[mysqld]\nbind-address=127.0.0.1\nmysqlx-bind-address=127.0.0.1\n' >"$temporary_file"
    run_privileged install -o root -g root -m 0644 "$temporary_file" "$destination" \
        || fail "Could not write the loopback-only MySQL setting to $destination."
    return 0
}

install_apt_mysql() {
    local architecture package
    architecture="$(dpkg --print-architecture)"
    [[ "$architecture" == amd64 || "$architecture" == i386 ]] \
        || fail "The MySQL APT repository does not publish server packages for $architecture. Use the Termux setup for Termux or install a supported local server manually."

    command -v curl >/dev/null 2>&1 || {
        run_privileged apt-get update
        run_privileged apt-get install -y curl ca-certificates
    }
    command -v sha256sum >/dev/null 2>&1 || fail 'sha256sum is required to verify the MySQL APT configuration package.'

    temporary_directory="$(mktemp -d)"
    package="$temporary_directory/mysql-apt-config_0.8.40-1_all.deb"
    curl --fail --location --silent --show-error \
        https://dev.mysql.com/get/mysql-apt-config_0.8.40-1_all.deb \
        --output "$package"
    printf '%s  %s\n' '41a796dd6ea681aef4675d3b0b27924873d4f9777e524facf7693eabdf232e93' "$package" | sha256sum --check --status \
        || fail 'The MySQL APT configuration package checksum did not match.'

    run_privileged env DEBIAN_FRONTEND=noninteractive MYSQL_SERVER_VERSION=mysql-8.4-lts \
        MYSQL_CONNECTORS=Disabled dpkg --install "$package"
    run_privileged apt-get update
    run_privileged apt-get install -y mysql-server
}

ensure_mysql_server() {
    local os_name os_id service_name
    os_name="$(uname -s)"
    case "$os_name" in
        Darwin)
            command -v brew >/dev/null 2>&1 \
                || fail 'Install Homebrew before using automatic MySQL setup on macOS.'
            if ! brew list --versions mysql@8.4 >/dev/null 2>&1; then
                brew install mysql@8.4
            fi
            brew services start mysql@8.4
            mysql_prefix="$(brew --prefix mysql@8.4)"
            export PATH="$mysql_prefix/bin:$PATH"
            ;;
        Linux)
            if [[ -r /etc/os-release ]]; then
                . /etc/os-release
                os_id="${ID:-unknown}"
            else
                os_id=unknown
            fi
            if command -v dnf >/dev/null 2>&1; then
                if ! command -v mysqld >/dev/null 2>&1; then
                    run_privileged dnf install -y mysql-server
                fi
                service_name=mysqld
                if command -v mysqld >/dev/null 2>&1 && mysqld --version 2>&1 | grep -qi MariaDB; then
                    fail 'The selected package is MariaDB. This setup requires MySQL on desktop Linux.'
                fi
                if install_loopback_config /etc/my.cnf.d/99-adaptive-reading-game.cnf; then
                    loopback_changed=true
                else
                    loopback_changed=false
                fi
                start_or_restart_linux_service "$service_name" "$loopback_changed"
            elif command -v apt-get >/dev/null 2>&1; then
                case "$os_id" in
                    debian|ubuntu)
                        if ! command -v mysqld >/dev/null 2>&1; then install_apt_mysql; fi
                        if mysqld --version 2>&1 | grep -qi MariaDB; then
                            fail 'The installed server is MariaDB. Desktop Linux setup uses MySQL; remove or replace MariaDB with MySQL, then rerun this script.'
                        fi
                        if install_loopback_config /etc/mysql/mysql.conf.d/99-adaptive-reading-game.cnf; then
                            loopback_changed=true
                        else
                            loopback_changed=false
                        fi
                        ;;
                    *) fail "Automatic MySQL installation is not configured for $os_id." ;;
                esac
                start_or_restart_linux_service mysql "$loopback_changed"
            else
                fail "No supported package manager was found for $os_id."
            fi
            ;;
        *)
            fail "Automatic local MySQL setup is not supported on $os_name."
            ;;
    esac
}

ensure_mysql_server
if [[ "$(uname -s)" == Linux ]] && command -v dnf >/dev/null 2>&1; then
    printf 'Info: On a first Fedora MySQL install, the one-time root password may be in /var/log/mysqld.log. Read it with: sudo grep "temporary password" /var/log/mysqld.log\n'
fi
if [[ "$reconfigure" == true ]]; then
    mvn -q -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlLocalDatabaseSetup \
        -Dexec.args=--reconfigure exec:java
else
    mvn -q -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlLocalDatabaseSetup exec:java
fi
