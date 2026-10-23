# Adaptive Reading Game

A school project for a story-driven reading game for elementary students. Players will read stories, make story decisions, and answer comprehension questions. The planned system will save progress per account and adjust reading difficulty after a completed chapter.

This `Dev1` branch connects account services, reusable story and assessment services, MySQL repositories, a versioned content-pack loader, and live JavaFX gameplay screens. The approved story, comprehension questions, and character art are not included yet. See [adding content](assets/README.md) and the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md).

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

To load approved story content, set `ARG_CONTENT_PACK` to a local version-1 JSON pack before launching. See [the content-pack guide](assets/README.md). The application has no built-in story or test chapter.

Character image keys resolve under `assets/characters/` by default. Set `ARG_ASSET_DIR` only when approved art is stored under a different local assets folder.

The optional Qwen helper runs separately from Java. Follow the [model-tool guide](model-tool/README.md) to start it; Java keeps the difficulty decision and uses its fallback when the helper is unavailable.

For commands without Maven, see [Manual Compilation](Module%20Guide/Manual%20Compilation.md).

## Current status

- Main menu layout: title, decorative divider, New Game, Continue, Save/Load, Settings, and Quit.
- Menu scales with the window and includes hover and keyboard focus states.
- Working signup/login/logout with normalized usernames, protected password verifiers, and matching account/student IDs.
- Account-safe history, full-save, load, Continue, and assessment coordination; no controller-supplied student IDs.
- Continue is enabled when the signed-in account has a full save. If a chapter is already active, the button becomes Return to Game and resumes its story, question, or results view. The default runtime has no prepared story or seeded saves.
- Save/Load shows three real account-scoped slots. Saving needs an active game; successful writes and discard/overwrite confirmations are handled by services.
- New Game lists prepared chapters when a valid content pack is loaded. With no pack, it explains that story content is not ready.
- Quit displays a confirmation before closing the window.
- The earlier FXML dashboard remains in the project but is not the opening screen.
- The older standalone backend demo may use synthetic records for local checks; it is not the player-facing story source. The JavaFX app loads only chapters from an approved content pack.
- Shared story/game-state/full-save records, repository interfaces, and strict version-1 JSON serialization now exist.
- Java owns first-answer scoring and the final difficulty decision. `ModelAssistedDifficultyPolicy` summarizes stored history, asks the optional local helper, validates the recommendation, and keeps the deterministic fallback.
- The app selects MySQL when all three `ARG_DB_*` settings are present. It uses in-memory development storage when all three settings are absent.
- MySQL adapters persist accounts, students, chapters, story nodes, first-answer drafts, results with the validated difficulty decision source, and three full-save slots. The schema migrations V001 through V003 are in `database/migrations/`.
- Generic story transitions keep narrative choices separate from comprehension scoring. MySQL and in-memory adapters store chapter graphs and first-answer drafts; unfinished assessments can restore from their stored first answers.
- Approved content is not bundled. The live app uses `how-to-play.fxml`, `gameplay.fxml`, `results.fxml`, and `analysis.fxml` with dynamic chapter data, story choices, comprehension questions, feedback, scores, and adaptive difficulty. The 40 `uiux-*.fxml` screens remain static visual references.
- Settings provide a session-only reading text-size adjustment and character-art toggle. Narration audio is unavailable until audio assets exist. Educator reports and parent/guardian progress views need agreed access roles and report fields before implementation.
- Java integrates an optional local Qwen recommendation behind `DifficultyPolicy`. Java scores first answers, summarizes stored history, validates the recommendation, and applies the fallback. The Python helper returns one label and receives no account IDs or raw answers.
- Qwen stays experimental and is not a trained reading-level classifier. The app holds the current level until three consecutive sections at the same difficulty exist. The pinned weight is included in the local Dev1 working copy through Git LFS; this LFS change is not on GitHub yet.

Backend tests, opt-in native JavaFX tests, a disposable-MySQL integration test, Python helper contract tests, and an opt-in real-model smoke test verify the implemented scope. Synthetic story fixtures exercise the live gameplay flow in tests only. The group-authored story and questions, actual model quality, and family or educator progress views remain unverified. See [playable-flow verification](docs/requirements/Dev1-Playable-Flow-Verification-2026-10-22.md) for the current test record.

## Current implementation ownership

The backend implementation is consolidated in the Dev1 workstream. The [Backend Development Guide](docs/requirements/Backend-Development-Guide.md) records the original service plan; the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md) describes the current implementation and remaining group inputs.

- Backend implementation: accounts, gameplay, MySQL persistence, content-pack loading, and optional model integration are connected in this branch.
- Remaining group inputs: provide the approved story, comprehension questions, character artwork, and any final UI/UX details. The app loads story material through the versioned content pack rather than story-specific Java classes.

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
|-- assets/                         Content-pack guide and future approved artwork
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
|   |       |-- content/            Story-pack validation, import, and asset lookup
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
    -> optional ARG_CONTENT_PACK -> validate and import approved chapters
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
5. Separate reading passage and comprehension questions
6. First-answer feedback and chapter results
7. Analysis, next-chapter selection, or a clear no-chapter state

Gameplay can return to the main menu and resume the active story or reading check. Additional states include answer feedback and explanations, quit and overwrite confirmations, account validation errors, empty save slots, and disabled Continue when neither active nor saved progress exists.

Story choices affect story events. Reading Checks assess understanding using the first submitted answer. A retry after the answer is revealed must not increase the assessment score.

The new backend's `Difficulty` enum uses Easy, Medium, and Hard. For a chapter with five questions: 4-5 correct increases one level, 3 keeps the level, and 0-2 decreases one level, within the allowed range. These are prototype rules, not a validated reading assessment. The existing `ReadingLevel` enum still uses four legacy dashboard bands; the new gameplay services do not use it for difficulty decisions.

The [concept proposal](https://docs.google.com/document/d/1XHok_fuPZJek9GvWUSSv_Bs5QgfURQUb/edit) asks for educator diagnostic reports and transparent parent/guardian progress views. It does not define viewing roles, report fields, or a student-skill taxonomy. Those decisions are needed before implementing the views.
