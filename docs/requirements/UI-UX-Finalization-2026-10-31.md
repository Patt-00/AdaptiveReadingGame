# UI/UX finalization verification

> Historical record: this report covers the UI checks performed on 2026-10-31. Later content and interface changes are covered by [integration verification](Final-Integration-Verification-2026-11-02.md).

The player-facing UI no longer shows storage, font, or startup diagnostics. The login, sign-up, main menu, gameplay, results, analysis, and dialog screens use player-facing wording, and unsaved-progress warnings appear only when progress would actually be lost. This report records the verification of that state.

## Automated verification

The unit suite passed with 233 tests, zero failures, and 61 opt-in skips:

```sh
mvn -B test
```

The JavaFX UI suite ran under Xvfb and passed 46 tests with zero failures and one opt-in skip:

```sh
XDG_RUNTIME_DIR=/run/user/$(id -u) \
xvfb-run -a -s '-screen 0 1920x1200x24' mvn -B \
  -Darg.uiTests=true \
  -Darg.ui.captureDir=docs/qa/ui-finalization-2026-10-31 \
  -DargLine='-Xmx512m --enable-native-access=ALL-UNNAMED' \
  -Dtest=Dev1UiTest test
```

The suite covers login, sign-up validation, appearance selection, the main menu at minimum and default sizes, instructions, story dialogue, story choices, reading questions, answer feedback, checkpoint results, the analysis screen, model-fallback results, save slots, reading history, settings, audio settings, credits, logout, quit, and the four ending combinations.

## Visual review

The UI suite wrote 78 screenshots to `docs/qa/ui-finalization-2026-10-31/`. The review covered the default 1280 by 720 window and the 760 by 520 minimum window. The screens and dialogs reviewed include:

- Welcome, login, sign-up, and validation states.
- Appearance selection at default and minimum sizes.
- Main menu with and without saved progress, including the compact layout.
- How to Play, story dialogue, story choices, reading questions, answer feedback, and results.
- The analysis transition and model-fallback results.
- Save/Load, Reading History, Credits, Audio Settings, Reading Settings, Story Log, logout, and quit dialogs.
- The four ending combinations and the large-text minimum-window layout.

Confirmation text wraps within the dialog, history rows fill the dialog width, and the reading-settings dialog fits its content at the minimum window size.

## Player-facing behavior

- Player screens do not show storage mode, font fallback, story-pack, database, or model diagnostics. Those details go to the console and the documentation.
- Logout with no unsaved work says the saved progress is kept.
- Logout and quit warn about lost progress only when unsaved progress exists.
- The analysis screen says that the game is checking reading results and choosing the next challenge.
- Results describe the next level in player language and keep the reading-helper transparency note.
- New Game, Save/Load, Credits, Instructions, and appearance errors use player-facing wording.

## Limits

- The six MySQL integration tests were skipped because no disposable MySQL database was configured for this run.
- The three JavaFX audio-device tests and the opt-in real-model smoke test were not run.
- Windows and macOS launches were not tested.
- The review-draft chapter still contains its authored draft-boundary text because the story content was left unchanged. The final chapter edition and its answer keys still need group and teacher review.
- In-memory storage remains temporary. Without MySQL settings, accounts and saves still disappear when the app closes; the mode is logged at startup instead of shown on player screens.
- Screenshots come from JavaFX Robot tests under Xvfb, not a manual desktop walkthrough.
