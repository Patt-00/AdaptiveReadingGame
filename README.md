<h1> SYSTEM ARCHITECTURE</h1>

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
