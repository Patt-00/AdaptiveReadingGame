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
- `StudentRepository` defines storage operations; no storage implementation exists.
- Login, Signup, Logout, gameplay, assessment, difficulty adjustment, and account saves are not implemented.
- No AI model or database engine has been selected or integrated.

The menu and cleanup passed a clean Maven compilation. This does not verify GUI appearance or interactions.

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
|   |       |-- controller/
|   |       |   |-- MenuController.java
|   |       |   `-- MainController.java
|   |       |-- model/
|   |       |   |-- Student.java
|   |       |   |-- ReadingMaterial.java
|   |       |   |-- Question.java
|   |       |   |-- Attempt.java
|   |       |   `-- ReadingLevel.java
|   |       |-- service/
|   |       |   |-- AdaptiveEngine.java
|   |       |   `-- InMemoryAdaptiveEngine.java
|   |       `-- repository/
|   |           `-- StudentRepository.java
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

The proposed difficulty levels are Easy, Medium, and Hard. For a chapter with five questions: 4-5 correct increases one level, 3 keeps the level, and 0-2 decreases one level, within the allowed range. These are prototype rules, not a validated reading assessment. The existing `ReadingLevel` enum still uses four different levels and has not been aligned with this proposal.

The final UI/UX submission needs the system title, labeled screen images in logical order, and a 2-3 sentence explanation per screen. The concept proposal and exact SDG have not been supplied. The existing [player flowchart](docs/diagrams/AdaptiveReadingGame_Flowchart.png) predates authentication and needs Login, Signup, and Logout added.
