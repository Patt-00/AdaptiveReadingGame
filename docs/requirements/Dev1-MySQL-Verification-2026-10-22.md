# MySQL adapter verification

> Historical snapshot: This test record predates the current playable-flow integration. See [current playable-flow verification](Dev1-Playable-Flow-Verification-2026-10-22.md) for the present application status and latest combined test run.

The MySQL repository adapters passed the combined JavaFX, backend, and database test run on 2026-10-22. Verification used the local uncommitted Dev1 working copy and a disposable MySQL 8.4 database.

## Commands and results

| Command | Result |
| --- | --- |
| `xvfb-run -a mvn -B -Darg.uiTests=true -Darg.mysqlTests=true -Darg.ui.captureDir=target/visual-qa-mysql verify` | PASS, 86 tests, 0 failures, 0 errors, 0 skipped; Maven built `target/adaptive-reading-game-0.1.0-SNAPSHOT.jar`. |
| `mvn -B -Dexec.cleanupDaemonThreads=false -Dexec.mainClass=com.adaptivereadinggame.repository.mysql.MySqlSchemaMigrator exec:java` | PASS, migration command reported that the schema is up to date and exited successfully. |
| MySQL-configured `mvn javafx:run` under Xvfb | PASS, the Adaptive Reading Game window opened and was then stopped by the test harness. |

The MySQL integration test ran against `jdbc:mysql://127.0.0.1:13317/adaptive_reading_test`. It applied the migration twice, checked registration rollback, duplicate usernames, account lookup through the application context, answer locking, result idempotency, save rollback, account isolation, and reads through new repository objects. The test created unique synthetic rows and removed its temporary rollback triggers.

The JavaFX tests saved 13 PNGs in `target/visual-qa-mysql/`. These cover account and menu states; they do not depict a playable chapter. Maven compiled the project for Java 21 while running on OpenJDK 25. The test run emitted JavaFX native-access and accessibility-bus warnings, and the expected Georgia font fallback notice.

## Limits

The test database was a disposable localhost container. No shared or production database was used. The test does not prove data migration from another schema, remote database security, or MySQL deployment on another computer.

The application still uses `UnavailableGameplay` until the story service and approved story content are connected. `AssessmentService.restoreAssessment` and `StoryService.restoreState` remain intentionally unimplemented, so a player cannot resume an unfinished reading check after restarting the app. The FXML game screens remain visual references. Educator analytics and the separate Qwen prototype are not integrated.

The verification changes remain local to the Dev1 checkout. No commit, push, or merge was performed.
