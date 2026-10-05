# Backend skeleton and three-developer handoff

## Architecture and current scope

This is a JavaFX desktop application. Its backend is currently **Java application services running inside the same JVM**, not a REST server. Keep this dependency direction:

```text
JavaFX controller -> service -> repository interface -> storage adapter
                       |
                       +-> Java difficulty policy -> optional model tool later
```

Services and repositories must not depend on JavaFX. Controllers must not execute SQL, hash passwords, grade answers, or decide difficulty. `BackendContext` is the composition root for wiring dependencies. The existing screens are unchanged and do not yet use this new context.

The skeleton includes:

- Account, chapter, assessment-result, difficulty, and checkpoint models.
- Authentication and password-hasher **interfaces only**.
- Repository contracts for accounts, credentials, chapters, assessment history, saves, and the existing students.
- Development-only in-memory student and game repositories.
- Working first-answer assessment, baseline difficulty, student-scoped history, and three save slots.
- A console demo and automated tests. No Python dependency is required to run this baseline.

No login implementation, database driver, SQL schema, REST routes, network authentication, or persistent storage is supplied. No AI code from the feature branch is merged into main by this change.

## Run and verify

From the repository root, with JDK 21+ and Maven:

```sh
mvn test
mvn compile exec:java
```

Expected console demo: score `1/1`, next difficulty `HARD`, repeated completion returns the same result, one saved slot, one completed history entry. Data disappears when the process exits.

`mvn javafx:run` still opens the existing menu, not this console demo. Compile/test checks do not prove the JavaFX screens are wired or visually tested.

## Ownership

| Developer | Main ownership | Starting files | First deliverable |
| --- | --- | --- | --- |
| Backend Dev 1 — you | Authentication, current-account lifecycle, account-facing save/load/continue coordination, composition | `service/AuthService.java`, `service/PasswordHasher.java`, `service/ProgressService.java`, `backend/BackendContext.java` | Tested signup/login/logout with an authenticated account facade; then account-scoped Continue/save/load |
| Backend Dev 2 | Chapter/story flow, comprehension checks, scoring, results, Java difficulty decisions, eventual model integration | `service/AssessmentService.java`, `service/DifficultyPolicy.java`, `service/BaselineDifficultyPolicy.java`, `model/Chapter.java` | One chapter playable through service calls, with first-answer scoring and stable results |
| Dev 3 — database | Database selection, schema/migrations, sample data, JDBC/storage adapters, persistence tests | `database/`, repository contracts; create `repository/jdbc/` after database choice | First persisted chapter read, followed by account/student registration and durable assessment/save operations |

Split by feature ownership, not “one person writes models and another writes controllers.” Each backend developer owns tests for their feature. Dev 3 writes adapter integration tests that enforce the same repository contracts as the in-memory adapters.

Shared models, repository signatures, and `BackendContext` changes need coordination. Do not independently rename fields or change identifier semantics. UI teammates consume agreed service contracts and handle screen display/navigation, not business rules.

## Backend Dev 1: your work

1. Implement `PasswordHasher` using a vetted password-hashing implementation and encoded verifier format. Never store plaintext passwords, log passwords, or expose verifiers in `Account`/UI DTOs. The interface is not a security implementation.
2. Implement `AuthService`: normalize usernames consistently, enforce signup validation, reject duplicate usernames, verify credentials on login, and keep the current account private to the application session.
3. Coordinate atomic registration with Dev 3: account, credential, and student profile must be created together or rolled back together. The separate repository interfaces currently do **not** provide a transaction boundary; agree on an atomic registration/unit-of-work contract before wiring those writes.
4. Establish that the account ID and student ID refer to the same learner (or introduce an explicit mapping consistently). The proposed simplest convention is the same UUID for both.
5. Add an account-facing facade that derives student IDs from `currentAccount()`. Never let controllers select arbitrary student IDs. The current low-level assessment/progress services validate existence, not authorization; knowing a session UUID is not authentication.
6. On logout, clear that account's active assessment sessions via `clearSessionsFor`, clear selected save/game state, and return to Login.
7. Coordinate Continue and explicit save/load with `ProgressService`. Empty slots are normal; obtain UI confirmation before overwriting an occupied slot.

Acceptance tests: signup success and invalid input, duplicate normalized username, wrong password, unauthenticated access denied, logout clears account/session state, another account's saves/history cannot be accessed through the authenticated facade. Authentication is not complete until those tests pass.

## Backend Dev 2: gameplay and assessment

