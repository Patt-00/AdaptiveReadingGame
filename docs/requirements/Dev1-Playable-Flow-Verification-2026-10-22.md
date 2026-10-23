# Playable flow verification

This record describes the local, uncommitted `Dev1` working tree tested on 2026-10-22. Re-run the listed commands after source changes.

The live JavaFX application completes the prepared-story flow from chapter selection through results and next-chapter selection. The story and art remain external content; the tests use synthetic chapters only.

## Live flow

- `gameplay.fxml` shows dialogue, thoughts, character artwork when available, narrative choices, a separate reading passage, and comprehension questions.
- `how-to-play.fxml` explains story navigation, score rules, and save access before a learner starts a chapter.
- Questions are submitted in order. The application stores the first answer before showing correctness, explanation, or the correct choice.
- `analysis.fxml` runs result completion outside the JavaFX thread.
- `results.fxml` shows the score, next difficulty, and decision source. The next-chapter selector omits already completed chapters at that level and clearly reports an empty level.
- The Menu action opens the main menu, whose Continue action returns to the active story, reading check, or results. Save, Load, overwrite confirmation, and unsaved-progress confirmation remain available there.
- Settings adjust reading text size and character-art visibility during the current app session. Narration audio is not available without audio assets.
- Model recommendations are checked by Java. Results distinguish a model recommendation, a Java guardrail, a Java rule, and the Java fallback.

Approved content is loaded with `ARG_CONTENT_PACK`. Character images use `speakerAssetKey` under `assets/characters/` or the local directory configured by `ARG_ASSET_DIR`. No group-authored content is bundled.

## Executed verification

| Command or check | Result |
| --- | --- |
| `xvfb-run -a env ARG_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:13317/adaptive_reading_test ARG_MYSQL_TEST_USER='your-test-user' ARG_MYSQL_TEST_PASSWORD='your-test-password' mvn -B -Darg.uiTests=true -Darg.mysqlTests=true -Darg.modelTests=true -Darg.ui.captureDir=target/visual-qa-final verify` | PASS, 129 tests, 0 failures, 0 errors, 0 skipped. Maven built the application JAR. |
| `mvn -B -Dtest=StoryPackLoaderTest test` | PASS, 7 tests. The tests validate and import the application pack format, including the test-only startup fixture. |
| `model-tool/.venv/bin/python -m unittest -v test_server` (run from `model-tool/`) | PASS, 8 Python helper contract tests. |
| MySQL 8.4.11 disposable database | PASS. Migrations V001 through V003 ran twice. Integration tests covered result-source persistence, story transitions, first-answer ordering, saves, resume, idempotent completion, and idempotent story-pack import with explanation/speaker-asset read-back. |
| Separate Maven JVM runs of `MySqlProcessPersistenceProbe` with `seed`, then `resume` | PASS. The first process saved question one and exited. The second process restored the saved answer, completed question two, and persisted the result. |
| Local Qwen model smoke test | PASS. With Hugging Face offline mode enabled, the helper loaded the checked-in repository snapshot and returned a Java-validated recommendation. |
| Opt-in JavaFX flow tests | PASS, 16 tests. Fifteen use in-memory repositories. One uses MySQL, imports a synthetic chapter, saves after the first answer, recreates `BackendContext.mysql`, resumes at question two, and stores results. |
| `xvfb-run -a env ARG_DB_URL=jdbc:mysql://127.0.0.1:13317/adaptive_reading_test ARG_DB_USER='your-test-user' ARG_DB_PASSWORD='your-test-password' ARG_CONTENT_PACK=src/test/resources/com/adaptivereadinggame/content/startup-content-smoke.json timeout --foreground --signal=INT --kill-after=2s 18s mvn -B javafx:run` | The real application entry point connected to MySQL, logged `Loaded 1 prepared story chapter(s).`, and remained active until the deliberate timeout. Exit code 124 is expected because the timeout stops the GUI. |
| Xvfb and xdotool restart flow | PASS. A new JavaFX process logged into the seeded test account, continued at question two, showed correct-answer feedback, and rendered a 100% result. |
| Rendered screen review | PASS. Twenty-five PNGs were generated in ignored `target/visual-qa-final/`. The instructions, story, question, feedback, normal-result, fallback-result, MySQL-resume, and process-restart screens were inspected. |

The opt-in MySQL UI test recreated `BackendContext` in the same JVM. `MySqlProcessPersistenceProbe` verified saved assessment data across separate Maven JVMs. The Xvfb restart flow then used a fresh JavaFX process to log in, continue, answer, and view results. These checks used the test-only pack and a disposable local database, not the group's authored content or database. Both test services were stopped afterward.

## Remaining limits

- The group's story, comprehension questions, and character art have not been loaded or tested. Add them through [the content-pack guide](../../assets/README.md) when approved.
- The cross-process JavaFX flow has not been tested with the group's authored content or MySQL setup.
- Educator diagnostic reports and parent/guardian progress views still need agreed access roles, report fields, and skill categories.
- The optional Qwen model has passed a request-contract smoke test, but its educational value has not been evaluated.
- Presentation readiness and deployment to the group's MySQL environment are not established.
- All code and documentation changes remain local on `Dev1`. They are not committed or pushed.
