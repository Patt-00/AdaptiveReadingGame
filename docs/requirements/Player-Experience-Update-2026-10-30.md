# Player experience update verification

The `main` checkout stores reading and audio settings in device-local Java preferences, names the protagonist from the account display name, shows clearer save and history details, marks chapter replays, includes a Credits screen, and starts in full screen by default. Set `ARG_FULL_SCREEN=false` to use a window. This verification covered the worktree based on commit `ed5c3b4`.

## Changes

`PlayerPreferences` stores text size, character artwork visibility, reduced motion, music volume, and effect volume outside account data and game saves. The opening screen and signed-in screens share the same preference values. Settings dialogs apply changes only when the player selects Apply; Reset to defaults remains pending until that action.

Story dialogue and the Story Log show the signed-in display name for player speaker labels and the current direct references to the protagonist. General references to servants as a group or occupation remain in the authored story. The story pack and stable chapter IDs stay unchanged.

Save slots display the chapter name, progress phase, difficulty, route when available, and save time. The player can save a reading result or completed story and restore it through Continue. Reading History includes chapter and checkpoint names. New Game marks assessed chapters as replays and explains that recorded answers and scores remain.

Credits display repository-recorded attribution for the menu music, optional story music, Qwen model, and provisional player artwork. The Bash and PowerShell preflight scripts check both the Java version on `PATH` and the JDK used by Maven. They report database configuration without testing connectivity, and warn about missing optional files.

## Verification

The full command ran on the tree based on `ed5c3b4` and completed with **227 Java tests, zero failures, zero errors, and four skipped opt-in tests**. It included 44 JavaFX gameplay tests, four opening tests, six MySQL integration tests, five prerequisite-script tests, and three isolated preference tests. The JavaFX tests ran through Robot under Xvfb. The MySQL tests used a temporary MySQL 8.4 container on loopback and stopped the container after the run.

```sh
MAVEN_OPTS=-Xmx256m \
ARG_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:13329/adaptive_reading_test \
ARG_MYSQL_TEST_USER=root \
ARG_MYSQL_TEST_PASSWORD="$ARG_TEST_DB_PASSWORD" \
XDG_RUNTIME_DIR=/run/user/1000 \
PULSE_SERVER=unix:/run/user/1000/pulse/native \
xvfb-run -a -s '-screen 0 1920x1200x24' mvn -B verify \
  -Darg.mysqlTests=true \
  -Darg.uiTests=true \
  -DargLine='-Xmx512m --enable-native-access=ALL-UNNAMED'
```

The `PlayerPreferencesTest` cases verified saved values after constructing a new preference store, invalid values, and an unavailable store. JavaFX checks covered opening preferences, the registered name in dialogue and Story Log, Apply and Cancel, Credits at the 760 by 520 minimum window, result saving and restore, save/history descriptions, and both story routes through their visible controls.

After integrating the newer `origin/main` UI commits, the complete `Dev1UiTest` class passed all 44 tests. The fullscreen default, windowed opt-out, and `Stage` request checks passed as four targeted tests after the fullscreen change. The Fedora launch confirmed actual fullscreen geometry and state; Windows, macOS, and Termux fullscreen behavior was not tested during this update.

The Python helper contract suite passed eight tests:

```sh
python3 -m unittest discover -s model-tool -v
```

The Bash preflight passed from `/tmp`, including with `JAVA_TOOL_OPTIONS` set. The `run-game.sh` launcher passed preflight on Fedora and loaded both story chapters. `AdaptiveReadingGameAppStartupTest` and the focused fullscreen opening test passed. With `ARG_FULL_SCREEN` unset, the visible JavaFX stage measured 1920 by 1080 and reported `_NET_WM_STATE_FULLSCREEN`; the [desktop loading screenshot](../qa/player-experience-2026-10-30/desktop-loading-ready.png) records that launch. JavaFX Robot tests cover input under Xvfb; this desktop launch did not include a full manual playthrough or human listening check.

## Remaining limits

The four skipped tests were three JavaFX audio-device tests and the opt-in real-model smoke test. The local Qwen weight file is still a Git LFS pointer, and the Python helper was not running during verification. Java's fallback is tested; the current story has two checkpoints per route, while the model policy requires three consecutive sections at one level before it requests a suggestion.

The two optional Pixabay story tracks are absent from `assets/local-music/`. The game remains playable without them. The menu soundtrack and click sound are bundled, but no person listened to the tracks during this verification.

No PowerShell executable was available, so the PowerShell preflight and a Windows launch remain unverified. The shared MySQL database was not accessed. The production application uses temporary in-memory storage until the group configures its MySQL server and applies migrations V001 through V005.

The group and teacher still need to approve the player-character presentation, story conclusion, and answer keys. Each current checkpoint has one question, so a result is 0% or 100%. You asked to defer teacher and parent or guardian reports; their access rules and report contents remain undecided.
