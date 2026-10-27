# Dev 1: accounts, gameplay, and content integration

Guys, ito yung Dev 1 workstream. This checkout connects accounts, MySQL, adaptive story checkpoints, the optional model helper, and the JavaFX game screens. The current story and art are a draft from the group folder, not the final story. The draft uses an unnamed servant and Prince Owen as the working name. The culprit, ending, and inferred answer keys still need confirmation. Follow [the content-pack guide](../../assets/README.md) when the group updates the content.

The supplied ZIP has a separate `GameFlowController` and local save store, so that controller is not used for runtime persistence. The active account and gameplay screens remain connected to `AccountController` and `GameplayController`; those controllers keep `AccountGameService` and MySQL as the owners of account, score, and save behavior.

## What works now, and what is waiting

| Piece | Current status | Owner of the next step |
| --- | --- | --- |
| Signup, normalized usernames, password checks, login, logout | Implemented with account/profile IDs matched; tested with in-memory and MySQL repositories | Use the current account service from the UI |
| Account-facing game access | `AccountGameService` uses the signed-in account; screens cannot supply a learner ID | UI/game screens call this service |
| Story and checkpoint routes | `RepositoryStoryService` follows validated node graphs; narrative choices do not score, while answer correctness follows the checkpoint outcome edge | Confirm the provisional route text and replace it with the final approved script |
| Full Save, Load, Continue and cancel/overwrite protection | The live menu can save, load, continue, and return to the active story or checkpoint result | Run the full flow with the final story and the group's MySQL setup |
| First submitted answer | The active checkpoint stores the first answer before feedback and does not let a retry replace it | Confirm the current question wording and inferred answer keys with the group |
| Reading results and next difficulty | Results show checkpoint score, next level, and the Java-validated decision source | Review the draft's one-question scores with the teacher before presentation |
| Story and art | The runtime loads the current FXML flow, character sprites, backgrounds, and poisoning illustration | Review character expressions and approve the working Owen name |
| Reading presentation | Written dialogue, questions, and supplied images; audio playback is removed | Review text wrapping and image choices |
| Account, story, answer, result, and save persistence | MySQL migrations V001 through V004 persist the episode, checkpoint variants, results, routes, and full saves | Set up a MySQL database using `database/README.md` |
| Restoring a checkpoint | In-memory tests and the disposable MySQL test cover route, difficulty, and first-answer restoration | Repeat the visible review flow with the group's database setup |
| Optional Python/Qwen model | Java owns scoring and validates the model's level suggestion; a Java fallback remains available | Start the helper separately when testing model-assisted decisions |

When no `ARG_DB_*` settings are present, the app starts in labeled in-memory development mode. When all three database settings are present, it uses MySQL after the migrations have run. The app loads `assets/content/hailey-investigation-draft.json` by default. Set `ARG_CONTENT_PACK` to load another local pack. The bundled pack stops before a final culprit or ending.

## Run it

JDK 21 or newer and Maven are required. Run from this repository root:

```sh
mvn test
mvn compile exec:java -Dexec.mainClass=com.adaptivereadinggame.backend.Dev1Demo
mvn javafx:run
```

For persistent storage, use the setup and migration steps in [the MySQL guide](../../database/README.md) before `mvn javafx:run`.

`Dev1Demo` demonstrates signup/login/logout, matching account/profile IDs, and account protection. The older `mvn compile exec:java` remains a low-level scoring demo; it is not the player-facing game flow.

For the optional Qwen helper, use the setup and fallback steps in [the model-tool guide](../../model-tool/README.md). Start that Python process separately; Java remains responsible for scoring and the final difficulty.

Normal `mvn test` skips opt-in native UI tests. To run those in a Linux virtual display:

```sh
xvfb-run -a mvn -Darg.uiTests=true -Dtest=Dev1UiTest test
```

On a machine with an available desktop display, run `mvn -Darg.uiTests=true -Dtest=Dev1UiTest test`. To capture the rendered screens, add `-Darg.ui.captureDir=target/visual-qa`. Synthetic UI fixtures and the startup content pack under `src/test/resources/` are test data, not playable group content.

