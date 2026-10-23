# Dev 1: accounts, gameplay, and content integration

Guys, ito yung Dev 1 workstream. The backend implementation is consolidated in this working copy: accounts, MySQL adapters, generic story transitions, content-pack loader, model helper, and live gameplay flow. The approved story, reading questions, and character art are still separate. Follow [the content-pack guide](../../assets/README.md) when they are ready. Do not add pretend story content to make the app look finished.

## What works now, and what is waiting

| Piece | Current status | Owner of the next step |
| --- | --- | --- |
| Signup, normalized usernames, password checks, login, logout | Implemented with account/profile IDs matched; tested with in-memory and MySQL repositories | Use the current account service from the UI |
| Account-facing game access | `AccountGameService` uses the signed-in account; screens cannot supply a learner ID | UI/game screens call this service |
| Story/save shared names | `RepositoryStoryService` follows validated node graphs; choices update narrative state only | Load the approved content pack and verify all authored branches |
| Full Save, Load, Continue and cancel/overwrite protection | The live menu can save, load, continue, and return to the active story or assessment | Run the full flow with the approved story and a shared MySQL setup |
| First submitted answer | The live reading-check screen submits in order; the first answer is stored before feedback and cannot be replaced | Review the question copy and feedback after the source is provided |
| Reading results and next difficulty | Results display score, next level, and whether the model, Java rule, guardrail, or fallback decided it | Check the wording with the teacher or group before presentation |
| Login/menu/gameplay/results/settings UI connections | Instructions, account, story, assessment, result, session settings, and menu navigation screens use the same visual theme | Replace synthetic test fixtures with approved story, questions, and artwork |
| Account, chapter, answer, result, and save persistence | Implemented with MySQL adapters and an opt-in disposable-database test | Set up the database using `database/README.md` |
| Restoring an unfinished reading check | Separate Maven processes and a fresh JavaFX process restore the first answer, continue at question two, and reach results with test-only content | Repeat GUI login and Continue with the group's authored content and MySQL setup |
| Optional Python/Qwen model | Java policy adapter, loopback helper, and pinned LFS weight are in the local Dev1 working copy; the helper returns only a label | Start the helper separately; the model weight remains unpublished until the Dev1 changes are authorized for publication |

When no `ARG_DB_*` settings are present, the app starts in labeled in-memory development mode. When all three database settings are present, it uses MySQL after the migrations have run. Set `ARG_CONTENT_PACK` to import approved chapters during startup. With no pack, New Game reports that story content is not ready instead of inventing a chapter.

## Run it

JDK 21 or newer and Maven are required. Run from this repository root:

```sh
mvn test
mvn compile exec:java -Dexec.mainClass=com.adaptivereadinggame.backend.Dev1Demo
mvn javafx:run
```

For persistent storage, use the setup and migration steps in [the MySQL guide](../../database/README.md) before `mvn javafx:run`.

`Dev1Demo` demonstrates real signup/login/logout, matching account/profile IDs, account protection, and the honest no-content boundary. The older `mvn compile exec:java` remains a low-level baseline scoring demo; it is not a controller integration or player-facing game test.

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
| Current screen data | `currentGameView()` | Account-scoped story node, passage, chapter summary, and completed result; never question answer keys |
| New game | `startChapter(chapterId, discardConfirmed)` | Reads signed-in learner ID internally; requires confirmation if dirty |
| Narrative next/choice | `advanceStory()` / `chooseStory(choiceId)` | `RepositoryStoryService` follows prepared nodes; choices do not grade anything |
| Begin reading check | `startAssessment(chapterId)` | Requires active matching story chapter at a reading-check node; creates its stored draft before publishing the session |
| Unanswered questions | `questions(sessionId)` | Only the signed-in active session; public prompts exclude correct indexes and explanations |
| Submit | `submit(sessionId, questionId, selectedIndex)` | Storage returns original first answer; only that option is graded; a write failure gives no pretend feedback |
| Answer feedback | `feedback(sessionId, questionId)` | Available only after the first answer has been stored |
| Results | `complete(sessionId)` | Stores the score, next difficulty, and validated decision source; history reads the stored result |
| Current game/dirty check | `currentGame()` / `hasUnsavedProgress()` | Requires login; UI uses these before Save/Load/navigation |
| Save | `save(slot, overwriteConfirmed)` | Only slots 1-3; full snapshot; clear dirty flag only after the stored snapshot matches the request |
| Load | `load(slot, discardConfirmed)` | Empty slot returns empty without changing the game; cancel preserves everything |
| Continue | `continueGame(discardConfirmed)` | Newest full snapshot; lower slot wins timestamp ties; a basic checkpoint alone is not resumable |
| Slot listing | `snapshots()` | Only current account's full snapshots, sorted by slot |
| Reading history | `history()` | Only current account's actual completed results |
| Logout | `logout(discardConfirmed)` | Clears active story/question/account state, not stored records; no state change on cancelled confirmation |

