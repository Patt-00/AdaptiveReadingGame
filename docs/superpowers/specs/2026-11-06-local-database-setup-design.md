# Local database setup design

**Status:** Implemented; platform verification details are recorded below.

## Summary

Each installation uses its own persistent local database. Setup installs or starts a local server, creates the application database and user, applies migrations V001 through V006, and saves the connection settings for later launches. Separate devices keep separate accounts and saves.

Windows, macOS, and supported Linux distributions use MySQL Server. Termux on Debian ARM64 uses MariaDB after migration and repository compatibility tests pass.

## Current behavior

`MySqlConfig.fromEnvironment()` reads `ARG_DB_URL`, `ARG_DB_USER`, and `ARG_DB_PASSWORD` first, then loads the saved local settings. If neither source has settings, `AdaptiveReadingGameApp` selects `BackendContext.inMemoryWithModel()`, and accounts and saves disappear when the application closes. These behaviors are implemented in `src/main/java/com/adaptivereadinggame/repository/mysql/MySqlConfig.java`, `src/main/java/com/adaptivereadinggame/AdaptiveReadingGameApp.java`, and `src/main/java/com/adaptivereadinggame/backend/BackendContext.java`.

`MySqlSchemaMigrator` applies V001 through V006 inside a database connection. It creates the migration version table and project tables. The platform setup scripts install or start the local server, provision the schema and user, and store application settings outside Git. See `database/README.md` for setup details and the manual migration command.

## Goals and non-goals

The setup flow will:

- Keep accounts, scores, checkpoints, appearances, and saves on the device that created them.
- Prompt for a local JDBC URL, application database username, and hidden application database password on first setup.
- Install or start a supported local database server and create the application database and user.
- Apply unapplied migrations before starting the game.
- Reuse saved local settings on later launches and allow the user to reconfigure them.
- Keep credentials outside Git and avoid printing them in setup or application logs.
- Preserve the current in-memory mode for direct, unconfigured development launches.

This design does not share accounts between devices, install a remote database, migrate accounts from a previous in-memory session, or change authentication and scoring rules.

## Platform support

| Platform | Local server | Service handling |
| --- | --- | --- |
| Windows | MySQL Server installed with the vendor MSI and Configurator CLI | Set the service up with TCP disabled first, then enable MySQL TCP bound to loopback. A temporary inbound firewall block protects the default MySQL ports during installation. Windows Defender Firewall must be enabled. Administrator approval may be requested. |
| macOS | MySQL 8.4 LTS through the supported MySQL package or Homebrew formula | Start the local MySQL service for the user. |
| Linux | MySQL Server from the vendor APT or Yum repository for supported distributions | Start the system service when the distribution supports it. Unsupported distributions stop with a manual-install message. |
| Termux | MariaDB in Debian PRoot | PRoot-Distro does not run systemd. The Termux launcher starts MariaDB for the game session and keeps its data directory in the Debian container. |