The project now uses Jackson for the fixed save JSON format. Maven resolves it automatically. Old manual `javac` commands that include only JavaFX are no longer enough: they also need the Maven-resolved runtime dependencies on the classpath. Prefer Maven while integrating this branch.

## Dev 1's screen/service calls

Only call `BackendContext.accountGameService()` from new game/account controllers. The other context accessors exist for composition, internal services, old demos, and tests, not as an alternate route around login.

| Screen action | Java action | Important behavior |
| --- | --- | --- |
| Signup | `signup(username, displayName, password)` | Returns `Account`; remains logged out; show success and return to Login |
| Login | `login(username, password)` | Returns signed-in `Account`; failed login never replaces the active account |
| Account label | `currentAccount()` | Empty means logged out; Account has no password/verifier field |
| Chapter selection | `availableChapters(difficulty)` | Returns `ChapterSummary(id, title, difficulty)`, never answer keys |
| Current screen data | `currentGameView()` | Account-scoped story node, checkpoint passage, route state, chapter summary, and completed result; never question answer keys |
| New game | `startChapter(chapterId, difficulty, discardConfirmed)` | Reads signed-in learner ID internally; requires confirmation if dirty |
| Narrative next/choice | `advanceStory()` / `chooseStory(choiceId)` | `RepositoryStoryService` follows prepared nodes; choices do not grade anything |
| Begin reading check | `startAssessment(chapterId)` | Requires the active checkpoint node; selects the current Java-approved level and creates its stored draft before publishing the session |
| Unanswered questions | `questions(sessionId)` | Only the signed-in active session; public prompts exclude correct indexes and explanations |
| Submit | `submit(sessionId, questionId, selectedIndex)` | Storage returns original first answer; only that option is graded; a write failure gives no pretend feedback |
| Answer feedback | `feedback(sessionId, questionId)` | Available only after the first answer has been stored |
| Results | `complete(sessionId)` / `continueAfterCheckpoint()` | Stores the score and next difficulty, then follows the correct or incorrect story outcome without changing the score |
| Current game/dirty check | `currentGame()` / `hasUnsavedProgress()` | Requires login; UI uses these before Save/Load/navigation |
| Save | `save(slot, overwriteConfirmed)` | Only slots 1-3; full snapshot; clear dirty flag only after the stored snapshot matches the request |
| Load | `load(slot, discardConfirmed)` | Empty slot returns empty without changing the game; cancel preserves everything |
| Continue | `continueGame(discardConfirmed)` | Newest full snapshot; lower slot wins timestamp ties; a basic checkpoint alone is not resumable |
| Slot listing | `snapshots()` | Only current account's full snapshots, sorted by slot |
| Reading history | `history()` | Only current account's actual completed results |
| Logout | `logout(discardConfirmed)` | Clears active story/question/account state, not stored records; no state change on cancelled confirmation |

Use `ConfirmationRequiredException` to ask the player before retrying an overwrite/discard action with `true`. Do not blindly pass `true` from a new screen. The current menu obtains the player's confirmation first. For account tasks, clear the password field immediately, do work off the JavaFX thread, and wipe the temporary `char[]` afterward.

The main menu uses `EASY` when a learner has no history and the latest stored `nextDifficulty` for a later start. The route choice selects Study-first or Hall-first. Each checkpoint uses the Java-approved difficulty and its matching question variant. The Qwen policy waits for three consecutive sections at one level before asking the model; the current draft has only two checkpoint questions in one route.

## Shared pieces: exact names

