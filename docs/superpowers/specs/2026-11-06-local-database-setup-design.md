# Local database setup design

**Status:** Proposed for review

## Summary

Each installation will use its own persistent local database. Setup will install or detect a local server, create the application database and user, apply migrations V001 through V006, and save the connection settings for later launches. Separate devices will keep separate accounts and saves.

Windows, macOS, and supported Linux distributions will use MySQL Server. Termux on Debian ARM64 may use MariaDB only after the application's migrations and persistence tests pass against it. If those tests fail, Termux setup will report that persistent storage is unavailable and stop before launching the game.

## Current behavior

`MySqlConfig.fromEnvironment()` reads `ARG_DB_URL`, `ARG_DB_USER`, and `ARG_DB_PASSWORD`. If all three are absent, `AdaptiveReadingGameApp` selects `BackendContext.inMemoryWithModel()`, and accounts and saves disappear when the application closes. These behaviors are implemented in `src/main/java/com/adaptivereadinggame/repository/mysql/MySqlConfig.java`, `src/main/java/com/adaptivereadinggame/AdaptiveReadingGameApp.java`, and `src/main/java/com/adaptivereadinggame/backend/BackendContext.java`.

`MySqlSchemaMigrator` applies V001 through V006 inside a database connection. It creates the migration version table and project tables, while database and user creation remain external setup tasks. The current setup scripts check whether MySQL settings exist and otherwise report temporary in-memory storage. See `database/README.md` for the current database user grants and manual migration command.

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
| Windows | MySQL Server installed with the vendor MSI and Configurator | Configure and start the Windows service. The vendor installer may request administrator approval and a server administrator password. |
| macOS | MySQL 8.4 LTS through the supported MySQL package or Homebrew formula | Start the local MySQL service for the user. |
| Linux | MySQL Server from the vendor APT or Yum repository for supported distributions | Start the system service when the distribution supports it. Unsupported distributions stop with a manual-install message. |
| Termux | MariaDB in Debian PRoot only after compatibility tests pass | PRoot-Distro does not run systemd. The Termux launcher must start the database process directly and keep its data directory in the Debian container. |

The Termux exception is based on package availability checked on 2026-11-06. Oracle's Debian Trixie APT repository lists MySQL packages for amd64, without an ARM64 package directory. Debian's Trixie default MySQL server package resolves to MariaDB. PRoot-Distro documents that systemd and other service supervisors do not generally run there. [MySQL APT repository index](https://repo.mysql.com/apt/debian/dists/trixie/mysql-8.4-lts/), [Debian database packages](https://packages.debian.org/trixie/database/), [PRoot-Distro limitations](https://github.com/termux/proot-distro).

For Termux, the implementation must run the migration and repository integration suite against a disposable MariaDB instance. The setup must not select MariaDB until that test passes. If compatibility fails, the setup must stop and explain the limitation.

The native MySQL installation paths use the vendor Windows MSI and Configurator, Homebrew or the MySQL package for macOS, and supported MySQL packages for Linux. The official Windows guide describes the MSI and service configuration; Homebrew documents its MySQL formula and service command; MySQL documents APT and Yum repository installation. [Windows installation](https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html), [Homebrew MySQL formula](https://formulae.brew.sh/formula/mysql), [MySQL APT repository](https://dev.mysql.com/doc/refman/8.4/en/linux-installation-apt-repo.html), [MySQL Yum repository](https://dev.mysql.com/doc/refman/8.4/en/linux-installation-yum-repo.html).

## First setup

The setup script first checks for a local server. If it is missing, the script installs a supported server and starts it. Installing server packages can require administrator approval. The server must listen on loopback so this local setup does not expose MySQL to a network.

The setup wizard asks for a JDBC URL, an application database username, and a password entered without echo. The suggested URL is `jdbc:mysql://127.0.0.1:3306/adaptive_reading`; the user may change the port or database name while keeping the server local. The wizard asks for a MySQL administrator password only when the platform's local administrator method requires one to create the database and application user. It uses that credential for provisioning and does not save it.

Setup creates the database named in the URL and a dedicated application user with the privileges needed by the existing migration and application code. It then tests the application credentials and runs `MySqlSchemaMigrator` to apply V001 through V006. The application user remains separate from the server's root or administrator account.

If a local configuration already exists, setup tests it and applies any unapplied migrations. A `--reconfigure` option lets the user replace the URL or application credentials. A connection or migration failure prints the failing step and stops setup before the game starts.

## Credential storage and launch

Store the URL, application username, and application password in a per-user configuration file outside the repository. Use the Windows application data directory on Windows and the user's XDG configuration directory on Linux, macOS, and Termux. On POSIX systems, set file permissions to `0600`; on Windows, restrict the file ACL to the current user. The password is plain text at rest in this file. Setup and launch scripts must not echo it, include it in command-line arguments, or write it to logs.

`MySqlConfig.fromEnvironment()` will keep explicit environment settings as the highest-priority override. If any `ARG_DB_*` variable is set, all three variables must be set and the environment values will be used. If none are set, the application will load the saved local configuration. Termux setup will save the configuration in the Termux user's config directory and make it available inside Debian PRoot. This keeps the same local settings available to the Java launcher inside the container.

The setup scripts prompt only when no valid configuration exists or when `--reconfigure` is used. The launch scripts load the saved settings, verify the database connection, and start the application in MySQL mode. A direct launch without configuration continues to show the existing in-memory storage notice.

## Failure handling

The game must not quietly switch to temporary storage after a requested persistent setup fails. For an unavailable server, invalid credentials, failed migration, unsupported Linux package, or failed Termux compatibility test, setup stops before the game launch and gives the user a recovery step.

The user may still launch the application directly without a local database configuration for a temporary session. The startup screen and log continue to label that mode as temporary.

## Verification and acceptance

Automated tests will verify that environment variables override the saved configuration, the password is not written to logs, malformed or incomplete credentials produce actionable errors, and migrations are safe to rerun. Database integration tests will run against disposable MySQL and, for Termux support, MariaDB databases.

Each supported setup path must be checked on a fresh environment. The test registers a game account, closes the application, launches it again, and confirms that the same account can log in and read its saved progress. The test also verifies the local service binds to loopback and that the saved credential file is excluded from Git.
