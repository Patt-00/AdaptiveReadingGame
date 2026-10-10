# Adaptive Reading Game

A school project for a story-driven reading game for elementary students. Players will read stories, make story decisions, and answer comprehension questions. The planned system will save progress per account and adjust reading difficulty after a completed chapter.

This `Dev1` branch adds the account/backend connections to the early JavaFX prototype. See the [Dev 1 handoff](docs/requirements/Dev1-Handoff.md) for exact teammate integration steps and remaining work.

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

For commands without Maven, see [Manual Compilation](Module%20Guide/Manual%20Compilation.md).

## Current status

- Main menu layout: title, decorative divider, New Game, Continue, Save/Load, Settings, and Quit.
- Menu scales with the window and includes hover and keyboard focus states.
- Working signup/login/logout with normalized usernames, protected password verifiers, and matching account/student IDs.
- Account-safe history, full-save, load, Continue, and assessment coordination; no controller-supplied student IDs.
- Continue is enabled only when the signed-in account has a full save. The default runtime has no prepared story or seeded saves.
- Save/Load shows three real account-scoped slots. Saving needs an active game; successful writes and discard/overwrite confirmations are handled by services.
- New Game honestly reports missing story content. Settings and the full playable story remain teammate work.
- Quit displays a confirmation before closing the window.
- The earlier FXML dashboard remains in the project but is not the opening screen.
- The in-memory adaptive engine supplies demo data and placeholder reading material.
- Shared story/game-state/full-save records, repository interfaces, and strict version-1 JSON serialization now exist.
- Java owns first-answer scoring and baseline next difficulty. The account coordinator writes the first answer through the draft repository before feedback.
- The default adapters are temporary: accounts, saves, and results disappear on exit. Full story gameplay and restart-safe reading-check restore are not implemented.
- MySQL is the group's chosen database; Dev 3's adapters are not connected here.
- Qwen remains an experimental model candidate on the separate `feature/java-owned-qwen-prototype` branch and was not merged.

Backend tests and opt-in native JavaFX interaction tests verify the implemented scope. Test stories/populated saves are fixtures only; they do not prove completed gameplay or MySQL persistence.

## Backend skeleton and team ownership

See [Backend Development Guide](docs/requirements/Backend-Development-Guide.md) for service contracts, unfinished work, and the three-developer task split:

- Backend Dev 1: authentication, current-account lifecycle, authenticated save/load/Continue coordination.
- Backend Dev 2: chapter/story flow, first-answer assessment, results, Java difficulty decisions, later model integration.
- Dev 3: MySQL schema/migrations, repository adapters, and persistence tests.

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
|-- assets/                         Original artwork and design exports
|-- database/                       Database schema and sample-data scripts
|-- docs/
|   |-- requirements/
|   |-- diagrams/
|   `-- meeting-notes/
|-- Module Guide/                   Compilation instructions
|-- src/main/
|   |-- java/
|   |   |-- MainController.java     Deprecated launcher forwarding to the main app
|   |   `-- com/adaptivereadinggame/
|   |       |-- AdaptiveReadingGameApp.java
|   |       |-- backend/             BackendContext composition and console demo
|   |       |-- controller/
|   |       |   |-- MenuController.java
|   |       |   `-- MainController.java
|   |       |-- model/
|   |       |   |-- Account.java
|   |       |   |-- AssessmentResult.java
|   |       |   |-- Student.java
|   |       |   |-- Chapter.java
|   |       |   |-- Difficulty.java
|   |       |   |-- ReadingMaterial.java
|   |       |   |-- Question.java
|   |       |   |-- Attempt.java
|   |       |   |-- SaveGame.java
|   |       |   `-- ReadingLevel.java
|   |       |-- service/
|   |       |   |-- AdaptiveEngine.java
|   |       |   |-- AssessmentService.java
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
|   |           `-- memory/         Development-only storage adapters
|   `-- resources/com/adaptivereadinggame/
|       |-- view/
|       |   |-- main-menu.fxml
|       |   `-- dashboard.fxml
|       `-- style/
|           `-- application.css
|-- pom.xml                         Maven configuration and dependencies
|-- bin/                            Generated manual compilation output
`-- target/                         Generated Maven output
```

`bin/` and `target/` are created by build commands. Keep practice projects outside this application's source folder. The older `main.java` learning example has been removed.

Backend tests live under `src/test/java/com/adaptivereadinggame/backend/`. Database planning starts in [database/README.md](database/README.md).

## How the architecture works

The app uses Model-View-Controller (MVC), with separate service and repository layers.

| Part | Responsibility |
|---|---|
| Application | Starts JavaFX and loads the opening screen. |
| View (FXML) | Defines screen layout and controls. |
| CSS | Defines fonts, colors, spacing, and control states. |
| Controller | Receives player actions, calls services, and updates the view. |
| Model | Holds player, reading material, question, and attempt data. |
| Service | Applies game rules, scoring, and difficulty selection when implemented. |
| Repository | Defines how data is loaded and saved. |

Current menu connection:

```text
AdaptiveReadingGameApp
    -> AppNavigator (one BackendContext)
    -> account.fxml -> AccountController -> AccountGameService
    -> main-menu.fxml -> MenuController -> AccountGameService
```

An FXML button's `onAction="#newGame"` calls `newGame()` in its controller. The FXML `fx:controller` attribute selects that controller. An `fx:id` connects a screen element to a matching `@FXML` field.

Planned gameplay connection:

```text
Player action -> Controller -> Service -> Repository
                     |
                     v
                Updated View
```

Models carry data between these parts. The current menu does not call a service or repository. The earlier dashboard controller calls the demo adaptive engine.

## Planned UI/UX screens

1. Login
2. Signup
3. Main Menu, including Logout
4. How to Play
5. Story / Dialogue
6. Story Choice
7. Reading Check
8. Chapter Results
9. Save / Load
10. Settings

Additional states include answer feedback and explanations, a gameplay menu, quit and overwrite confirmations, account validation errors, empty save slots, and disabled Continue.

Story choices affect story events. Reading Checks assess understanding using the first submitted answer. A retry after the answer is revealed must not increase the assessment score.

The new backend's `Difficulty` enum uses Easy, Medium, and Hard. For a chapter with five questions: 4-5 correct increases one level, 3 keeps the level, and 0-2 decreases one level, within the allowed range. These are prototype rules, not a validated reading assessment. The existing `ReadingLevel` enum still uses four legacy dashboard bands; the new gameplay services do not use it for difficulty decisions.

The final UI/UX submission needs the system title, labeled screen images in logical order, and a 2-3 sentence explanation per screen. The concept proposal and exact SDG have not been supplied. The existing [player flowchart](docs/diagrams/AdaptiveReadingGame_Flowchart.png) predates authentication and needs Login, Signup, and Logout added.
