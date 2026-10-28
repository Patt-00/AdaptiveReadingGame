# Finish player settings, save clarity, and launch readiness

Complete this goal in the existing AdaptiveReadingGame checkout:

`/home/patt/Desktop/OOP_REQUIREMENTS/PROJECT/AdaptiveReadingGame-Dev1`

Read `AGENTS.md`, inspect the actual branch and Git status, and read the current README and verification report before editing. The expected branch is `test-merge`, but verify it. Preserve the existing uncommitted opening, appearance, audio, story, and preflight work. This goal authorizes the improvements below and their focused tests. Do not commit, merge, push, install system software, or change a shared database.

## Current state and completion target

The game already has authentication, character appearance selection, the animated opening and loading screen, adaptive reading checks, four team-authored endings, account-scoped saves, MySQL adapters, and an optional Qwen helper. Verify current behavior instead of rebuilding these features. The default pack contains the unchanged original draft and a completed chapter under separate stable IDs. The current story has two reading checkpoints per investigation order and one question per checkpoint at each difficulty.

The target is a more comfortable, understandable game that remembers preferences, explains saved progress and replay, and starts reliably through the supplied launchers. Finish each numbered item and verify its acceptance conditions. Record a concrete blocker if a platform or external service is unavailable; do not report unrun checks as passing.

## 1. Remember reading and audio preferences

- Add one small Java preference component for local, device-user settings. Use `java.util.prefs.Preferences` and the existing application defaults. Keep these preferences separate from accounts, scores, MySQL, and save snapshots.
- Persist reading text scale, character-art visibility, reduced motion, music volume, and effect volume. Load them before the opening starts and share them with the navigator. A muted opening must not become loud again when authentication opens.
- Settings dialogs save only when Apply is selected. Cancel keeps the previous values. Add Reset to Defaults as a pending change that the player can apply or cancel. The opening's mute and reduced-motion controls take effect and persist when changed.
- Reject invalid stored values and fall back to existing defaults. Unavailable preference storage must leave the game usable. Tests must use an isolated preference node or fake store and preserve the player's real settings.

Acceptance: settings survive application restart; the opening and game use the same saved audio and motion values; Apply, Cancel, Reset, and mute work consistently.

## 2. Make saved progress, history, and replay understandable

- Keep the three manual save slots and existing ownership and overwrite safeguards. Show the chapter title, a readable progress phase such as Story, Reading Check, Results, or Completed, difficulty, and save time. Show a readable investigation name when available. Avoid raw internal IDs in the player interface.
- Review the menu condition that disables Save whenever `currentResult()` is present. Allow saving and restoring checkpoint-result and terminal-story states when supported by the service. Make any necessary service correction through the existing account-safe boundary. A saved completion must resume its ending summary through Continue after restarting a MySQL-backed context.
- Add chapter and checkpoint names to Reading History. Preserve the stored scores and first-answer rule. Give empty history and missing-content states clear text.
- Label previously assessed chapters as story replays in the chapter picker and explain that earlier comprehension results remain recorded. Replaying an ending must not erase first answers or create another scored completion. Distinguish the original review draft from the completed episode.
- Keep manual save behavior explicit. Do not introduce automatic overwrites or erase learner history.

Acceptance: a player can identify each save, save and restore a completed story, distinguish replay from new assessment, and understand which chapter a history row describes. Old draft saves still load.

## 3. Add readable credits and preserve the visual style

- Add a Credits action using the existing menu and dialog conventions. Include the attribution and license information already recorded in the asset and model guides, including the bundled menu music and the optional story tracks. Do not invent contributor names or rights statements.
- Keep the burgundy, gold, white, and serif design direction. Fit the added action and new labels into the minimum supported window with keyboard focus, wrapping, and scrolling as needed.
- Keep the existing explicit font fallback notice. Reuse existing controls and styles rather than adding a new UI framework or redesigning unrelated screens.

Acceptance: Credits is reachable with mouse and keyboard, is readable at the minimum size, and contains accurate attribution supported by repository sources.

## 4. Strengthen the preflight and launch scripts

- Inspect the Bash and PowerShell scripts as actual launch code. Check both the Java command on PATH and the Java runtime Maven uses; a valid PATH Java with an older `JAVA_HOME` must not pass incorrectly. Confirm that a JDK/compiler is available.
- Keep relative paths rooted in the project directory and handle project paths containing spaces. A normal successful `java -version`, including its stderr output, must not abort the PowerShell checker.
- Match MySQL environment validation to `MySqlConfig.fromEnvironment()`. Never print a database password. Clearly distinguish checks performed from database connectivity or migrations that were not verified. Keep schema migration a documented, explicit command.
- Preserve the missing-pack check, optional model, and optional music behavior. Report unresolved Git LFS assets as appropriate without making optional assets mandatory. Ensure a failed check stops the launcher and a successful check actually launches Maven once.
- Keep `mvn javafx:run` available as the documented direct launch route. Do not weaken the Java startup error screen or its ready gate.

Acceptance: Bash check and launcher succeed in the supported local environment, reject broken prerequisites clearly, and work from outside the project directory. Exercise PowerShell on an available PowerShell environment; separately state whether a Windows launch was actually verified.

## 5. Verify and document the result

- Add focused behavior tests for preference restart and invalid data, Apply and Cancel, result-state saves, completed Continue, replay score preservation, and the launcher failure cases. Use existing project conventions.
- Exercise the new settings, save/history, credits, and result layouts at 1280 by 720, 1366 by 768, 800 by 600, and 760 by 520. Check relevant reading text scales of 0.8, 1.0, and 1.6, including actual keyboard and mouse reachability through JavaFX Robot.
- Verify MySQL save/restart behavior with a disposable local test database only. Keep in-memory checks separate. Preserve existing first-answer locking, account isolation, and draft compatibility tests.
- Run focused checks first. After final code changes, run the relevant regression suite and one full Maven verification with supported opt-in services. Record failures and skipped checks honestly. Use controlled three-section fixtures for the optional model trigger; the current story only visits two reading checkpoints per route.
- Launch the real application entry point and inspect the affected screens. Distinguish automated tests, an Xvfb launch, a physical desktop playthrough, and human listening. Capture representative screenshots and inspect them before declaring the UI finished.
- Update the existing setup, content, handoff, and verification guides with current behavior, commands, results, platform limitations, and any new preference-storage details. Correct stale claims rather than adding conflicting summaries.

Finish with a report that states what changed, exact check commands and results, which screens were observed, remaining limitations, and the current branch and uncommitted status. Stop application and disposable test processes that you started when verification is complete, unless the user asks to leave the game open.

## Boundaries and later content work

Java owns application logic, first-answer scoring, persistence coordination, and the final difficulty decision. The Python model remains an optional helper. Story choices never add to comprehension scores. Preserve the original storyboard, original draft, current completed chapter IDs, all existing save compatibility, group artwork, and the separate 40-screen FXML references.

Do not rewrite the story, expand the question bank, add a timer, add voice acting, replace or retrain the model, introduce teacher/parent reporting, or create a new database engine in this goal. Those require a separate content or product task. The remaining story work is to expand the final narration into dialogue scenes, make the portfolio search feel supported by earlier observations, and review age suitability. A later educational task can add more evidence-backed questions; the current one-question checkpoint produces only 0% or 100% and cannot establish the model's educational quality.

Do not claim teacher approval, human listening, Windows/macOS verification, or 100% project completion without the corresponding evidence.
