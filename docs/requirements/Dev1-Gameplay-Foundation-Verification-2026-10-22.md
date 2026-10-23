# Story and content foundation verification

> Historical snapshot: This test record predates the current playable-flow integration. See [current playable-flow verification](Dev1-Playable-Flow-Verification-2026-10-22.md) for the present application status and latest combined test run.

The reusable story and content foundation passes its JavaFX, backend, and disposable-MySQL test suite. It is ready to receive approved chapter text, reading questions, and character asset keys. The player-facing gameplay screens are not connected yet.

## Implemented scope

- A repository-backed story service starts chapters, advances automatic nodes, follows valid choices, and keeps narrative choices out of comprehension scoring.
- A version-1 JSON loader validates stable chapter and question IDs, story graph destinations, a single reading-check node, question answer keys, feedback text, and optional speaker asset keys before import.
- In-memory and MySQL adapters store prepared story nodes and first-answer drafts. MySQL migration V002 adds question explanations and speaker asset keys.
- The account coordinator requires questions in order and keeps the first answer. An unfinished assessment restores the original session and the authoritative stored answers.
- `ARG_CONTENT_PACK` loads approved content at application startup. No story content or character art is included in this checkout.

## Executed verification

| Command or check | Result |
| --- | --- |
| `xvfb-run -a env ARG_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:13317/adaptive_reading_test ARG_MYSQL_TEST_USER='your-test-user' ARG_MYSQL_TEST_PASSWORD='your-test-password' mvn -B -Darg.uiTests=true -Darg.mysqlTests=true -Darg.ui.captureDir=target/visual-qa-gamecore verify` | PASS, 119 tests, 0 failures, 0 errors, 1 skipped; Maven built the application JAR. The skipped test is the opt-in real-model smoke test. |
| `xvfb-run -a mvn -B -Darg.uiTests=true -Darg.ui.captureDir=target/visual-qa-gamecore-final -Dtest=Dev1UiTest test` | PASS, 12 JavaFX account/menu tests after the no-content message update, 0 failures, 0 errors, 0 skipped. |
| `mvn -B -Dtest=StoryPackLoaderTest,RepositoryStoryServiceTest,SharedContractsTest,AssessmentServiceConcurrencyTest test` | PASS, 27 tests, 0 failures, 0 errors, 0 skipped. |
| MySQL 8.4 disposable database test | PASS. The test applied migrations twice, imported synthetic chapter and story records, and restored the same unfinished assessment session after creating a new application context. |

The MySQL server used for verification was a local disposable container and was removed after the test. No shared or production database was used. The model helper was not started for the combined run; its real-model smoke remains documented separately in [the AI verification record](Dev1-AI-Verification-2026-10-22.md).

## Remaining limits

- No approved story pack, reading-question document, or character images are checked in. See [the content-pack guide](../../assets/README.md) for the import format.
- The 40 UI/UX FXML screens remain visual references. Signup, login, and menu are the only player-facing screens connected to the game services.
- Settings and educator diagnostic reports are not implemented. The proposal does not define report roles or fields.
- This verification does not establish model quality, classroom effectiveness, deployment to a shared database, or complete game presentation readiness.
- The changes are local to the `Dev1` checkout. They are not committed or pushed.
