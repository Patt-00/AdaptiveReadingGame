# Adaptive Reading Game

A school project for a story-driven reading game for elementary students. Players read stories, make story decisions, and answer comprehension questions. The application saves progress per account and adjusts reading difficulty after a completed checkpoint.

This version connects account services, adaptive story checkpoints, MySQL repositories, the optional difficulty helper, and live JavaFX screens. It includes a provisional story pack and the character and scene artwork shared by the group. The draft uses an unnamed servant and Prince Owen as the working name. The culprit and final ending remain pending. The student game uses written dialogue and images. See [story content and art](assets/README.md) and the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md).

## Requirements and running the app

- JDK 21 or newer; the project compiles for Java 21.
- Maven available on the terminal PATH.
- JavaFX 21.0.5 dependencies are managed by Maven.

Run these commands from the project root:

```sh
mvn -B -DskipTests compile
mvn javafx:run
```

The launch class is `com.adaptivereadinggame.AdaptiveReadingGameApp`. It opens authentication, then the main menu after a successful login.

With no database settings, the app uses visibly labeled in-memory development storage. To use MySQL, set `ARG_DB_URL`, `ARG_DB_USER`, and `ARG_DB_PASSWORD`, then apply the schema described in [database setup](database/README.md).

The app loads `assets/content/hailey-investigation-draft.json` when `ARG_CONTENT_PACK` is not set. Set `ARG_CONTENT_PACK` to a local version-1 or version-2 JSON pack to use other content. The bundled episode is a review draft and ends before a culprit or final ending is chosen. See [the content-pack guide](assets/README.md).

Character, scene, and CG keys resolve under `assets/characters/`, `assets/scenes/`, and `assets/cg/`. Set `ARG_ASSET_DIR` only when the same folder layout is stored under another trusted asset root.

The optional Qwen helper runs separately from Java. Follow the [model-tool guide](model-tool/README.md) to start it; Java keeps the difficulty decision and uses its fallback when the helper is unavailable.

For commands without Maven, see [Manual Compilation](Module%20Guide/Manual%20Compilation.md).

## Current status

- Main menu layout: title, decorative divider, New Game, Continue, Save/Load, Settings, and Quit.
- Menus keep readable text as the window changes size and scroll when needed. Hover and keyboard focus states remain available.
- Working signup/login/logout with normalized usernames, protected password verifiers, and matching account/student IDs.
- Account-safe history, full-save, load, Continue, and assessment coordination; no controller-supplied student IDs.
- Continue is enabled when the signed-in account has a full save. If an episode is active, the button becomes Return to Game and resumes its story, checkpoint, or result view. A new account has no saved progress.
- Save/Load shows three real account-scoped slots. Saving needs an active game; successful writes and discard/overwrite confirmations are handled by services.
- New Game lists prepared story episodes at the Java-approved starting difficulty. The bundled draft supports Study-first and Hall-first routes.
- Quit displays a confirmation before closing the window.
- The earlier FXML dashboard remains in the project but is not the opening screen.
- The older standalone backend demo may use synthetic records for local checks; it is not the player-facing story source. The JavaFX app loads story and question text from versioned JSON packs.
- Shared story, checkpoint, game-state, and full-save records support version-1 packs and adaptive version-2 packs. Save encoding uses version 2 and still reads version-1 snapshots.
- Java owns first-answer scoring and the final difficulty decision. `ModelAssistedDifficultyPolicy` summarizes stored history, asks the optional local helper, validates the recommendation, and keeps the deterministic fallback.
- The app selects MySQL when all three `ARG_DB_*` settings are present. It uses in-memory development storage when all three settings are absent.
- MySQL adapters persist accounts, episodes, route choices, checkpoints, first-answer drafts, checkpoint results, decision sources, and three full-save slots. The schema migrations V001 through V004 are in `database/migrations/`.
- Generic story transitions keep narrative choices separate from comprehension scoring. MySQL and in-memory adapters store chapter graphs and first-answer drafts; unfinished assessments can restore from their stored first answers.
- The live app uses `how-to-play.fxml`, `gameplay.fxml`, `results.fxml`, and `analysis.fxml` for the adaptive story and checkpoint flow. The 40 `uiux-*.fxml` files remain visual references. The player can make story choices and answer the separate checkpoint question.
- Settings provide a session-only reading text-size adjustment and character-art toggle. The reading flow uses text and images, with no audio control or media dependency.
- Java integrates an optional local Qwen recommendation behind `DifficultyPolicy`. Java scores first answers, summarizes stored history, validates the recommendation, and applies the fallback. The Python helper returns one label and receives no account IDs or raw answers.
- Qwen stays experimental and is not a trained reading-level classifier. Java holds the current level until three consecutive sections at the same difficulty exist, checks model suggestions, and applies its fallback. The pinned weight uses Git LFS, and the supplied image paths have Git LFS rules.

Backend tests, JavaFX UI tests, a disposable-MySQL integration test, Python helper contract tests, and an opt-in real-model smoke test cover their respective code paths. The version-2 draft pack has in-memory route and difficulty coverage plus MySQL save and restore coverage. The group has not approved its answer keys, main character, suspect, or ending. The model quality remains unverified. Teacher and parent reporting are outside the student-game scope. See [gameplay repair and verification](docs/requirements/Dev1-Gameplay-QA-2026-10-27.md) and the [gameplay visual polish report](docs/requirements/Dev1-Visual-Polish-Verification-2026-10-27.md) for the current QA record. Earlier reports remain historical records.

## Current implementation ownership

The backend implementation is consolidated in the Dev1 workstream. The [Backend Development Guide](docs/requirements/Backend-Development-Guide.md) records the original service plan; the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md) describes the current implementation and remaining group inputs.

- Backend implementation: accounts, gameplay, MySQL persistence, content-pack loading, and optional model integration are connected in this branch.
- Remaining group inputs: confirm the story and question wording, approve the unnamed player and culprit, provide the ending, and confirm final UI/UX details. The app loads story material through the versioned content pack rather than story-specific Java classes.

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
|   |-- content/                    Provisional story-pack JSON
|   |   `-- source/                 Storyboard snapshot and node-source mapping
|   |-- characters/                 Character-expression artwork
|   |-- scenes/                     Background art
|   `-- cg/                         Story illustrations
|-- database/
|   |-- migrations/                 Versioned MySQL schema files
|   `-- examples/                   Versioned full-save JSON example
|-- model-tool/                     Optional local Python inference helper
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
|   |       |-- content/            Versioned story packs and safe asset lookup
|   |       |-- AdaptiveReadingGameApp.java
|   |       |-- backend/             BackendContext composition and console demo
|   |       |-- controller/
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
|       |-- view/
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
    -> AppNavigator (one BackendContext)
    -> account.fxml -> AccountController -> AccountGameService
    -> main-menu.fxml -> MenuController -> AccountGameService
    -> gameplay.fxml -> GameplayController -> AccountGameService
    -> results.fxml / analysis.fxml -> stored Java assessment result
    -> ARG_DB_* settings -> MySqlAccountRepository + MySqlGameRepository
    -> no database settings -> in-memory development repositories
    -> ARG_CONTENT_PACK or bundled draft pack -> validate and import story episodes
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