1. Create a small prepared chapter/story with stable node IDs. Add a story-flow service with a clear current node and legal choice transitions; personal story choices must not contribute to comprehension scores.
2. Use `AssessmentService.start`, `questions`, `submit`, and `complete` for comprehension checks. Public question prompts omit answer-key indexes. A second submission does not replace the first answer.
3. Retain the same completed result on repeated completion or a replay of the same chapter variant. Coordinate logical chapter IDs and variant IDs with Dev 3 so replay cannot bypass scoring protections by changing IDs.
4. Select the next prepared variant from Java's difficulty result. Baseline: at least 80% increases one level, 60% to below 80% keeps, below 60% decreases; clamp to EASY–MEDIUM–HARD. These rules are experimental, not validated educational assessment.
5. Connect the results view to the persisted assessment result; do not recompute scores in controllers. Derive history/progress from assessment records, not the legacy dashboard's demo counters.
6. Integrate AI only after the baseline works. The existing prototype is on `feature/java-owned-qwen-prototype`. Adapt its Java decision layer behind `DifficultyPolicy`; use context student/chapter IDs to query history, but send only numeric summaries to Python. Do not merge the whole branch blindly over newer main.
7. Keep model/network work off the JavaFX thread. Before model integration, replace the coarse synchronized completion critical section with a per-session/in-flight design that does not block all other sessions during inference while still preventing duplicate completion.

Acceptance tests: valid story transitions; unknown node/choice rejection; first-answer-only grading; incomplete assessment rejection; idempotent completion/replay; difficulty bounds; model unavailable/invalid/timeout cases once integrated.

## Dev 3: database and persistence

1. Agree on the database engine before adding a driver or dialect-specific SQL. Start from [database/README.md](../../database/README.md).
2. Build versioned schema scripts and repeatable sample chapter/question data. Use stable IDs, foreign keys, unique normalized usernames, unique student/save-slot pairs, and a uniqueness rule for scored chapter completion.
3. Implement repository interfaces in a storage-specific package. Keep connection credentials/configuration outside committed source; services must stay storage-independent.
4. Implement `AssessmentRepository.saveCompleted` atomically: concurrent/repeated insert must return the original result rather than replacing scores. Preserve assessed and next difficulty with the completion.
5. Persist saves by student and slot. Return empty `Optional` for absent data, not fake records. Ensure history is ordered oldest first (timestamp, then ID for ties).
6. Coordinate registration transactions with Dev 1. Coordinate persisted first-answer drafts with Dev 2 **before feedback is shown**; the skeleton keeps active answers in memory, so process restart currently loses unfinished assessments.
7. Test persistence across process/repository recreation, account isolation, duplicate usernames/completions, rollback, referential integrity, and overwrite semantics.

## Contract notes and known gaps

- `Difficulty` is the new gameplay enum (EASY, MEDIUM, HARD). Existing `ReadingLevel` still has four legacy dashboard bands; it remains unchanged to avoid breaking the dashboard. Do not map enums by ordinal. Plan a coordinated legacy-model migration.
- `Student.readingLevel`, `completedAttempts`, and `accuracy` are still legacy/demo fields. The new services do not update those counters. New results/history come from `AssessmentRepository`; decide on canonical profile fields before database mapping.
- `Attempt.materialId` refers to the new chapter ID when used by `AssessmentService`; the name is retained for compatibility with the original model.
- A `Chapter` currently represents one prepared reading variant, without logical-story/variant grouping. Add that grouping before implementing replay/variant progression across difficulty levels.
- `SaveGame` is only a checkpoint snapshot. Story-node existence/transition validation, choices, settings, active assessment snapshots, and durable resume are future work. Three slots are a configurable product assumption, not a teacher requirement.
- In-memory services are stateless between process launches; discarded unfinished sessions lose first answers. They are development scaffolding, not production-ready authentication or assessment storage.

## Parallel workflow

After this skeleton lands on main, work in separate branches:

```sh
# Only pull when your worktree is clean; commit or preserve your changes first.
git switch main
git pull --ff-only origin main
git switch -c feature/auth-and-account-flow
```

Use `feature/chapter-assessment-flow` for Dev 2 and `feature/database-persistence` for Dev 3. Start a new branch from updated main, not from someone else's feature branch. Submit small pull requests with tests and an explanation of contract changes; do not force-push main. Coordinate shared schema/model changes before coding against them.

Suggested integration order: agreed models/IDs -> persisted chapter read -> atomic signup/login -> one chapter assessment -> persisted first answers/results -> authenticated saves/Continue -> optional Qwen adapter.
