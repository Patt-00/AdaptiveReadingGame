# Local database setup

The application stores accounts, story progress, answers, results, and three save slots in a database on the same device. Data does not sync between devices.

Windows, macOS, and supported desktop Linux use MySQL Server 8.0 or newer. Termux uses MariaDB inside its Debian PRoot environment. The game setup scripts provision the database and application user, apply migrations V001 through V006, and save the application connection settings for later launches.

## Set up a device

Use the platform setup script described in [`scripts/SCRIPT.md`](../scripts/SCRIPT.md). On first setup, the database wizard asks for a local JDBC URL, application username, and hidden application password. The suggested URL is `jdbc:mysql://127.0.0.1:3306/adaptive_reading`. Change the port or database name only if needed; setup accepts loopback hosts only. Desktop setup also binds MySQL and MySQL X Protocol to loopback, and Termux starts MariaDB on `127.0.0.1` for the game session.

When the local server requires a database administrator password, setup asks for it without showing the characters. The administrator password is used only to create the database and application user. Termux uses MariaDB's local socket for that step and does not ask for a root password.

The application user receives only the privileges needed by the schema and migrations on the game's database. It is separate from the MySQL or MariaDB administrator account.

## Saved settings

The URL, application username, and password are stored outside the repository:

- Windows: `%APPDATA%\AdaptiveReadingGame\database.properties`, with an ACL limited to the current user.
- Linux and macOS: `${XDG_CONFIG_HOME:-~/.config}/adaptive-reading-game/database.properties`, with file permissions set to `0600`.
- Termux: `/root/.config/adaptive-reading-game/database.properties` inside the Debian PRoot container.

The password is plain text in this local file. Setup does not print it or include it in a command-line argument. Set `ARG_DB_CONFIG_FILE` to choose a different file path. On Termux, the path must point inside the Debian PRoot environment. The file is not committed to Git.

Explicit environment settings take priority over the saved file. If any application setting is provided, all three are required:

| Variable | Purpose |
| --- | --- |
| `ARG_DB_URL` | MySQL JDBC URL, such as `jdbc:mysql://127.0.0.1:3306/adaptive_reading`. |
| `ARG_DB_USER` | Application database username. |
| `ARG_DB_PASSWORD` | Application database password. |

If no environment settings or saved configuration exist, a direct game launch uses the visibly labeled temporary in-memory mode. Accounts and progress in that mode disappear when the game closes. A setup-script launch configures persistent storage before the game opens.

## Manual migration and launch

When using an existing local database outside the setup wizard, provide the three `ARG_DB_*` variables and run:

```sh
mvn compile -Dexec.cleanupDaemonThreads=false \
  -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlSchemaMigrator exec:java
mvn javafx:run
```

The migrator applies unapplied SQL files from `database/migrations/` and records their versions in `schema_migrations`. Applied versions are skipped on later runs. Add a new numbered migration for each schema change; do not edit a migration that has already been applied to user data.

V004 adds checkpoint question variants and outcomes, route IDs, scene asset keys, and checkpoint progress. V005 stores the player's appearance. V006 adds `chapters.available_for_new_game` so retired chapters can remain available to restore existing saves without appearing in new-game selection.

MySQL Connector/J 9.7.0 supports MySQL Server 8.0 and later according to its [release notes](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html). The schema stores timestamps as UTC in `DATETIME(6)` columns. Account usernames use a binary collation after Java normalizes them.

## Integration tests

The MySQL repository integration test is opt-in and requires a disposable loopback database named `adaptive_reading_test`. It creates fixture data and temporary triggers to verify account isolation, first-answer locking, save rollback, result idempotency, route persistence, and migration repeatability. Never point this test at a database containing user data.

The local setup integration test is also opt-in. Set `ARG_LOCAL_DB_URL` to a loopback URL whose database name ends in `_test`, and provide `ARG_LOCAL_DB_APP_USER` and `ARG_LOCAL_DB_APP_PASSWORD`. For administrator-password provisioning, also set `ARG_LOCAL_DB_ADMIN_USER` and `ARG_LOCAL_DB_ADMIN_PASSWORD`. For a local MariaDB socket test, set `ARG_DB_ADMIN_MODE=socket` and `ARG_DB_ADMIN_CLIENT=mariadb`.

Run it with:

```sh
mvn -B -Darg.mysqlLocalSetupTests=true \
  -Dtest=MySqlLocalDatabaseProvisionerIntegrationTest test
```

The test requires administrator access to its disposable database because it creates the schema and user. Remove the test database and test user after running it.

## Stored data

The schema stores accounts and password verifiers, chapters, questions, answer choices, story nodes, route choices, checkpoint results, first answers, and three full-save slots. Public account records do not contain the password verifier. The game applies the first submitted answer to scoring; story choices only change the narrative route.

The application imports a validated content pack at startup. Keep chapter, question, node, and choice IDs stable after player data refers to them. See the [content pack guide](../assets/README.md) for the content format.
