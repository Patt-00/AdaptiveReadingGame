# Adaptive Reading Game

A school project for a story-driven reading game for elementary students. Players read stories, make story decisions, and answer comprehension questions. The game supports account-based save slots and adjusts question difficulty after a completed checkpoint.

The JavaFX game includes account services, versioned story content, MySQL repositories, and an optional local difficulty helper. See [story content and art](assets/README.md) and the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md).

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

The setup script does not install or create a MySQL server, database, or user. To use persistent storage, configure MySQL and apply the migrations in [database setup](database/README.md). Preflight checks the Java and Maven runtimes, story-pack path, and MySQL setting shape; it does not connect to MySQL or apply migrations.

You can run the preflight check by itself to confirm Java 21 or newer, including the runtime Maven uses, the selected story-pack file, and MySQL settings without changing the database. The check also reports missing optional Git LFS assets, story music, and model weights. If the check passes, use the launch script:

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

The application starts in full screen on supported desktop and Termux:X11 launches. Set `ARG_FULL_SCREEN=false` to open it in a window. You can still start the app directly with `mvn javafx:run`. The Java startup screen checks and loads the story pack and provides Retry when startup fails. The preflight scripts check that the selected pack file exists; the Java loader validates its contents.

To compile manually before launch, run these commands from the project root:

```sh
mvn -B -DskipTests compile
mvn javafx:run
```

### Run the game from Termux:X11

