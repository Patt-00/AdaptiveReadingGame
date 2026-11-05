# Adaptive Reading Game - Project Context

## Tech Stack
- **Language:** Java 21
- **UI Framework:** JavaFX 21.0.13 (FXML + CSS)
- **Build Tool:** Maven
- **Database:** MySQL (with in-memory fallback for development)
- **Serialization:** Jackson 2.18.11
- **Testing:** JUnit Jupiter 5.11.4
- **Architecture:** Modular monolith with clean separation (model, repository, service, controller, view)

## Project Structure
```
src/main/java/
├── com.adaptivereadinggame/              # Application entry point
├── com.adaptivereadinggame.ai/           # AI model integration (QwenClient, DifficultyService)
├── com.adaptivereadinggame.audio/        # Audio system (GameAudio, JavaFxAudioOutput)
├── com.adaptivereadinggame.backend/      # Composition root (BackendContext, demos)
├── com.adaptivereadinggame.content/      # Story content loading (StoryPack, AssetResolver)
├── com.adaptivereadinggame.controller/   # JavaFX controllers (AppNavigator, *Controller)
├── com.adaptivereadinggame.model/        # Domain records (Account, Chapter, GameState, etc.)
├── com.adaptivereadinggame.repository/   # Data access interfaces + implementations
│   ├── memory/                           # In-memory implementations
│   └── mysql/                            # MySQL implementations
├── com.adaptivereadinggame.service/      # Business logic (AdaptiveEngine, AssessmentService, etc.)
├── com.adaptivereadinggame.settings/     # Player preferences
├── com.adaptivereadinggame.uiux/         # Visual-only FXML prototype viewer
└── com.adaptivereadinggame.view/         # Custom JavaFX view components

src/main/resources/
├── com.adaptivereadinggame/style/        # CSS stylesheets
├── com.adaptivereadinggame/view/         # FXML screens (40+ UI/UX reference screens)
└── com.adaptivereadinggame/audio/        # Audio assets

src/test/java/                            # JUnit tests
```

## Code Conventions

### Naming
- **Classes:** PascalCase (`AccountGameService.java`)
- **Interfaces:** PascalCase (`AdaptiveEngine.java`, `DifficultyPolicy.java`)
- **Records:** PascalCase for domain models (`GameState.java`, `Account.java`)
- **Enums:** PascalCase (`Difficulty.java`, `ReadingLevel.java`)
- **Packages:** lowercase with dots (`com.adaptivereadinggame.service`)
- **Methods:** camelCase (`startChapter()`, `validateState()`)
- **Fields:** camelCase (`currentGame`, `hasUnsavedProgress`)
- **Constants:** UPPER_SNAKE_CASE (`STORY_LOG_LIMIT`)

### Architecture Patterns
- **Records for immutable data** - Domain models use Java records extensively
- **Repository pattern** - Clean separation between data access and business logic
- **Service layer** - Business logic in service classes, controllers are thin
- **Composition root** - `BackendContext` wires all repositories and services
- **Account-scoped operations** - All game operations require authenticated account
- **First-answer scoring** - Assessment service only records first attempt per question
- **FXML + Controller** - JavaFX views use FXML with controller classes

### Imports Order
1. Java standard library
2. Third-party libraries (Jackson, MySQL, JUnit)
3. Internal modules (same package first, then other packages)
4. JavaFX classes

### Error Handling
- Custom exceptions: `AuthException`, `ConfirmationRequiredException`, `RepositoryException`
- `IllegalStateException` for invalid operations
- `IllegalArgumentException` for invalid inputs
- `SecurityException` for account boundary violations

## Key Domain Models

### Core Entities
- **Account** - User account (id, username, displayName)
- **Student** - Account-linked profile (id, displayName, readingLevel, completedAttempts, accuracy)
- **Chapter** - Reading content with passage, questions, optional adaptive checkpoints
- **StoryNode** - Dialogue/narration node with speaker, text, choices, next actions
- **GameState** - Immutable progress snapshot (student, chapter, node, assessment, answers)
- **SaveSnapshot** - Persistable save slot with metadata
- **Question** - Comprehension question (id, prompt, choices, correctIndex, explanation)

### Enums
- **Difficulty** - EASY, MEDIUM, HARD (with `move(step)` for bounded transitions)
- **ReadingLevel** - BEGINNER, DEVELOPING, PROFICIENT, ADVANCED (legacy profile field)
- **DifficultyDecisionSource** - JAVA_RULES, MODEL, JAVA_FALLBACK, JAVA_GUARDRAIL

## Key Services

