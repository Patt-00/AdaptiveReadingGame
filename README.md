# Adaptive Reading Game

A school project for a story-driven reading game for elementary students. Players read stories, make story decisions, and answer comprehension questions. The application saves progress per account and adjusts reading difficulty after a completed checkpoint.

This version connects account services, adaptive story checkpoints, MySQL repositories, the optional difficulty helper, and live JavaFX screens. It includes the supplied investigations and a team-authored continuation with four outcomes. The player is an unnamed servant, and Prince Owen is used consistently. The continuation identifies Lord Robert based on the supplied clues; this creative decision and its answer keys still need group and teacher review. The student game uses written dialogue and images. See [story content and art](assets/README.md) and the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md).

## Requirements and running the app

- JDK 21 or newer; the project compiles for Java 21.
- Maven available on the terminal PATH.
- JavaFX 21.0.13 dependencies are managed by Maven.
- Git LFS is needed to download the bundled music, large artwork, and optional model weights when cloning the repository.

For one-command setup and launch, use the setup script for your operating system. It installs JDK 21 or newer and Maven through the available package manager when either is missing, then checks the project and starts the game. Package installation may ask for administrator permission.

Linux or macOS, from the project root:

```sh
./scripts/setup-and-run.sh
```

Windows PowerShell, from the project root:

```powershell
.\scripts\Setup-And-Run.ps1
```

To install missing tools and run the check without launching the game, add `--install-only` on Linux or macOS, or `-InstallOnly` in PowerShell. Automatic installation requires Homebrew on macOS and WinGet on Windows. On Linux the script supports DNF and APT.

The setup script does not install or create a MySQL server, database, or user. Without MySQL variables the game runs with temporary in-memory storage. To use persistent storage, configure MySQL and apply the migrations in [database setup](database/README.md); the preflight script checks that the three connection variables are all set.

You can also run the preflight check by itself to confirm Java 21 or newer, Maven, the selected story-pack file, and complete MySQL settings without changing the database. If the check passes, use the launch script:

Linux or macOS, from the project root:

```sh
./scripts/check-prerequisites.sh --check-only
./scripts/run-game.sh
```

Windows PowerShell, from the project root:

```powershell
.\scripts\Check-Prerequisites.ps1 -CheckOnly
.\scripts\Run-Game.ps1
```

You can still start the app directly with `mvn javafx:run`. The Java startup screen then checks and loads the story pack and provides Retry when startup fails. The preflight scripts check that the selected pack file exists; the Java loader validates its contents.

To compile manually before launch, run these commands from the project root:

```sh
mvn -B -DskipTests compile
mvn javafx:run
```

### Run the game from Termux:X11

