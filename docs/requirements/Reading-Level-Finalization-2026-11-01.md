# Reading level display and finalization verification

> Historical record: this report covers the version-2 content pack before the version-3 story integration. Its one-question assertions and test totals are not the current repository status. See [integration verification](Final-Integration-Verification-2026-11-02.md).

A reading level now describes the comprehension questions only. Story screens no longer present a level as a property of the narrative, the repository builds and runs clean, and the full suite passes. This report records the verification of that state.

## Automated verification

The unit suite passed with 232 tests, zero failures, and 63 opt-in skips:

```sh
mvn -B test
```

The JavaFX UI suite ran under Xvfb and passed the same 232 tests with zero failures and 11 opt-in skips. `Dev1UiTest` contributed 49 tests and `OpeningScreenUiTest` 4:

```sh
XDG_RUNTIME_DIR=/run/user/$(id -u) \
xvfb-run -a -s '-screen 0 1920x1200x24' mvn -B \
  -Darg.uiTests=true \
  -Darg.ui.captureDir=docs/qa/finalization-2026-11-01 \
  -DargLine='-Xmx512m --enable-native-access=ALL-UNNAMED' \
  test
```

Three tests cover the reading level display directly:

- `storyNarrationShowsNoQuestionLevelChip` asserts the gameplay header chip is neither visible nor managed during story narration, and carries no stale default text.
- `readingCheckHeaderNamesTheQuestionLevel` asserts the chip reads `Question level: Easy` while a check is on screen, and that the progress line reads `Question 1 of 1` without repeating the level.
- `levelCopyIsAboutQuestionsWhereverThePlayerMeetsIt` asserts the New Game picker, the How to Play screen, and the save slot line all use question wording.

## Repository state recovered

The working tree held unresolved `git stash pop` conflict markers in `README.md` and `OpeningScreenUiTest.java`, which made `mvn test-compile` fail, so no test could run. Both are resolved. The stashed `configuredFullscreenOpeningUsesTheWholeDisplay` test was dropped because it called a five argument `OpeningScreen` constructor that no longer exists; full screen now comes from the `ARG_FULL_SCREEN` environment variable at `OpeningScreen.java:131`. The README link to `docs/requirements/Game-Finalization-QA-2026-10-30.md` was removed because that file does not exist.

`OpeningScreenUiTest.completedTaskCannotBeEnteredUntilTheLoadingAnimationFinishes` failed on this host before any of the changes above. It was reproduced in a detached worktree at commit `c68f606`, so the defect was already committed and did not come from this pass. The test awaited the transient message `Finishing the loading sequence...`, which the app shows only when the startup task finishes while the progress bar sweep is still running. Instrumentation showed the 1200 ms sweep completing first, so the message never appeared and the assertion timed out. The test is now `startupCannotBeEnteredBeforeTheLoadingTaskCompletes` and asserts the guarantee that does not depend on that race: while the task is pending, the loading screen is showing, Enter is hidden, the bar is below 1.0, and neither Space nor Enter reaches the account screen.

Startup progress now reaches the scene graph through `Platform.runLater` at `OpeningScreen.java:356`. `Task.updateProgress` reports from the worker thread, and the progress listener stopped, created, and played animations on that thread.

## Dead code removed

`dashboard.fxml`, `com/adaptivereadinggame/controller/MainController.java`, and the package-less `src/main/java/MainController.java` were deleted. The only references were the FXML controller attribute inside `dashboard.fxml` itself and the README file tree. No loader or service reached them.

## Visual review

The UI suite wrote 80 screenshots to `docs/qa/finalization-2026-11-01/`. The review covered the default 1280 by 720 window and the 760 by 520 minimum window.

- `game-story-dialogue.png` confirms the story header shows only the chapter title.
- `completed-story-question.png` confirms the reading check header shows `Question level: Easy` under the title, with `Question 1 of 1` below the passage and no repeated level.
- `game-how-to-play.png` confirms `Reading checks start at Easy` under the chapter name, and the note that the questions change level while the story stays the same for every player.

## Player-facing wording

`LevelCopy` owns the shared phrasing so the screens cannot drift apart: `Question level: X` for a level in view, `Reading checks start at X` for a level not yet attempted, and `Next question level: X` for the recommendation after a check.

- Save slots and reading history show the wording above instead of a raw `Difficulty` enum name.
- Save slot phases read `Story not available` and `Chapter not available` instead of `Content unavailable` and `Unavailable chapter`.
- Startup progress reads `Getting your saves ready...` and `Opening the story...`. Storage mode, font fallback, and story pack diagnostics stay on the console.
- A missing or unreadable story pack says `Please close the game and open it again.` rather than naming setup variables or asking for a teacher.
- Alert titles moved from internal nouns such as `Game action unavailable` and `Screen unavailable` to `That did not work` and `Could not open the screen`.

## Limits

- The six MySQL integration tests, three audio device tests, and the real model smoke test were skipped because no disposable database, audio device, or model helper was configured for this run.
- Windows and macOS launches were not tested.
- Screenshots come from JavaFX Robot tests under Xvfb, not a manual desktop walkthrough.
- Full screen start-up is driven by `ARG_FULL_SCREEN` and has no automated coverage, because the flag is read from the process environment at start-up.
- Story review status, model evaluation limits, and the deferred teacher and guardian reporting are recorded in `README.md`, which owns those facts.