The Termux exception is based on package availability checked on 2026-11-06. Oracle's Debian Trixie APT repository lists MySQL packages for amd64, without an ARM64 package directory. Debian's Trixie default MySQL server package resolves to MariaDB. PRoot-Distro documents that systemd and other service supervisors do not generally run there. [MySQL APT repository index](https://repo.mysql.com/apt/debian/dists/trixie/mysql-8.4-lts/), [Debian database packages](https://packages.debian.org/trixie/database/), [PRoot-Distro limitations](https://github.com/termux/proot-distro).

Termux compatibility passed on a Xiaomi Pad 7 running Debian 13 ARM64 and MariaDB 11.8.6. The disposable setup test created the local schema and user, applied all six migrations, checked the stored connection settings, and removed its test schema and user. The Termux launcher also started and stopped the server when no game configuration was present.

The native MySQL installation paths use the vendor Windows MSI and Configurator, Homebrew or the MySQL package for macOS, and supported MySQL packages for Linux. The official Windows guide describes the MSI and service configuration; Homebrew documents its MySQL formula and service command; MySQL documents APT and Yum repository installation. [Windows installation](https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html), [MySQL Configurator CLI](https://dev.mysql.com/doc/refman/8.4/en/mysql-configurator-cli.html), [Homebrew MySQL formula](https://formulae.brew.sh/formula/mysql), [MySQL APT repository](https://dev.mysql.com/doc/refman/8.4/en/linux-installation-apt-repo.html), [MySQL Yum repository](https://dev.mysql.com/doc/refman/8.4/en/linux-installation-yum-repo.html).

## First setup

The setup script first checks for a local server. If it is missing, the script installs a supported server and starts it. On Debian or Ubuntu, setup temporarily blocks package-triggered service startup, writes the loopback setting, then removes the guard and starts MySQL. MySQL's default `bind_address` is `*`, which listens on all network interfaces, so setup writes `127.0.0.1` for MySQL TCP and MySQL X Protocol before starting the desktop service. Termux starts MariaDB with `--bind-address=127.0.0.1`. [MySQL `bind_address` and `mysqlx_bind_address` documentation](https://dev.mysql.com/doc/refman/8.4/en/server-system-variables.html).

The setup wizard asks for a JDBC URL, an application database username, and a password entered without echo. The suggested URL is `jdbc:mysql://127.0.0.1:3306/adaptive_reading`; the user may change the port or database name while keeping the server local. Setup validates loopback hosts even when complete `ARG_DB_*` environment overrides or saved settings are used, and rejects username or password properties embedded in the URL. The wizard asks for a MySQL administrator password only when the platform's local administrator method requires one to create the database and application user. It uses that credential for provisioning and does not save it.

Setup creates the database named in the URL and a new dedicated application user with the privileges needed by the existing migration and application code. It never resets or reuses an existing account for the selected host, because that account may have broader privileges or be used by another application. The user must choose an unused application database username. Setup then tests the application credentials and runs `MySqlSchemaMigrator` to apply V001 through V006. The application user remains separate from the server's root or administrator account.

If a local configuration already exists, setup tests it and applies any unapplied migrations. A `--reconfigure` option lets the user replace the URL or application credentials. A connection or migration failure prints the failing step and stops setup before the game starts.

## Credential storage and launch

Store the URL, application username, and application password in a per-user configuration file outside the repository. Use `%APPDATA%\AdaptiveReadingGame\database.properties` on Windows, the user's XDG configuration directory on Linux and macOS, and `/root/.config/adaptive-reading-game/database.properties` inside Termux Debian. On POSIX systems, set file permissions to `0600`; on Windows, restrict the file ACL to the current account. The password is plain text at rest in this file. Setup and launch scripts must not echo it, include it in command-line arguments, or write it to logs.

`MySqlConfig.fromEnvironment()` keeps explicit environment settings as the highest-priority override. If any `ARG_DB_*` variable is set, all three variables must be set and the environment values are used. If none are set, the application loads the saved local configuration. Termux setup saves the configuration inside Debian PRoot for the Java launcher.

The setup scripts prompt only when no valid configuration exists or when `--reconfigure` is used. The setup wizard verifies the application connection and runs missing migrations before launch. A direct launch without configuration continues to show the existing in-memory storage notice.

## Failure handling

The game must not quietly switch to temporary storage after a requested persistent setup fails. For an unavailable server, invalid credentials, failed migration, unsupported Linux package, or failed Termux compatibility test, setup stops before the game launch and gives the user a recovery step.

The user may still launch the application directly without a local database configuration for a temporary session. The startup screen and log continue to label that mode as temporary.

## Verification and remaining platform checks

- MySQL 8.4.11: local database provisioning, special-character password handling, duplicate-account rejection without changing the existing password, and migration repeatability passed against a disposable container.
- MariaDB 11.8.9: the same provisioning and duplicate-account checks passed against a disposable container. The repository transaction and rollback suite passed against both containers after enabling the test-only binary log trigger setting.
- Xiaomi Pad 7, Debian 13 ARM64, MariaDB 11.8.6: setup and database tests passed on the device. The interactive wizard created the local schema and user, saved a mode-`0600` config, and a second run reloaded it and verified migrations. Duplicate account setup was rejected safely. Test schema, user, config, and temporary source copy were removed afterward. The Termux session wrapper started and stopped MariaDB.
- Debian 13 AMD64 container, MySQL APT package 8.4.13: setup installed the missing APT installer dependencies, verified the package-startup guard was present during `mysql-server` installation, and confirmed the classic and X listeners were on loopback. A real systemd service restart was not exercised in this container.
- Windows: an earlier Windows runtime check confirmed the saved database configuration ACL contains only the current account. The current scripts parse under PowerShell 7.6.9, and mock tests cover option-file updates and temporary firewall rules. A full Windows 5.1 setup, UAC flow, Configurator CLI initialization, and service restart were not run on this machine.
- macOS: the Homebrew formula specifies a loopback-only listener, but installation and service startup have not been run on a macOS device. [Homebrew MySQL 8.4 formula](https://github.com/Homebrew/homebrew-core/blob/main/Formula/m/mysql%408.4.rb).
- Maven `verify`: 268 tests, 0 failures, 0 errors, 67 skipped. Provisioning tests cover duplicate-account rejection without password changes; other tests reject JDBC URL credentials and report migration version/reason.

The saved credential file is excluded from Git. Setup never prints the application password. Complete Windows and macOS fresh-install checks before treating those installer paths as fully validated. The Linux service restart path also needs a fresh desktop install check.
