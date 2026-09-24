# Adaptive Reading Game

A Java 21 / JavaFX starter project for a reading-comprehension game that adjusts material to a student's performance.

## Run

Install Maven, then run:

```sh
mvn javafx:run
```

## Project structure

```
src/main/java/com/adaptivereadinggame/
├── controller/  # JavaFX event handlers
├── model/       # Student, material, question, and attempt data
├── repository/  # Persistence boundaries
└── service/     # Adaptive-game business rules

src/main/resources/com/adaptivereadinggame/
├── view/        # FXML screens
└── style/       # Application CSS
```

The dashboard is functional as a UI shell. The in-memory adaptive engine supplies demo data until a database and reading-session flow are added.

## Original architecture

```
AdaptiveReadingGame/
│
├── README.md
├── .gitignore
│
├── docs/
│   ├── requirements/
│   ├── diagrams/
│   └── meeting-notes/
│
├── database/
│
├── assets/
│
└── src/
    └── main/
        ├── java/
        │
        └── resources/
```
<h1>
    MVC ARCHITECTURE FOR SYSTEM
</h1>

```
                    USER
                      |
                      |
                      v
        +-----------------------------+
        |            VIEW             |
        |        (JavaFX GUI)         |
        |                             |
        | - Login Screen              |
        | - Story Screen              |
        | - Question Screen           |
        | - Dashboard Screen          |
        +-------------+---------------+
                      |
                      | User Actions
                      | (click, input, submit)
                      v
        +-----------------------------+
        |        CONTROLLER           |
        |                             |
        | - Receives GUI events       |
        | - Gets user input           |
        | - Calls system functions    |
        | - Updates View              |
        +-------------+---------------+
                      |
                      | Requests / Data
                      v
        +-----------------------------+
        |           MODEL             |
        |                             |
        |  +-----------------------+  |
        |  | Business Logic        |  |
        |  |                       |  |
        |  | - Assessment          |  |
        |  | - Scoring             |  |
        |  | - Adaptive Engine     |  |
        |  | - Progress Tracking   |  |
        |  +-----------------------+  |
        |                             |
        |  Data Objects:              |
        |  - Student                  |
        |  - Question                 |
        |  - ReadingMaterial          |
        |  - Attempt                  |
        |  - PerformanceRecord        |
        +-------------+---------------+
                      |
                      |
                      v
        +-----------------------------+
        |        DATABASE             |
        |                             |
        | - Student Records           |
        | - Questions                 |
        | - Attempts                  |
        | - Progress Data             |
        +-----------------------------+
```