### AssessmentService
- Manages reading check sessions (in-memory, process-local)
- **First-answer only** - `submit()` records first choice, ignores retries
- `feedback()` reveals explanation after first answer stored
- `complete()` scores and applies difficulty policy

### AccountGameService
- Controller-facing boundary, all operations require authenticated account
- Manages `GameState` lifecycle (start, advance, choose, save, load, restore)
- Owns assessment sessions via `ownedSessions` map
- Validates all state transitions against repositories

### AdaptiveEngine / DifficultyPolicy
- `DifficultyPolicy` functional interface with `nextDifficulty(Context)`
- `BaselineDifficultyPolicy` - At least 80% raises one level, below 60% lowers one level, and intermediate scores keep the current level. These project thresholds are not educationally validated.
- `ModelAssistedDifficultyPolicy` - Optional local AI model with Java fallback
- Bounded transitions: max one level change per assessment

### BackendContext
- Composition root for swapping repository implementations
- `inMemory()` - Development mode
- `inMemoryWithModel()` - Development with AI model
- `mysql(MySqlConfig)` - Production MySQL

## FXML Screens (40 UI/UX Reference Screens)
Located in `src/main/resources/com/adaptivereadinggame/view/`:
- Authentication: Welcome, Sign up, Log in (with error states)
- Main Menu: No progress, Returning player
- Gameplay: Story dialogue, thoughts, narrative choices, reading checks
- Reading Check: Unanswered, selected, correct/incorrect feedback, final question
- Results: Normal, model fallback
- Menus: Pause, Save/Load slots, Settings, Confirmations
- Completion: Story complete, loading states, error states

Run UI/UX prototype: `mvn javafx:run -Puiux-prototype`

## Testing
- Unit tests in `src/test/java/com/adaptivereadinggame/backend/BackendSkeletonTest.java`
- Tests cover: assessment scoring, first-answer locking, difficulty policy, session scoping, save slots
- Run tests: `mvn test`

## Important Files for Context

### Entry Points
- `src/main/java/com/adaptivereadinggame/AdaptiveReadingGameApp.java` - Main application
- `src/main/java/com/adaptivereadinggame/uiux/UiuxPrototypeApp.java` - Visual prototype viewer

### Core Logic
- `src/main/java/com/adaptivereadinggame/service/AssessmentService.java` - Reading check scoring
- `src/main/java/com/adaptivereadinggame/service/AccountGameService.java` - Game state orchestration
- `src/main/java/com/adaptivereadinggame/service/DifficultyPolicy.java` - Difficulty adaptation
- `src/main/java/com/adaptivereadinggame/backend/BackendContext.java` - Dependency composition

### Navigation & UI
- `src/main/java/com/adaptivereadinggame/controller/AppNavigator.java` - Screen navigation, story log, audio
- `src/main/java/com/adaptivereadinggame/controller/GameplayController.java` - Story/assessment rendering

### Data Models
- `src/main/java/com/adaptivereadinggame/model/GameState.java` - Progress snapshot
- `src/main/java/com/adaptivereadinggame/model/Chapter.java` - Reading content variants
- `src/main/java/com/adaptivereadinggame/model/StoryNode.java` - Narrative nodes

## Development Commands
```bash
mvn compile                    # Compile
mvn test                       # Run tests
mvn javafx:run                 # Run main application
mvn javafx:run -Puiux-prototype # Run UI/UX prototype viewer
mvn exec:java                  # Run BackendDemo (exec.mainClass in pom.xml)
```

## Environment Variables
- `ARG_CONTENT_PACK` - Path to story content JSON (default: assets/content/hailey-investigation-complete.json)
- `ARG_STAGE_DIRECTIONS` - Path to character staging JSON
- `ARG_ASSET_DIR` - Base directory for artwork assets (default: assets)
- MySQL config via `MySqlConfig.fromEnvironment()`

## Key Architectural Decisions
1. **A reading level describes questions, never the story** - Only `LevelCopy` in the controller package may word a level. Story screens hide the level chip; reading-check screens show `Question level: X`. `ChapterSummary.adaptive` indicates whether a chapter changes question level at its checkpoints.
2. **No arbitrary student IDs in controllers** - All operations derive studentId from authenticated account
3. **First-answer immutability** - Assessment drafts store first answers; retries don't overwrite
4. **Story service owns narrative state** - `StoryService` handles advance/choose/restore logic
5. **Repository abstraction** - Swap in-memory/MySQL without changing services
6. **Visual prototype separate** - `UiuxPrototypeApp` loads FXML only, no game logic
7. **Font fallbacks** - Georgia → Times New Roman → Serif (handled in AppNavigator)

## Story Content Format
JSON story packs loaded via `StoryPackImporter` containing chapters, nodes, checkpoints, questions, and asset keys.
