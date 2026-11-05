#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
reconfigure=false
check_only=false
temporary_directory=""
service_start_guard_owned=false
service_start_guard_path=/usr/sbin/policy-rc.d
service_start_guard_marker='# AdaptiveReadingGame temporary service-start guard'

cleanup_temporary_directory() {
    if [[ "$service_start_guard_owned" == true && -f "$service_start_guard_path" ]] \
            && grep -Fqx "$service_start_guard_marker" "$service_start_guard_path"; then
        run_privileged rm -f -- "$service_start_guard_path" >/dev/null 2>&1 || true
    fi
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

prevent_package_service_start() {
    if [[ -e "$service_start_guard_path" ]]; then
        if [[ -x "$service_start_guard_path" ]]; then
            local policy_status=0
            if "$service_start_guard_path" mysql start >/dev/null 2>&1; then
                policy_status=0
            else
                policy_status=$?
            fi
            if (( policy_status == 101 )); then return 0; fi
        fi
        fail "An existing $service_start_guard_path does not deny MySQL service startup. Setup will stop before package installation to avoid starting MySQL with a network-wide listener."
    fi

    if [[ -z "$temporary_directory" ]]; then temporary_directory="$(mktemp -d)"; fi
    local guard_file="$temporary_directory/policy-rc.d"
    printf '#!/bin/sh\n%s\nexit 101\n' "$service_start_guard_marker" >"$guard_file"
    run_privileged install -o root -g root -m 0755 "$guard_file" "$service_start_guard_path" \
        || fail "Could not prevent MySQL from starting during package installation."
    service_start_guard_owned=true
}

allow_configured_service_start() {
    if [[ "$service_start_guard_owned" == true ]]; then
        if [[ ! -f "$service_start_guard_path" ]] \
                || ! grep -Fqx "$service_start_guard_marker" "$service_start_guard_path"; then
            fail "The temporary $service_start_guard_path guard changed during setup; refusing to remove it."
        fi
        run_privileged rm -f -- "$service_start_guard_path" \
            || fail "Could not remove the temporary service-start guard."
        service_start_guard_owned=false
    fi
}

install_apt_mysql() {
    local architecture package
    architecture="$(dpkg --print-architecture)"
    [[ "$architecture" == amd64 ]] \
        || fail "The MySQL APT repository does not publish server packages for $architecture. Use the Termux setup for Termux or install a supported local server manually."

    run_privileged apt-get update
    if ! command -v curl >/dev/null 2>&1; then
        run_privileged apt-get install -y curl ca-certificates
    fi
    command -v sha256sum >/dev/null 2>&1 || fail 'sha256sum is required to verify the MySQL APT configuration package.'

    prerequisite_packages=()
    command -v lsb_release >/dev/null 2>&1 || prerequisite_packages+=(lsb-release)
    command -v wget >/dev/null 2>&1 || prerequisite_packages+=(wget)
    command -v gpg >/dev/null 2>&1 || prerequisite_packages+=(gnupg)
    if ((${#prerequisite_packages[@]} > 0)); then
        run_privileged apt-get install -y "${prerequisite_packages[@]}"
    fi

    temporary_directory="$(mktemp -d)"
    package="$temporary_directory/mysql-apt-config_0.8.40-1_all.deb"
    curl --fail --location --silent --show-error \
        https://dev.mysql.com/get/mysql-apt-config_0.8.40-1_all.deb \
        --output "$package"
    printf '%s  %s\n' '41a796dd6ea681aef4675d3b0b27924873d4f9777e524facf7693eabdf232e93' "$package" | sha256sum --check --status \
        || fail 'The MySQL APT configuration package checksum did not match.'

    run_privileged mkdir -p /etc/mysql/mysql.conf.d
    if install_loopback_config /etc/mysql/mysql.conf.d/99-adaptive-reading-game.cnf; then :; fi
    run_privileged env DEBIAN_FRONTEND=noninteractive MYSQL_SERVER_VERSION=mysql-8.4-lts \
        MYSQL_CONNECTORS=Disabled dpkg --install "$package"
    run_privileged apt-get update
    prevent_package_service_start
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
                        allow_configured_service_start
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
