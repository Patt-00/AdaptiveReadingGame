# Game finalization verification

The default story pack now has a new playable edition with a fuller ending and three questions at each checkpoint difficulty. Earlier chapter IDs remain available to load old progress but are hidden from New Game. This report records the verification of the content, MySQL migration, JavaFX screens, model helper, and launch scripts.

## Content and compatibility

Story-pack version 3 adds a required `availableForNewGame` field. The version 1 and 2 loaders remain supported and treat chapters as selectable by default. The new `hailey-investigation-final-v2` edition is selectable; the original draft and `hailey-investigation-complete-v1` remain loadable for saved progress.

The final edition has four route-specific checkpoint records. Each has three questions at Easy, Medium, and Hard, for 36 stored question entries and six questions in a normal two-checkpoint playthrough. Java scores each question's first submitted answer. Narrative choices do not affect scores. The four endings each include an aftermath scene and a completion summary.

MySQL migration V006 adds `chapters.available_for_new_game`. The chapter picker filters archived chapters; chapter lookup and save restoration still load them. The group and teacher have not reviewed the new ending or answer keys. The supplied story text was kept unchanged at the user's direction, so this work does not claim an age-suitability review.

## Automated verification

The full verification ran on the feature branch with a disposable MySQL 8.4 database on loopback. Maven reported **231 tests, zero failures, zero errors, and four opt-in skips**. The run included 44 JavaFX gameplay tests, five opening-screen tests, six MySQL integration tests, content-pack validation, settings, and launcher checks. The four skips were the three opt-in JavaFX audio-device tests and the opt-in real-model test.

The full command was:

```sh
MAVEN_OPTS=-Xmx256m \
ARG_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:13329/adaptive_reading_test \
ARG_MYSQL_TEST_USER=root \
ARG_MYSQL_TEST_PASSWORD="${ARG_TEST_DB_PASSWORD:?set a disposable test password}" \
XDG_RUNTIME_DIR=/run/user/1000 \
PULSE_SERVER=unix:/run/user/1000/pulse/native \
xvfb-run -a -s '-screen 0 1920x1200x24' mvn -B verify \
  -Darg.uiTests=true \
  -Darg.mysqlTests=true \
  -DargLine='-Xmx512m --enable-native-access=ALL-UNNAMED'
```

The isolated content, pack-loader, and soundtrack-cue checks passed 13 tests. The MySQL integration suite passed six tests, including repeated migrations, account isolation, first-answer persistence, chapter availability, and restoring old and current chapter progress.

The Python helper contract tests passed eight tests:

```sh
model-tool/.venv/bin/python -m unittest discover -s model-tool -v
```

The Git LFS model file was verified at 988,097,824 bytes with SHA-256 `fdf756fa7fcbe7404d5c60e26bff1a0c8b8aa1f72ced49e7dd0210fe288fb7fe`.

The real-model smoke test passed after the local helper reported ready and loaded the bundled repository weights:

```sh
model-tool/.venv/bin/python model-tool/server.py
mvn -B -Darg.modelTests=true -Dtest=QwenModelSmokeTest test
```

The JavaFX audio-device test was run separately because starting its JavaFX toolkit in the same Maven process as the Robot UI suite prevents the other UI classes from initializing their toolkit. The first combined invocation failed with `Toolkit already initialized` and an opening-screen timeout. The full suite passed after running without the audio-device flag. In the separate audio run, two playback and repeated-click checks passed; the optional story-track check skipped because the local Investigation Piano and Tense Atmosphere files are absent.

## Fedora desktop

The Linux prerequisite check passed. The application opened with all three chapters loaded. The live Fedora window measured 1920 by 1080 and reported `_NET_WM_STATE_FULLSCREEN`.

The visible walkthrough covered signup, login, appearance selection, the main menu, New Game chapter selection, instructions, and the first story screen. New Game listed only `Aurelium Investigation`. The account used temporary in-memory storage. JavaFX Robot separately tested all four ending combinations, the three-question assessment and feedback flow, both investigation orders, and the supported layout sizes.

Screenshots from the visible desktop and Robot checks are available here:

- [Opening screen](../qa/game-finalization-2026-10-30/fedora-opening-ready.png)
- [Login screen](../qa/game-finalization-2026-10-30/fedora-login.png)
- [Main menu](../qa/game-finalization-2026-10-30/fedora-main-menu.png)
- [New Game chapter picker](../qa/game-finalization-2026-10-30/fedora-chapter-picker.png)
- [New story screen](../qa/game-finalization-2026-10-30/fedora-new-story.png)
- [Three-question screen](../qa/game-finalization-2026-10-30/robot/completed-story-question.png)
- [Answer feedback](../qa/game-finalization-2026-10-30/robot/completed-story-answer-feedback.png)
- [Three-question checkpoint result](../qa/game-finalization-2026-10-30/robot/completed-story-quiz-results.png)
- [Minimum-size ending](../qa/game-finalization-2026-10-30/robot/completed-ending-small-window-large-text.png)

The manual desktop walkthrough stopped at the first story screen. The rest of the route and ending interactions were covered by Robot tests rather than a full manual playthrough.

## Termux and platform limits

The Pad 7's Debian PRoot setup compiled the current source, verified the X11 display with `xdpyinfo`, and launched JavaFX until it logged that all three chapters loaded. Testing reproduced a stale X11 socket that existed without an active server. The launcher now checks for an X11 response, removes a stale socket only when no Termux:X11 process is running, and starts a fresh display. The subsequent launch reported that the game was ready in Termux:X11.

A visible Pad 7 playthrough could not be completed. At the device check, Android showed a pending Tailscale authorization page instead of the game, so no device-side authorization or interaction was performed. The temporary test checkout is at `~/AdaptiveReadingGame-finalize-test`; the separate older Termux checkout was left untouched.

Alpine 3.24.3 with OpenJDK 21 was tested on the Pad 7. Both normal JVM startup and `java -Xint` failed before JavaFX with `Failed to mark memory page as executable`. The temporary Alpine packages were removed. The tested Termux launcher remains on Debian PRoot; Alpine is not a supported launch target on this device.

The production MySQL server remains externally configured through `ARG_DB_*`; the application uses in-memory storage when those settings are absent. Windows and macOS launches were not tested. The optional local story tracks are absent, human listening was not performed, and the model has not been evaluated for reading outcomes. The group and teacher still need to review the new story conclusion and answer keys.