Use `ConfirmationRequiredException` to ask the player before retrying an overwrite/discard action with `true`. Do not blindly pass `true` from a new screen. The current menu obtains the player's confirmation first. For account tasks, clear the password field immediately, do work off the JavaFX thread, and wipe the temporary `char[]` afterward.

The current menu's chapter picker uses `EASY` for a new learner and the latest stored result's `nextDifficulty` afterward. It does not let the player bypass that difficulty decision by listing every level. The live results screen lists only uncompleted chapters at that level and shows when none are loaded.

## Shared pieces: exact names

| File | What belongs here |
| --- | --- |
| `model/StoryChoice.java` | `choiceId`, `choiceText`, `nextNodeId` |
| `model/StoryNode.java` | `storyNodeId`, `speakerName`, `dialogueText`, `thought`, `storyChoices`, `nextNodeId`, `readingCheck`, `speakerAssetKey` |
| `model/Question.java` | `id`, `prompt`, `choices`, `correctChoiceIndex`, `explanation` |
| `model/AssessmentResult.java` | `attempt`, `assessedDifficulty`, `nextDifficulty`, `decisionSource` |
| `model/GameState.java` | `studentId`, `chapterId`, `storyNodeId`, `assessmentSessionId`, `questionIndex`, `firstAnswers` |
| `model/SaveSnapshot.java` | `checkpoint`, `gameState`; learner/chapter/node must match |
| `repository/RegistrationRepository.java` | `register(Account, String encodedVerifier, Student)` stores all three or none |
| `repository/StoryRepository.java` | Prepared chapter/node/choice reads |
| `repository/AssessmentDraftRepository.java` | New session draft, atomic first answer, authoritative draft read |
| `repository/SaveSnapshotRepository.java` | Full/basic save together, occupied snapshot list, empty optional for missing slots |
| `service/StoryService.java` | Story actions and restore boundary; `RepositoryStoryService` reads prepared nodes and includes `clearStateFor(studentId)` for logout |
| `service/GameStateCodec.java` | `encode(GameState)` and `decode(String)`; fixed version 1 only |

All Java paths above are under `src/main/java/com/adaptivereadinggame/`. Existing Account, Student, Chapter, Question, SaveGame, Attempt, Difficulty, and ReadingLevel names were preserved. The older four-band ReadingLevel remains separate from the three-level Difficulty. Do not convert them by ordinal.

## Story content handoff

The reusable story service and assessment restoration are implemented. The remaining content work does not require new story-specific Java classes.

1. Keep one stable ID for each chapter, question, story node, choice, and character asset key. Do not change an ID after learner records refer to that content.
2. Use the field names and rules in [the content-pack guide](../../assets/README.md). A chapter needs one reading-check node, one or more questions, and paths that reach the reading check without a loop.
3. Keep narrative choices separate from comprehension answers. The Java account coordinator stores the first submitted answer and does not let a later answer change the score.
4. Add artwork under `assets/characters/` and use the filename key in the matching story node. Do not place absolute paths in the content pack.
5. Provide the approved source document and image files when they are ready. The story can be converted into the pack and loaded through `ARG_CONTENT_PACK`; nobody needs to add story text to Java source.
6. The live screens are `how-to-play.fxml`, `gameplay.fxml`, `results.fxml`, and `analysis.fxml`. The 40 `uiux-*.fxml` files remain static references, not functional screen routes.

## MySQL adapter and content validation

The group has chosen **MySQL**. Core repository adapters and three additive schema migrations live in this branch under `repository/mysql/` and `database/migrations/`. The setup guide documents configuration and the disposable-database test. Do not put credentials in source control.

