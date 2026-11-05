# Integration verification

This report records the current Adaptive Reading Game UI, storage notice, story compatibility, tests, and platform checks.

## Current behavior

Story screens hide the question-level chip. Reading-check screens show the active question level, and the question counter does not repeat it. Chapter selection and instructions use consistent question-level wording.

The account and main-menu screens explain whether progress is temporary or saved to the account. Without MySQL settings, the notice says progress will be lost when the game closes.

The default version-3 story pack keeps the review draft and earlier completed edition available for old saves, and offers the new edition for new play. The new edition keeps the supplied story text and adds fuller aftermath scenes plus three questions at each difficulty per checkpoint. V006 stores whether a chapter is available to start without blocking restoration of old saves.

## Automated verification

The full Maven verification passed **237 tests with zero failures, zero errors, and four opt-in skips**. It included 49 JavaFX gameplay tests, six opening-screen tests, and six MySQL integration tests. The skips were the three opt-in JavaFX audio-device tests and the real-model smoke test.

The full run used a disposable MySQL 8.4 database bound to loopback. Set `ARG_TEST_DB_PASSWORD` to a disposable local password before running:

```sh
MAVEN_OPTS=-Xmx256m \
ARG_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:13329/adaptive_reading_test \
ARG_MYSQL_TEST_USER=root \
ARG_MYSQL_TEST_PASSWORD="$ARG_TEST_DB_PASSWORD" \
XDG_RUNTIME_DIR=/run/user/1000 \
PULSE_SERVER=unix:/run/user/1000/pulse/native \
xvfb-run -a -s '-screen 0 1920x1200x24' mvn -B verify \
  -Darg.uiTests=true \
  -Darg.mysqlTests=true \
  -DargLine='-Xmx512m --enable-native-access=ALL-UNNAMED'
```

The Python helper suite passed eight tests. The real Qwen smoke test also passed with the bundled Git LFS model after the helper reported ready. The model file is 988,097,824 bytes and has SHA-256 `fdf756fa7fcbe7404d5c60e26bff1a0c8b8aa1f72ced49e7dd0210fe288fb7fe`.

The JavaFX audio-device suite was run separately because its JavaFX toolkit cannot share the full Maven process with the Robot UI classes. Two music and click checks passed; the optional story-track check skipped because the local tracks are absent.

## Fedora desktop

The actual application opened fullscreen at 1920 by 1080. The visible flow covered signup, login, appearance selection, main menu, New Game, instructions, and the first story screen. The startup used in-memory storage and showed the temporary-progress notice on the account screen.

![Welcome screen with the temporary-progress notice](../qa/finalization-2026-11-05/fedora-temporary-progress-notice.png)

Robot tests separately covered the three-question checkpoint, answer feedback, correct scoring, both story orders, all four ending combinations, and the supported window sizes. Screenshots are stored in [the November 5 QA captures](../qa/finalization-2026-11-05/).

## Termux and remaining review

On the Xiaomi Pad 7, Debian PRoot setup and source compilation passed. The launcher recovered from a stale X11 socket, confirmed a live display response, and logged that it loaded all three chapters. A visible Pad 7 playthrough remains unverified because the device screen was waiting on Tailscale authorization during the test.

Alpine 3.24.3 with OpenJDK 21 failed before JavaFX startup with `Failed to mark memory page as executable`, including with `java -Xint`. Temporary Alpine packages were removed; the supported Termux launcher remains on Debian PRoot.

The group and teacher still need to review the new ending and answer keys. The supplied story wording was kept unchanged, so this report does not claim an age-suitability review. The production MySQL server remains external configuration. Windows/macOS launch, human audio listening, and educational validation of the model remain unverified. Teacher and parent reporting remains deferred.
