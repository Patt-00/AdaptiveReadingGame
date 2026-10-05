# Adaptive Reading Game

A school project for a story-driven reading game for elementary students. Players will read stories, make story decisions, and answer comprehension questions. The planned system will save progress per account and adjust reading difficulty after a completed chapter.

The current school requirement is UI/UX designs. The application is an early JavaFX prototype.

## Requirements and running the app

- JDK 21 or newer; the project compiles for Java 21.
- Maven available on the terminal PATH.
- JavaFX 21.0.5 dependencies are managed by Maven.

Run these commands from the project root:

```sh
mvn -B -DskipTests compile
mvn javafx:run
```

The launch class is `com.adaptivereadinggame.AdaptiveReadingGameApp`. It opens the main menu.

For commands without Maven, see [Manual Compilation](Module%20Guide/Manual%20Compilation.md).

## Current status

- Main menu layout: title, decorative divider, New Game, Continue, Save/Load, Settings, and Quit.
- Menu scales with the window and includes hover and keyboard focus states.
- Continue is disabled because saved games are not implemented.
- New Game, Save/Load, and Settings show placeholder notices.
- Quit displays a confirmation before closing the window.
- The earlier FXML dashboard remains in the project but is not the opening screen.
- The in-memory adaptive engine supplies demo data and placeholder reading material.
- A Java backend skeleton now provides in-memory repositories, first-answer assessment, baseline difficulty selection, history, and three checkpoint slots. These services are not connected to the JavaFX screens yet.
- `AuthService` and `PasswordHasher` are contracts only; Login, Signup, and Logout are not implemented.
- Full story gameplay, persistent account saves, and a database adapter are not implemented.
- Qwen is an experimental model candidate on the separate `feature/java-owned-qwen-prototype` branch, not integrated into main. No database engine has been selected.

The menu and cleanup passed a clean Maven compilation. This does not verify GUI appearance or interactions.

## Backend skeleton and team ownership

See [Backend Development Guide](docs/requirements/Backend-Development-Guide.md) for service contracts, unfinished work, and the three-developer task split:

- Backend Dev 1: authentication, current-account lifecycle, authenticated save/load/Continue coordination.
- Backend Dev 2: chapter/story flow, first-answer assessment, results, Java difficulty decisions, later model integration.
- Dev 3: database choice, schema/migrations, repository adapters, and persistence tests.

Run the baseline backend without Python or a database:

```sh
mvn test
mvn compile exec:java
```

The console demo scores a sample question, records one completion, and saves a checkpoint. All demo data is volatile; this is not a login implementation or a fully wired game.

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
    -> main-menu.fxml + application.css
    -> MenuController
    -> placeholder notices or quit confirmation
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