| Operation | MySQL behavior |
| --- | --- |
| Registration | One transaction for account + encoded credential + student; same UUID; rollback on any failure; unique normalized username |
| Duplicate registration | Throw `IllegalArgumentException` for a uniqueness rejection so Dev 1 can return the same username-unavailable message, including a racing insert |
| Username lookup | Match Java's `trim().toLowerCase(Locale.ROOT)` naming; do not silently introduce additional accent/case equivalences through a default SQL collation |
| Password storage | Store only the encoded verifier returned by PasswordHasher; no plaintext, no password in Account; preserve the verifier exactly |
| First answer | Atomic insert-once per session/question; preserve original on retries/races; verify owner/chapter/option membership; refuse completed-assessment writes |
| Draft creation | Only new empty sessions; duplicate must not delete or reset answers |
| Completed result | Keep the original result per learner + prepared chapter variant, including its decision source; repeating an insert returns it |
| Full save | Update basic checkpoint columns and full JSON in ONE transaction; failure preserves old valid save; exact matching returned snapshot |
| Save time | Dev 1 truncates `savedAt` to microseconds; store with matching precision (for example `DATETIME(6)`) under a consistent UTC conversion |
| Stored JSON | Use GameStateCodec and the checked-in [version-1 example](../../database/examples/game-state-v1.json); UUID strings, integer option indexes, no renamed fields |
| Missing rows | Return empty Optional/list, not invented accounts, saves, or questions |
| Ordering | Saves by slot; history oldest first then ID; Continue newest timestamp then lower slot |

The opt-in test runs against MySQL 8.4 and covers the listed storage behavior with synthetic records. See [the MySQL verification record](Dev1-MySQL-Verification-2026-10-22.md). When the group supplies its story data, Dev 1 should compare the schema assumptions with the approved content and identify any field mismatch before changing the shared records.

## How Dev 1 connects the finished pieces

Use `BackendContext.create(...)`. It accepts student, account, credential, registration, chapter, results, basic-save, full-save, story-node, and draft adapters; a story factory; PasswordHasher; DifficultyPolicy; and Clock. The same MySQL adapter object may implement several interfaces if its transaction boundaries support that. `storyFactory` receives the shared assessment service.

The application uses MySQL when all `ARG_DB_*` settings are present and otherwise labels its in-memory mode. Run the migration before the first MySQL launch. Controllers must continue using the account coordinator and must not contain SQL, grade answers, or read password verifiers.

## Next integration milestone

We can call the main loop integrated only after all of these work together:

1. Import the group's approved story, questions, and images through the versioned content pack.
2. Run the complete visible flow with that content: story choices, comprehension questions, feedback, result, and next chapter.
3. Launch the database-backed application with the group's MySQL setup, then sign up, log out, and log in again.
4. Save a full snapshot, close the application, reopen/login, and resume the same chapter/session without resetting answers.
5. Check overwrite, load, logout, quit, settings, and missing-next-chapter behavior with the approved screens.

Passing service tests alone is not proof of overall completion. The current policy is not evidence that the model improves reading outcomes; test the model with the approved story after the playable loop is connected.

## Verification and limits

Focused tests cover authentication, account isolation, immutable story choices, pack validation, first-answer-only scoring, settings, feedback, results, save/load/resume, and next-chapter navigation. Opt-in JavaFX tests use synthetic content only; they do not certify the group's final story or question copy.

The opt-in MySQL test uses a separate disposable database and synthetic records. It covers all migrations, result-source persistence, story transitions, answer persistence, save/load, and restoration after creating a new application context. The latest test record is [playable flow verification](Dev1-Playable-Flow-Verification-2026-10-22.md). The story, questions, art, parent/guardian and educator access rules, classroom review, and deployment to the group's database remain unverified. The original source ZIP and other local checkouts remain untouched. The changes in this checkout are local; no remote branch was pushed or merged.

See [the AI verification record](Dev1-AI-Verification-2026-10-22.md) for the Java, Python, and real-model smoke-test results.

Password hashing uses the JDK's PBKDF2-HMAC-SHA256 implementation with 600,000 iterations, random 16-byte salts, 32-byte hashes, and bounded versioned verifiers. This is the standard algorithm implemented by Java, not our own hash algorithm. References: [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Java cryptography reference](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html).

The reference uses Times New Roman titles and Georgia body/menu text. If Georgia is unavailable, this branch explicitly displays a Times New Roman fallback notice; if both are unavailable it discloses system Serif. No fonts are bundled or installed by this change.