| File | What belongs here |
| --- | --- |
| `model/StoryChoice.java` | `choiceId`, `choiceText`, `nextNodeId`, optional `routeId` |
| `model/StoryNode.java` | `storyNodeId`, `speakerName`, `dialogueText`, `thought`, choices, `nextNodeId`, checkpoint marker, optional asset keys, `storyComplete` |
| `model/StoryCheckpoint.java` | `id`, checkpoint node, passage, Easy/Medium/Hard question variants, correct and incorrect story destinations |
| `model/Question.java` | `id`, `prompt`, `choices`, `correctChoiceIndex`, `explanation`, optional checkpoint ID and difficulty |
| `model/AssessmentResult.java` | `attempt`, `assessedDifficulty`, `nextDifficulty`, `decisionSource` |
| `model/GameState.java` | learner, chapter, story node, selected route, active checkpoint and difficulty, last completed checkpoint, session, question index, first answers |
| `model/SaveSnapshot.java` | `checkpoint`, `gameState`; learner/chapter/node must match |
| `repository/RegistrationRepository.java` | `register(Account, String encodedVerifier, Student)` stores all three or none |
| `repository/StoryRepository.java` | Prepared chapter/node/choice reads; checkpoint definitions live with the chapter content |
| `repository/AssessmentDraftRepository.java` | New session draft, atomic first answer, authoritative draft read |
| `repository/SaveSnapshotRepository.java` | Full/basic save together, occupied snapshot list, empty optional for missing slots |
| `service/StoryService.java` | Story actions and restore boundary; `RepositoryStoryService` reads prepared nodes and includes `clearStateFor(studentId)` for logout |
| `service/GameStateCodec.java` | `encode(GameState)` writes version 2; `decode(String)` also reads version 1 saves |

All Java paths above are under `src/main/java/com/adaptivereadinggame/`. Existing Account, Student, Chapter, Question, SaveGame, Attempt, Difficulty, and ReadingLevel names were preserved. The older four-band ReadingLevel remains separate from the three-level Difficulty. Do not convert them by ordinal.

## Story content handoff

The runtime reads versioned story content instead of compiling authored dialogue into Java. Version 2 supports answer-specific outcomes and multiple checkpoints in one episode.

1. Keep one stable ID for each chapter, question, story node, choice, and character asset key. Do not change an ID after learner records refer to that content.
2. Use the field names and graph rules in [the content-pack guide](../../assets/README.md). Version 2 requires all three difficulty variants at each checkpoint and a finite path to a marked draft-completion node.
3. Keep narrative choices separate from comprehension answers. The Java account coordinator stores the first submitted answer, selects the correct or incorrect route, and never lets a retry change the score.
4. Add artwork under `assets/characters/`, `assets/scenes/`, or `assets/cg/` and use a stable filename key. Do not place absolute paths in the content pack.
5. Keep the player flow text-only. Do not add voice fields or audio controls.
6. The live screens are `how-to-play.fxml`, `gameplay.fxml`, `results.fxml`, and `analysis.fxml`. The 40 `uiux-*.fxml` files remain visual references, not separate runtime routes.
7. Keep the draft ending unresolved. Confirm the Owen/Ellis naming mismatch, the hard Study question's omitted clue, and all provisional answer keys with the group.

## MySQL adapter and content validation

The group has chosen **MySQL**. The adapters and four additive migrations live in `repository/mysql/` and `database/migrations/`. V004 stores checkpoint question variants, route state, and optional image keys. The setup guide documents configuration and the disposable-database test. Do not put credentials in source control.

| Operation | MySQL behavior |
| --- | --- |
| Registration | One transaction for account + encoded credential + student; same UUID; rollback on any failure; unique normalized username |
| Duplicate registration | Throw `IllegalArgumentException` for a uniqueness rejection so Dev 1 can return the same username-unavailable message, including a racing insert |
| Username lookup | Match Java's `trim().toLowerCase(Locale.ROOT)` naming; do not silently introduce additional accent/case equivalences through a default SQL collation |
| Password storage | Store only the encoded verifier returned by PasswordHasher; no plaintext, no password in Account; preserve the verifier exactly |
| First answer | Atomic insert-once per learner/chapter/checkpoint/question; preserve the original on retries/races; verify owner/checkpoint/option membership; refuse completed-assessment writes |
| Draft creation | Only new empty sessions; duplicate must not delete or reset answers |
| Completed result | Keep the original result per learner + chapter + checkpoint, including assessed level, next level, and decision source; repeating an insert returns it |
| Full save | Update basic checkpoint columns and full JSON in ONE transaction; failure preserves old valid save; exact matching returned snapshot |
| Save time | Dev 1 truncates `savedAt` to microseconds; store with matching precision (for example `DATETIME(6)`) under a consistent UTC conversion |
| Stored JSON | Encode GameState version 2 with route/checkpoint state; continue reading version-1 save files and integer answer indexes |
| Missing rows | Return empty Optional/list, not invented accounts, saves, or questions |
| Ordering | Saves by slot; history oldest first then ID; Continue newest timestamp then lower slot |

