# MySQL setup and verification

The Dev1 app can store accounts, prepared chapters, story nodes, first answers, results with the difficulty decision source, and three full-save slots in MySQL. Generic story transitions and unfinished-assessment restoration are implemented; approved story content is loaded from a local pack. This guide covers the repository at `src/main/java/com/adaptivereadinggame/repository/mysql/` and the schema at `database/migrations/`.

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

The integration test creates and removes temporary triggers to force rollback cases. Give `TRIGGER` permission only to the disposable test user, not to a shared application user.

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

Run the migration once before the first database-backed launch:

```sh
mvn -Dexec.cleanupDaemonThreads=false -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlSchemaMigrator exec:java
mvn javafx:run
```

The migration creates `schema_migrations` and applies the numbered migration files, including `V001__initial_schema.sql`, `V002__content_feedback_and_assets.sql`, and `V003__difficulty_decision_source.sql`. Versions recorded as applied are skipped on later runs. Future schema changes need a new numbered file. Back up a database before running any later migration that changes existing records. If a migration fails partway through, inspect the schema before retrying because MySQL DDL may already have taken effect.

The application expects three save slots. The migration stores a full version-1 `GameState` JSON snapshot with its checkpoint in one row. A basic checkpoint overwrite clears an older full snapshot in that slot. To import approved content at startup, set `ARG_CONTENT_PACK` to its local JSON path. See [the content-pack guide](../assets/README.md).

## Run the MySQL integration test

Use a disposable local database named exactly `adaptive_reading_test`. The opt-in test rejects non-loopback hosts and any other database name. It adds uniquely named fixture rows and temporary triggers, then removes its triggers. Do not point it at a shared or production database.

Set these test variables in addition to the application variables:

- `ARG_MYSQL_TEST_URL`, for example `jdbc:mysql://127.0.0.1:13317/adaptive_reading_test`.
- `ARG_MYSQL_TEST_USER` and `ARG_MYSQL_TEST_PASSWORD`.

Run the focused test with:

```sh
mvn -B -Darg.mysqlTests=true -Dtest=MySqlGameRepositoryIntegrationTest test
```

The test applies the migrations twice, checks registration rollback, first-answer ordering and locking, account isolation, save rollback, result idempotency and decision-source persistence, story transitions, and unfinished-assessment restoration after constructing a new application context. JavaFX player-flow coverage uses in-memory synthetic content; the MySQL test verifies the backend contract rather than a production deployment.

## Stored data and remaining boundary

The schema contains accounts, credentials, students, chapters, questions, answer choices, story nodes, story choices, first-answer drafts, completed assessments with a decision source, and three save slots. Java stores only password verifiers in `credentials`; public `Account` records contain no verifier.

`MySqlGameRepository.saveStoryNodes` stores a validated, immutable scene graph with stable node and choice IDs. The application can import a validated pack at startup. Chapter and question UUIDs are derived from stable pack IDs; keep those source IDs stable once learner data refers to them. Do not publish randomized test IDs as permanent content. The database stores the question explanation, speaker asset key, and difficulty decision source with the relevant result or content record.

The current MySQL and player-flow results are recorded in [playable-flow verification](../docs/requirements/Dev1-Playable-Flow-Verification-2026-10-22.md). The earlier [MySQL adapter record](../docs/requirements/Dev1-MySQL-Verification-2026-10-22.md) documents the initial storage test scope.