The game runs inside a Debian PRoot environment on Termux and opens its JavaFX window through Termux:X11. The Android Java runtime cannot load the desktop JavaFX native libraries. Debian supplies a Linux ARM64 runtime, and Maven resolves JavaFX artifacts for that platform. See the [OpenJFX Maven setup](https://openjfx.io/openjfx-docs/), [Termux:X11 instructions](https://github.com/termux/termux-x11), and [proot-distro options](https://github.com/termux/proot-distro).

Install the Termux:X11 Android app from its [official release page](https://github.com/termux/termux-x11/releases). The setup script installs the Termux companion package, Debian PRoot, JDK 21 or newer, Maven, Git LFS, `xdpyinfo`, PulseAudio, and the Linux libraries used by JavaFX graphics and media. It downloads the artwork and menu music, then compiles the Java sources. The launcher maps ALSA's default device to the Termux PulseAudio sink over loopback using the [Termux PulseAudio PRoot method](https://github.com/termux/termux-packages/issues/12289). It does not download the optional Qwen model or install MySQL.

Run setup once from the project root in Termux:

```sh
./scripts/termux-setup-x11.sh
```

The setup script uses the current checkout. If you run it outside a checkout, it clones `main` into `$HOME/AdaptiveReadingGame-Termux`. Pass `--project-dir PATH` to select another project directory. A temporary Android package-manager query failure is treated as unknown; the launcher tries to open Termux:X11 and reports a missing app only when Android explicitly cannot resolve its activity.

Launch the game from the same project root:

```sh
./scripts/termux-run-x11.sh
```

The launcher checks that `xdpyinfo` can reach an X11 server before using a display socket. If a socket remains after its server exits, it removes that stale socket and starts a new display. It opens the Termux:X11 app before waiting for the display to answer. X11 startup diagnostics are written to `$HOME/.local/state/adaptive-reading-game/x11.log`. The game runs in a detached Debian PRoot session and requests fullscreen mode. The launcher writes a separate startup log to `target/termux-game-<timestamp>-<pid>.log` for each launch; `target/termux-game.log` points to the latest log. It uses Maven to compile changed Java sources, caches the runtime classpath under `target`, and starts Java directly with the JavaFX version configured in `pom.xml`. Setup prepares this cache so later launches do not need the JavaFX Maven launcher. Set `ARG_TERMUX_X11_DISPLAY=:1` to choose another display. JavaFX uses software rendering by default; set `ARG_TERMUX_PRISM_ORDER=es2` to request its accelerated pipeline. Set `ARG_TERMUX_MEDIA_DEBUG=true` to show detailed JavaFX media logs.

Java's difficulty fallback keeps the game playable when the optional Python model helper is not running.

The supported Termux:X11 launcher uses Debian PRoot. Alpine PRoot is not a supported launch target.

The Termux launcher does not change Android's media-volume setting. If PulseAudio reports an active stream but the game sounds silent, raise the device's media volume and check its selected audio output.

### Compile-check source in native Termux

Run `./scripts/termux-build-check.sh` from a Termux checkout when you only need a Java source compile check. A standalone copy clones `main` into `$HOME/AdaptiveReadingGame-Termux` when it cannot find a project. The script installs missing Git, JDK 21, or Maven packages and compiles the Java sources. It uses desktop Linux JavaFX classes for compilation on ARM64; it cannot launch the game window. Pass `--install-only` to check or install tools without cloning or compiling. Pass `--project-dir PATH` to use an existing checkout or select a clone directory.

The launch class is `com.adaptivereadinggame.AdaptiveReadingGameApp`. It opens an animated text introduction and a loading screen. Once startup finishes, press Space or select Enter to open authentication. A new account selects a servant appearance before reaching the main menu.

With no database settings, the app uses temporary in-memory storage and shows a plain-language notice on the account and main menu before logging the storage mode at startup. With MySQL settings, progress is saved to the configured account. To use MySQL, set `ARG_DB_URL`, `ARG_DB_USER`, and `ARG_DB_PASSWORD`, then apply the migrations described in [database setup](database/README.md). Migrations V001 through V006 are in `database/migrations/`.

The app loads `assets/content/hailey-investigation-complete.json` when `ARG_CONTENT_PACK` is not set. That version-3 pack contains the unchanged review draft, the previous completed edition, and the new selectable edition under separate stable IDs. The new edition has 36 question entries across its four route-specific checkpoints, with three questions at each difficulty; a normal route answers six questions. Version-1 and version-2 packs remain supported, and older pack versions default to selectable chapters. See [the content-pack guide](assets/README.md).

Character, scene, and CG keys resolve under `assets/characters/`, `assets/scenes/`, and `assets/cg/`. Set `ARG_ASSET_DIR` only when the same folder layout is stored under another trusted asset root.

The optional Qwen helper runs separately from Java. Follow the [model-tool guide](model-tool/README.md) to start it; Java keeps the difficulty decision and uses its fallback when the helper is unavailable.

For commands without Maven, see [Manual Compilation](Module%20Guide/Manual%20Compilation.md).

## Current status

- Startup shows the animated opening and loading progress. Space or Enter enters the game after loading; Reduce Motion is available.
- Players can sign up, log in, select a servant appearance, adjust reading and audio settings, play stories, save and load progress, view Reading History, manage their account, and quit.
- The account and main-menu screens explain whether progress is temporary or saved to the player's account.
- The default version-3 pack preserves earlier editions for saved progress and offers a new edition for new games. It supports both investigation orders and four story outcomes.
- Each checkpoint stores three questions at every difficulty. A normal route presents six questions. Only each question's first submitted answer counts; story choices do not affect comprehension scores.
- Reading level wording describes questions, not story scenes. Java owns scoring, persistence coordination, and the final difficulty choice. The optional Qwen helper suggests a level; Java applies its safeguards and fallback.
- The game uses written dialogue and images. Menu music and a click effect are bundled; optional story tracks remain local under their distribution terms. The 40 `uiux-*.fxml` files are visual references; the running game uses `how-to-play.fxml`, `gameplay.fxml`, `results.fxml`, and `analysis.fxml`.
- See the [latest integration verification report](docs/requirements/Final-Integration-Verification-2026-11-05.md) for test results and platform limits. The new story conclusion and question set need group and teacher review. Educational quality of the optional model has not been evaluated; teacher and parent reporting remains deferred.

## Backend development

Java owns account boundaries, answer scoring, save and load coordination, and difficulty decisions. Player-facing controllers call `AccountGameService`; repository adapters provide temporary in-memory or MySQL storage. See the [Backend Development Guide](docs/requirements/Backend-Development-Guide.md) for the implementation guide and the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md) for current interfaces.

Story and question text comes from the versioned content pack.

Run the backend tests and baseline console demo without Python or a database:

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
|   |       |   `-- LevelCopy.java          Shared reading-level wording
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
2. Main menu, including new game, continue, save/load, settings, credits, logout, and quit
3. How to Play for the selected chapter
4. Story dialogue, thoughts, and narrative choices
5. Separate Study or Hall passage and comprehension checkpoint
6. First-answer feedback, checkpoint result, and the next story route
7. Draft-boundary summary, analysis, or a clear no-content state

Gameplay can return to the main menu and resume the active story or reading check. Additional states include answer feedback and explanations, quit and overwrite confirmations, account validation errors, empty save slots, and disabled Continue when neither active nor saved progress exists.

The `Difficulty` enum uses Easy, Medium, and Hard. Each checkpoint in the new edition asks three questions at the selected level, so its score can be 0%, 33%, 67%, or 100%. Java stores first answers and validates difficulty recommendations. The optional model requires three consecutive completed sections at the same level, while the story has two checkpoints per route, so a normal playthrough uses Java's fallback. The model smoke test checks the request contract; it does not establish that the rule or model measures reading ability. The older four-band `ReadingLevel` enum does not affect checkpoint difficulty.

The [concept proposal](https://docs.google.com/document/d/1XHok_fuPZJek9GvWUSSv_Bs5QgfURQUb/edit) asks for educator diagnostic reports and transparent parent/guardian progress views. It does not define viewing roles, report fields, or a student-skill taxonomy. Those decisions are needed before implementing the views.