The game runs inside a Debian PRoot environment on Termux and opens its JavaFX window through Termux:X11. The Android Java runtime cannot load the desktop JavaFX native libraries. Debian supplies a Linux ARM64 runtime, and Maven resolves JavaFX artifacts for that platform. See the [OpenJFX Maven setup](https://openjfx.io/openjfx-docs/), [Termux:X11 instructions](https://github.com/termux/termux-x11), and [proot-distro options](https://github.com/termux/proot-distro).

Install the Termux:X11 Android app from its [official release page](https://github.com/termux/termux-x11/releases). The setup script installs the Termux companion package, Debian PRoot, JDK 21 or newer, Maven, Git LFS, PulseAudio, and the Linux libraries used by JavaFX graphics and media. It downloads the artwork and menu music, then compiles the Java sources. The launcher maps ALSA's default device to the Termux PulseAudio sink over loopback using the [Termux PulseAudio PRoot method](https://github.com/termux/termux-packages/issues/12289). It does not download the optional Qwen model or install MySQL.

Run setup once from the project root in Termux:

```sh
./scripts/termux-setup-x11.sh
```

The setup script uses the current checkout. If you run it outside a checkout, it clones `main` into `$HOME/AdaptiveReadingGame-Termux`. Pass `--project-dir PATH` to select another project directory. A temporary Android package-manager query failure is treated as unknown; the launcher tries to open Termux:X11 and reports a missing app only when Android explicitly cannot resolve its activity.

Launch the game from the same project root:

```sh
./scripts/termux-run-x11.sh
```

The launcher opens the Termux:X11 app, uses an existing `:0` or `:1` display socket, and starts the JavaFX stage in full-screen mode. When it starts a new display, it opens the Android app before waiting up to 30 seconds for the display socket. X11 startup diagnostics are written to `$HOME/.local/state/adaptive-reading-game/x11.log`. The game runs in a detached PRoot session so it remains open after the launch command returns. The launcher writes startup output to `target/termux-game.log`. It uses Maven to compile changed Java sources, caches the runtime classpath under `target`, and starts Java directly with the JavaFX version configured in `pom.xml`. Setup prepares this cache so later launches do not need the JavaFX Maven launcher. If neither display socket exists, it starts display `:0`. Set `ARG_TERMUX_X11_DISPLAY=:1` to choose another display. JavaFX uses software rendering by default; set `ARG_TERMUX_PRISM_ORDER=es2` to request its accelerated pipeline. Set `ARG_TERMUX_MEDIA_DEBUG=true` to show detailed JavaFX media logs.

The game uses temporary in-memory storage when MySQL settings are absent. The Java difficulty fallback works when the optional Python model helper is not running.

The Termux launcher does not change Android's media-volume setting. If PulseAudio reports an active stream but the game sounds silent, raise the device's media volume and check its selected audio output.

### Compile-check source in native Termux

Run `./scripts/termux-build-check.sh` from a Termux checkout when you only need a Java source compile check. A standalone copy clones `main` into `$HOME/AdaptiveReadingGame-Termux` when it cannot find a project. The script installs missing Git, JDK 21, or Maven packages and compiles the Java sources. It uses desktop Linux JavaFX classes for compilation on ARM64; it cannot launch the game window. Pass `--install-only` to check or install tools without cloning or compiling. Pass `--project-dir PATH` to use an existing checkout or select a clone directory.

The launch class is `com.adaptivereadinggame.AdaptiveReadingGameApp`. It opens an animated text introduction and a loading screen. Once startup finishes, press Space or select Enter to open authentication. A new account selects a servant appearance before reaching the main menu.

With no database settings, the app uses visibly labeled in-memory development storage. To use MySQL, set `ARG_DB_URL`, `ARG_DB_USER`, and `ARG_DB_PASSWORD`, then apply the schema described in [database setup](database/README.md).

The app loads `assets/content/hailey-investigation-complete.json` when `ARG_CONTENT_PACK` is not set. That pack contains the unchanged draft chapter and a completed chapter under a new ID. Set `ARG_CONTENT_PACK` to a local version-1 or version-2 JSON pack to use other content. The pack preserves the original review draft for existing saves and includes a team-authored completed version for new play. See [the content-pack guide](assets/README.md).

Character, scene, and CG keys resolve under `assets/characters/`, `assets/scenes/`, and `assets/cg/`. Set `ARG_ASSET_DIR` only when the same folder layout is stored under another trusted asset root.

The optional Qwen helper runs separately from Java. Follow the [model-tool guide](model-tool/README.md) to start it; Java keeps the difficulty decision and uses its fallback when the helper is unavailable.

For commands without Maven, see [Manual Compilation](Module%20Guide/Manual%20Compilation.md).

## Current status

- The text introduction includes a headphone recommendation and can be skipped. The loading screen rotates available artwork and reports startup progress; Reduce Motion is available during both stages.
- Each account can select or change a provisional servant appearance. It applies to all saves for that account and does not affect story outcomes or scores.
- The welcome and main menu show character artwork beside their controls on wider windows. On smaller windows, the artwork panel is hidden to keep the controls usable.
- Main menu layout: title, decorative divider, New Game, Continue, Save/Load, Settings, and Quit.
- Menus keep readable text as the window changes size and scroll when needed. Hover and keyboard focus states remain available.
- Working signup/login/logout with normalized usernames, protected password verifiers, and matching account/student IDs.
- Account-safe history, full-save, load, Continue, and assessment coordination; no controller-supplied student IDs.
- Continue is enabled when the signed-in account has a full save. If an episode is active, the button becomes Return to Game and resumes its story, checkpoint, or result view. A new account has no saved progress.
- Save/Load shows three real account-scoped slots. Saving needs an active game; successful writes and discard/overwrite confirmations are handled by services.
- New Game lists prepared story episodes at the Java-approved starting difficulty. The completed episode supports Study-first and Hall-first investigations, then offers four conclusion outcomes.
- Quit displays a confirmation before closing the window.
- The earlier FXML dashboard remains in the project but is not the opening screen.
- The older standalone backend demo may use synthetic records for local checks; it is not the player-facing story source. The JavaFX app loads story and question text from versioned JSON packs.
- Shared story, checkpoint, game-state, and full-save records support version-1 packs and adaptive version-2 packs. Save encoding uses version 2 and still reads version-1 snapshots.
- Java owns first-answer scoring and the final difficulty decision. `ModelAssistedDifficultyPolicy` summarizes stored history, asks the optional local helper, validates the recommendation, and keeps the deterministic fallback.
- The app selects MySQL when all three `ARG_DB_*` settings are present. It uses in-memory development storage when all three settings are absent.
- MySQL adapters persist accounts, servant appearances, episodes, route choices, checkpoints, first-answer drafts, checkpoint results, decision sources, and three full-save slots. The schema migrations V001 through V005 are in `database/migrations/`.
- Generic story transitions keep narrative choices separate from comprehension scoring. MySQL and in-memory adapters store chapter graphs and first-answer drafts; unfinished assessments can restore from their stored first answers.
- The live app uses `how-to-play.fxml`, `gameplay.fxml`, `results.fxml`, and `analysis.fxml` for the adaptive story and checkpoint flow. The 40 `uiux-*.fxml` files remain visual references. The player can make story choices and answer the separate checkpoint question.
- Settings provide session-only reading text-size, character-art, and reduced-motion controls. Audio Settings provides separate session-only volume controls for music and sound effects.
- The opening, welcome, login, and main menu play optional background music. Button actions can play a click sound. Story scenes use optional local investigation and tense music; reading checks remain quiet. See [music and sound effects](assets/README.md#music-and-sound-effects).
- Story Log shows up to 500 previously viewed story lines in the current session. Starting a chapter, loading saved progress, or signing out starts a fresh log. Reading questions and answers are not added.
- Optional author-supplied stage directions can show a supporting character beside the current speaker. The [story content and art guide](assets/README.md) describes the cue file and fallback behavior.
- Java integrates an optional local Qwen recommendation behind `DifficultyPolicy`. Java scores first answers, summarizes stored history, validates the recommendation, and applies the fallback. The Python helper returns one label and receives no account IDs or raw answers.
- Qwen stays experimental and is not a trained reading-level classifier. Java holds the current level until three consecutive sections at the same difficulty exist, checks model suggestions, and applies its fallback. The pinned weight uses Git LFS, and the supplied image paths have Git LFS rules.

Java unit tests, JavaFX input tests, disposable-MySQL tests, Python helper tests, and an opt-in real-model smoke test cover their respective code paths. The default story pack preserves the original draft and includes a separate completed chapter with save and restore coverage. The group has not approved the player identity, suspect, ending, or answer keys. The model has not been evaluated for reading outcomes, and teacher or parent reporting is outside this student-game scope. See [completed story verification](docs/requirements/Completed-Story-Verification-2026-10-28.md) and [opening, appearance, and audio verification](docs/requirements/Dev1-Opening-Appearance-Audio-QA-2026-10-28.md) for test results and limits.

## Current implementation ownership

The backend implementation is consolidated in the Dev1 workstream. The [Backend Development Guide](docs/requirements/Backend-Development-Guide.md) records the original service plan; the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md) describes the current implementation and remaining group inputs.

- Backend implementation: accounts, gameplay, MySQL persistence, content-pack loading, and optional model integration are connected in this branch.
- Remaining review inputs: approve or revise the player identity, culprit, ending, answer keys, and final UI/UX details. The app loads story material through the versioned content pack rather than story-specific Java classes.

Run the baseline backend without Python or a database:

```sh
mvn test
mvn compile exec:java
```

The original console demo scores a sample question, records one completion, and saves a basic checkpoint. All demo data is volatile; this low-level demo is not a fully wired game. To check the new real account flow:

```sh
mvn compile exec:java -Dexec.mainClass=com.adaptivereadinggame.backend.Dev1Demo
```

## Directory architecture

```text
AdaptiveReadingGame/
|-- assets/
|   |-- content/                    Versioned draft and completed story packs
|   |   `-- source/                 Storyboard snapshot and node-source mapping
|   |-- characters/                 Character-expression artwork
|   |-- scenes/                     Background art
|   `-- cg/                         Story illustrations
|-- database/
|   |-- migrations/                 Versioned MySQL schema files
|   `-- examples/                   Versioned full-save JSON example
|-- model-tool/                     Optional local Python inference helper
|-- scripts/                        Prerequisite checks, audio bridge, and launchers
|-- docs/
|   |-- requirements/
|   |-- diagrams/
|   `-- meeting-notes/
|-- Module Guide/                   Compilation instructions
|-- src/main/
|   |-- java/
|   |   |-- MainController.java     Deprecated launcher forwarding to the main app
|   |   `-- com/adaptivereadinggame/
|   |       |-- ai/                 Java model client and recommendation service
|   |       |-- audio/              Music and click sound playback
|   |       |-- content/            Versioned story packs and safe asset lookup
|   |       |-- AdaptiveReadingGameApp.java
|   |       |-- backend/             BackendContext composition and console demo
|   |       |-- controller/
|   |       |   |-- AppearanceController.java
|   |       |   |-- SplashController.java
|   |       |   |-- MenuController.java
|   |       |   |-- GameplayController.java
|   |       |   |-- ResultsController.java
|   |       |   |-- AnalysisController.java
|   |       |   `-- MainController.java
|   |       |-- model/
|   |       |   |-- Account.java
|   |       |   |-- AssessmentResult.java
|   |       |   |-- Student.java
|   |       |   |-- Chapter.java
|   |       |   |-- StoryCheckpoint.java
|   |       |   |-- Difficulty.java
|   |       |   |-- DifficultyDecisionSource.java
|   |       |   |-- ReadingMaterial.java
|   |       |   |-- Question.java
|   |       |   |-- Attempt.java
|   |       |   |-- SaveGame.java
|   |       |   `-- ReadingLevel.java
|   |       |-- service/
|   |       |   |-- AdaptiveEngine.java
|   |       |   |-- AssessmentService.java
|   |       |   |-- RepositoryStoryService.java
|   |       |   |-- ModelAssistedDifficultyPolicy.java
|   |       |   |-- AuthService.java
|   |       |   |-- BaselineDifficultyPolicy.java
|   |       |   |-- DifficultyPolicy.java
|   |       |   |-- PasswordHasher.java
|   |       |   |-- ProgressService.java
|   |       |   `-- InMemoryAdaptiveEngine.java
|   |       `-- repository/
|   |           |-- AccountRepository.java
|   |           |-- AssessmentRepository.java
|   |           |-- ChapterRepository.java
|   |           |-- CredentialRepository.java
|   |           |-- SaveGameRepository.java
|   |           |-- StudentRepository.java
|   |           |-- memory/         Development-only storage adapters
|   |           `-- mysql/          MySQL adapters and migration runner
|   `-- resources/com/adaptivereadinggame/
|       |-- audio/                  Music and generated click sound
|       |-- view/
|       |   |-- appearance.fxml
|       |   |-- splash.fxml
|       |   |-- account.fxml
|       |   |-- main-menu.fxml
|       |   |-- gameplay.fxml
|       |   |-- results.fxml
|       |   |-- analysis.fxml
|       |   |-- how-to-play.fxml
|       |   |-- dashboard.fxml
|       |   `-- uiux-*.fxml        Visual-only screen references
|       `-- style/
|           `-- application.css
|-- pom.xml                         Maven configuration and dependencies
|-- bin/                            Generated manual compilation output
`-- target/                         Generated Maven output
```

`bin/` and `target/` are created by build commands. Keep practice projects outside this application's source folder. The older `main.java` learning example has been removed.

Backend tests live under `src/test/java/com/adaptivereadinggame/backend/`. MySQL setup and verification steps are in [database/README.md](database/README.md).

## How the architecture works

The app uses Model-View-Controller (MVC), with separate service and repository layers.

| Part | Responsibility |
|---|---|
| Application | Starts JavaFX and loads the opening screen. |
| View (FXML) | Defines screen layout and controls. |
| CSS | Defines fonts, colors, spacing, and control states. |
| Controller | Receives player actions, calls services, and updates the view. |
| Model | Holds player, reading material, question, and attempt data. |
| Service | Applies authentication, story transitions, scoring, saves, and difficulty selection. |
| Repository | Defines how data is loaded and saved. |

Current account, content, storage, and gameplay connection:

```text
AdaptiveReadingGameApp
    -> OpeningScreen -> initialize one BackendContext -> wait for player input
    -> AppNavigator -> account.fxml -> appearance.fxml when no choice exists
    -> account.fxml -> AccountController -> AccountGameService
    -> main-menu.fxml -> MenuController -> AccountGameService
    -> gameplay.fxml -> GameplayController -> AccountGameService
    -> results.fxml / analysis.fxml -> stored Java assessment result
    -> ARG_DB_* settings -> MySqlAccountRepository + MySqlGameRepository
    -> no database settings -> in-memory development repositories
    -> ARG_CONTENT_PACK or bundled versioned story pack -> validate and import story episodes
```

An FXML button's `onAction="#newGame"` calls `newGame()` in its controller. The FXML `fx:controller` attribute selects that controller. An `fx:id` connects a screen element to a matching `@FXML` field.

Live gameplay connection:

```text
Player action -> Controller -> Service -> Repository
                     |
                     v
                Updated View
```

Models carry data between these parts. The gameplay controller calls the account coordinator for story transitions, reading questions, first-answer feedback, saves, and results. The views do not calculate scores or choose difficulty.

When gameplay calls assessment completion, `ModelAssistedDifficultyPolicy` reads chronological Java history and sends a numeric summary through `QwenClient` to the loopback Python helper. Java validates the suggested action and chooses the result. The result records whether the model, Java guardrail, or Java fallback supplied the accepted path. The helper receives no learner IDs or raw answers. See [the model-tool guide](model-tool/README.md) for setup and fallback behavior.

## Playable screen flow

1. Login or signup
2. Main menu, including new game, continue, save/load, settings, logout, and quit
3. How to Play for the selected chapter
4. Story dialogue, thoughts, and narrative choices
5. Separate Study or Hall passage and comprehension checkpoint
6. First-answer feedback, checkpoint result, and the next story route
7. Draft-boundary summary, analysis, or a clear no-content state

Gameplay can return to the main menu and resume the active story or reading check. Additional states include answer feedback and explanations, quit and overwrite confirmations, account validation errors, empty save slots, and disabled Continue when neither active nor saved progress exists.

Story choices affect story events. Reading Checks assess understanding using the first submitted answer. A retry after the answer is revealed must not increase the assessment score.

The new backend's `Difficulty` enum uses Easy, Medium, and Hard. Each current checkpoint has one question at each level, so one checkpoint score is 0% or 100%. Java stores each first answer and validates the recommendation before applying the next level. The optional model waits for three consecutive sections at the same level. This small draft cannot establish that the rule or model measures reading ability. The older four-band `ReadingLevel` enum is separate and does not affect checkpoint difficulty.

The [concept proposal](https://docs.google.com/document/d/1XHok_fuPZJek9GvWUSSv_Bs5QgfURQUb/edit) asks for educator diagnostic reports and transparent parent/guardian progress views. It does not define viewing roles, report fields, or a student-skill taxonomy. Those decisions are needed before implementing the views.
