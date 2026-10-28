# MySQL setup and verification

The JavaFX app stores accounts, story episodes, route choices, adaptive checkpoints, first answers, results, and three full-save slots in MySQL. This guide covers `src/main/java/com/adaptivereadinggame/repository/mysql/` and the migrations in `database/migrations/`.

## Requirements

- MySQL Server 8.0 or newer.
- JDK 21 or newer and Maven.
- The Maven build downloads MySQL Connector/J 9.7.0. Its official release notes state support for MySQL Server 8.0 and later: [Connector/J 9.7.0 release notes](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html).

The schema stores timestamps as UTC in `DATETIME(6)` columns. The account username column uses a binary collation so MySQL compares the normalized value that Java supplies.

## Create the database

Use an administrator account to create the application database and a local application user. Replace the example username with your own choice. Choose the password locally and do not commit it.

```sql
CREATE DATABASE adaptive_reading CHARACTER SET utf8mb4;
CREATE USER 'adaptive_game'@'localhost' IDENTIFIED BY 'choose-a-local-secret';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON adaptive_reading.* TO 'adaptive_game'@'localhost';
```

The application does not create the database or MySQL user. The migration command creates the project tables inside the database.

The integration test creates and removes temporary triggers to force rollback cases. Give `TRIGGER` permission only to the disposable test user, not to a shared application user. On MySQL installations with binary logging enabled, the disposable test instance may also need `log_bin_trust_function_creators=1` so the test user can create its temporary triggers.

## Set the connection variables

Set the following variables in the terminal or IDE run configuration. Store the password in the local environment, not in a source file.

| Variable | Default | Usage |
| --- | --- | --- |
| `ARG_DB_URL` | Not set | MySQL JDBC URL. Example: `jdbc:mysql://localhost:3306/adaptive_reading`. |
| `ARG_DB_USER` | Not set | MySQL username used by the app and migration command. |
| `ARG_DB_PASSWORD` | Not set | Password for that MySQL user. |
| `ARG_MYSQL_TEST_URL` | Not set | Loopback-only test URL ending in `/adaptive_reading_test`. |
| `ARG_MYSQL_TEST_USER` | Not set | User for the disposable MySQL integration-test database. |
| `ARG_MYSQL_TEST_PASSWORD` | Not set | Password for the integration-test user. |

When all `ARG_DB_*` variables are absent, JavaFX starts in visibly labeled in-memory development mode. When any one is set, all three are required and the app selects MySQL. The app does not silently fall back to memory after a configured database connection fails.

For Linux or macOS, set the URL and user in the current shell, then provide the password through your shell or IDE environment settings:

```sh
export ARG_DB_URL='jdbc:mysql://localhost:3306/adaptive_reading'
export ARG_DB_USER='adaptive_game'
```

For Windows PowerShell, set the same values in the current terminal:

```powershell
$env:ARG_DB_URL = 'jdbc:mysql://localhost:3306/adaptive_reading'
$env:ARG_DB_USER = 'adaptive_game'
```

Set `ARG_DB_PASSWORD` before running either command below.

## Apply the schema and start the app

Run the migration before the first database-backed launch. If an existing database has V001 through V004, run it again to apply V005 before using character selection:

```sh
mvn compile -Dexec.cleanupDaemonThreads=false -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlSchemaMigrator exec:java
mvn javafx:run
```

The migration creates `schema_migrations` and applies V001 through V005. V004 adds checkpoint-specific question variants and outcomes, route IDs, scene asset keys, and checkpoint progress fields. V005 stores one cosmetic servant appearance per account. Versions recorded as applied are skipped on later runs. Future schema changes need a new numbered file. Back up a database before running any later migration that changes existing records. If a migration fails partway through, inspect the schema before retrying because MySQL DDL may already have taken effect.

V004 retains a default-empty `voice_asset_key` column for schema compatibility. The text-only application does not read or write it.

The application expects three save slots. Full saves store a version-2 `GameState` JSON snapshot with its current route, checkpoint, difficulty, prior checkpoint result ID, and first answers. The codec still reads version-1 saves. See the [version-2 save example](examples/game-state-v2.json) and the [version-1 save example](examples/game-state-v1.json). A basic checkpoint overwrite clears an older full snapshot in that slot. The app loads `assets/content/hailey-investigation-complete.json` by default. This pack includes the original draft chapter and a completed chapter with a distinct stable ID. Set `ARG_CONTENT_PACK` to select another local JSON file. See [the content-pack guide](../assets/README.md).

## Run the MySQL integration test

Use a disposable local database named exactly `adaptive_reading_test`. The opt-in test rejects non-loopback hosts and any other database name. It adds uniquely named fixture rows and temporary triggers, then removes its triggers. Do not point it at a shared or production database.

Set these test variables in addition to the application variables:

- `ARG_MYSQL_TEST_URL`, for example `jdbc:mysql://127.0.0.1:13317/adaptive_reading_test`.
- `ARG_MYSQL_TEST_USER` and `ARG_MYSQL_TEST_PASSWORD`.

Run the focused test with:

```sh
mvn -B -Darg.mysqlTests=true -Dtest=MySqlGameRepositoryIntegrationTest test
```

The test applies the migrations twice. It checks account isolation, first-answer locking, full-save rollback, result idempotency, version-1 content, adaptive question variants, route persistence, and checkpoint restoration after creating a new application context. JavaFX player-flow coverage uses in-memory content; this test verifies the MySQL adapter against a disposable database rather than a production deployment.

## Stored data and remaining boundary

The schema contains accounts, credentials, students, chapters, questions, answer choices, story nodes, route choices, checkpoint outcome definitions, first-answer drafts, checkpoint assessments with a decision source, and three save slots. Java stores only password verifiers in `credentials`; public `Account` records contain no verifier.

`MySqlGameRepository.saveStoryNodes` stores a validated, immutable scene graph with stable node and choice IDs. The application imports a validated version-1 or version-2 pack at startup. Chapter and question UUIDs are derived from stable pack IDs; keep those source IDs stable once learner data refers to them. Do not publish randomized test IDs as permanent content. The database stores checkpoint passages, question difficulty variants, answer explanations, asset keys, route IDs, and the validated source of each difficulty decision.

The current MySQL and player-flow results are recorded in [playable-flow verification](../docs/requirements/Dev1-Reading-Draft-Verification-2026-10-26.md). The earlier [MySQL adapter record](../docs/requirements/Dev1-MySQL-Verification-2026-10-22.md) documents the initial storage test scope.