The opt-in test runs against a disposable MySQL 8.4 database. It covers version-1 content and version-2 checkpoint import, route and difficulty save/restore, and first-answer persistence. It does not certify the group's final story or shared database deployment.

## How Dev 1 connects the finished pieces

Use `BackendContext.create(...)`. It accepts student, account, credential, registration, chapter, results, basic-save, full-save, story-node, and draft adapters; a story factory; PasswordHasher; DifficultyPolicy; and Clock. The same MySQL adapter object may implement several interfaces if its transaction boundaries support that. `storyFactory` receives the shared assessment service.

The application uses MySQL when all `ARG_DB_*` settings are present and otherwise labels its in-memory mode. Run the migration before the first MySQL launch. Controllers must continue using the account coordinator and must not contain SQL, grade answers, or read password verifiers.

## Next integration milestone

The provisional review flow uses the currently supplied scenes. The available narrative is playable through both investigations. Its terminal results screen displays the explicit draft boundary. The remaining story inputs are:

1. Confirm the main character, Owen/Ellis naming, suspect, and final ending with the story author.
2. Confirm the six comprehension answer keys, including the Study hard question and the clue absent from its source.
3. Append the approved conclusion as content nodes and publish a new chapter ID when learner records already refer to the draft.
4. Run the full visible flow with the finalized story, art, and question pack.
5. Launch the database-backed app with the group's MySQL setup and verify a full restart during an active checkpoint.

Passing service tests alone is not proof of overall completion. The current policy is not evidence that the model improves reading outcomes; test the model with the approved story after the playable loop is connected.

## Verification and limits

The latest player-flow work is recorded in [gameplay repair and verification, 2026-10-27](Dev1-Gameplay-QA-2026-10-27.md). The build, automated checks, and visible Study-first desktop route passed with the provisional pack. This does not make the group's story, answer keys, ending or model quality final.

The current visual update is recorded in [gameplay visual polish and verification, 2026-10-27](Dev1-Visual-Polish-Verification-2026-10-27.md). It preserves the existing burgundy, gold, white, and serif direction, uses the mapped group art on a more cinematic story stage, and leaves the draft boundary unchanged.

The runtime UI accessibility checks are recorded in [runtime UI accessibility and consistency QA, 2026-10-27](Dev1-UI-Accessibility-QA-2026-10-27.md). The report covers disabled-button readability, keyboard focus, accessible labels, save-slot descriptions, and the desktop-test limits.

Focused tests cover authentication, account isolation, both adaptive route orders, difficulty variants, first-answer-only scoring, asset fallbacks, feedback, results, save/load/resume, and MySQL checkpoint persistence. Opt-in JavaFX tests still use test fixtures; they do not certify the group's final story or question copy.

The opt-in MySQL test uses a separate disposable database. It covers all migrations, version-2 checkpoint variants, route persistence, first-answer storage, save/load, and restoration after creating a new application context. The original source ZIP and other project checkouts remain untouched.

See [the AI verification record](Dev1-AI-Verification-2026-10-22.md) for the Java, Python, and real-model smoke-test results.

Password hashing uses the JDK's PBKDF2-HMAC-SHA256 implementation with 600,000 iterations, random 16-byte salts, 32-byte hashes, and bounded versioned verifiers. This is the standard algorithm implemented by Java, not our own hash algorithm. References: [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Java cryptography reference](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html).

The reference uses Times New Roman titles and Georgia body/menu text. If Georgia is unavailable, this branch explicitly displays a Times New Roman fallback notice; if both are unavailable it discloses system Serif. No fonts are bundled or installed by this change.
