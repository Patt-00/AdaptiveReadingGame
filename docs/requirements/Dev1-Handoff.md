# Dev 1: accounts and connecting our pieces

Guys, ito yung Dev 1 branch. The account code and shared names are now real Java files, not just suggestions in the puzzle guide. Dev 2 still fills in the story and reading-check restore code. Dev 3 still fills in MySQL storage. Do not replace somebody else's work to make an AI-generated demo look finished.

## What works now, and what is waiting

| Piece | Current status | Owner of the next step |
| --- | --- | --- |
| Signup, normalized usernames, password checks, login, logout | Implemented with account/profile IDs matched; tested with temporary storage | Dev 1 code is ready; Dev 3 connects MySQL |
| Account-facing game access | `AccountGameService` uses the signed-in account; screens cannot supply a learner ID | UI/game screens call this service |
| Story/save shared names | Four records and four repository interfaces now exist and compile | Dev 2 and Dev 3 fill in their implementations |
| Full Save, Load, Continue and cancel/overwrite protection | Implemented coordination; integration tests use small test-only story/storage doubles | Real gameplay and MySQL adapters still required |
| First submitted answer | Account coordinator stores the first answer before returning feedback; no bare/no-story assessment route | Dev 3 implements the atomic draft write; Dev 2 restores the same session |
| Reading results and next difficulty | Existing Java baseline retained; history displays actual stored results | Dev 2 supplies the playable chapter and results navigation |
| Login/menu/save-slot/history/logout/quit UI connections | Wired in the main checkout's existing visual style; no story or settings redesign | Full groupmate UI/story integration is still separate |
| Restart/reopen persistence | NOT implemented by the temporary adapters | Dev 3: MySQL |
| Restoring an unfinished reading check after restart | Signature exists, deliberately fails until implemented | Dev 2: `AssessmentService.restoreAssessment` and coordinated story restore |
| Optional Python/Qwen model | Not merged, not required for this branch | Later, after the Java baseline works |

The default app starts with authentication and uses **temporary in-memory storage**. Accounts, saves, and results disappear when this process closes. There is no automatic switch to a pretend MySQL connection. New Game reports that story content is not connected instead of inventing a playable chapter.

## Run it

JDK 21 or newer and Maven are required. Run from this repository root:

```sh
mvn test
mvn compile exec:java -Dexec.mainClass=com.adaptivereadinggame.backend.Dev1Demo
mvn javafx:run
```

`Dev1Demo` demonstrates real signup/login/logout, matching account/profile IDs, account protection, and the honest unavailable-story boundary. The older `mvn compile exec:java` remains a low-level baseline scoring demo; it is NOT a controller integration or durable reading-check resume test.

Normal `mvn test` skips opt-in native UI tests. To run those in a Linux virtual display:

```sh
xvfb-run -a mvn -Darg.uiTests=true -Dtest=Dev1UiTest test
```

On a machine with an available desktop display, run `mvn -Darg.uiTests=true -Dtest=Dev1UiTest test`. To capture the rendered screens, add `-Darg.ui.captureDir=target/visual-qa`. UI fixtures and temporary test stories are not shipped playable content.

The project now uses Jackson for the fixed save JSON format. Maven resolves it automatically. Old manual `javac` commands that include only JavaFX are no longer enough: they also need the Maven-resolved runtime dependencies on the classpath. Prefer Maven while integrating this branch.

## Dev 1's screen/service calls

Only call `BackendContext.accountGameService()` from new game/account controllers. The other context accessors exist for composition, internal services, old demos, and tests—not as an alternate route around login.

| Screen action | Java action | Important behavior |
| --- | --- | --- |
| Signup | `signup(username, displayName, password)` | Returns `Account`; remains logged out; show success and return to Login |
| Login | `login(username, password)` | Returns signed-in `Account`; failed login never replaces the active account |
| Account label | `currentAccount()` | Empty means logged out; Account has no password/verifier field |
| Chapter selection | `availableChapters(difficulty)` | Returns `ChapterSummary(id, title, difficulty)`, never answer keys |
| New game | `startChapter(chapterId, discardConfirmed)` | Reads signed-in learner ID internally; requires confirmation if dirty |
| Narrative next/choice | `advanceStory()` / `chooseStory(choiceId)` | Dev 2 controls transitions; these do not grade anything |
| Begin reading check | `startAssessment(chapterId)` | Requires active matching story chapter at a reading-check node; creates its stored draft before publishing the session |
| Unanswered questions | `questions(sessionId)` | Only the signed-in active session; public prompts exclude correct indexes |
| Submit | `submit(sessionId, questionId, selectedIndex)` | Storage returns original first answer; only that option is graded; a write failure gives no pretend feedback |
| Results | `complete(sessionId)` | Existing first-completion Java result; history uses its actual score/next difficulty |
| Current game/dirty check | `currentGame()` / `hasUnsavedProgress()` | Requires login; UI uses these before Save/Load/navigation |
| Save | `save(slot, overwriteConfirmed)` | Only slots 1–3; full snapshot; clear dirty flag only after the stored snapshot matches the request |
| Load | `load(slot, discardConfirmed)` | Empty slot returns empty without changing the game; cancel preserves everything |
| Continue | `continueGame(discardConfirmed)` | Newest full snapshot; lower slot wins timestamp ties; a basic checkpoint alone is not resumable |
| Slot listing | `snapshots()` | Only current account's full snapshots, sorted by slot |
| Reading history | `history()` | Only current account's actual completed results |
| Logout | `logout(discardConfirmed)` | Clears active story/question/account state, not stored records; no state change on cancelled confirmation |

Use `ConfirmationRequiredException` to ask the player before retrying an overwrite/discard action with `true`. Do not blindly pass `true` from a new screen. The current menu obtains the player's confirmation first. For account tasks, clear the password field immediately, do work off the JavaFX thread, and wipe the temporary `char[]` afterward.

The current menu's chapter picker uses `EASY` for a new learner and the latest stored result's `nextDifficulty` afterward. It does not let the player bypass that difficulty decision by listing every level. Dev 2 still supplies chapter sequencing and the actual next-chapter/results destination.

## Shared pieces: exact names

| File | What belongs here |
| --- | --- |
| `model/StoryChoice.java` | `choiceId`, `choiceText`, `nextNodeId` |
| `model/StoryNode.java` | `storyNodeId`, `speakerName`, `dialogueText`, `thought`, `storyChoices`, `nextNodeId`, `readingCheck` |
| `model/GameState.java` | `studentId`, `chapterId`, `storyNodeId`, `assessmentSessionId`, `questionIndex`, `firstAnswers` |
| `model/SaveSnapshot.java` | `checkpoint`, `gameState`; learner/chapter/node must match |
| `repository/RegistrationRepository.java` | `register(Account, String encodedVerifier, Student)` stores all three or none |
| `repository/StoryRepository.java` | Prepared chapter/node/choice reads |
| `repository/AssessmentDraftRepository.java` | New session draft, atomic first answer, authoritative draft read |
| `repository/SaveSnapshotRepository.java` | Full/basic save together, occupied snapshot list, empty optional for missing slots |
| `service/StoryService.java` | Dev 2 implementation boundary; includes `clearStateFor(studentId)` for logout |
| `service/GameStateCodec.java` | `encode(GameState)` and `decode(String)`; fixed version 1 only |

All Java paths above are under `src/main/java/com/adaptivereadinggame/`. Existing Account, Student, Chapter, Question, SaveGame, Attempt, Difficulty, and ReadingLevel names were preserved. The older four-band ReadingLevel remains separate from the three-level Difficulty. Do not convert them by ordinal.

## Dev 2: plug in this piece next

1. Implement `StoryService` in a new class in `service/`, using the prepared `StoryRepository`. Use the shared records directly. Do not add a different GameState class.
2. Story start/advance/choice methods return the correct current GameState. Before the account coordinator begins an assessment, the current node must have `readingCheck = true`. Story choices never change scores.
3. The account coordinator owns its active question snapshot after `startAssessment` and `submit`. Do not route a reading-check session back into story choices or reset its answers. After results, select the next prepared chapter through the agreed next-difficulty rule.
4. Fill in the declared `AssessmentService.restoreAssessment(GameState)`. It currently throws intentionally. Validate the learner/chapter/session and read stored first answers; keep the original session UUID; reject completed results and mismatched drafts. Never create a fresh scored attempt to make resume appear to work.
5. Implement `StoryService.restoreState` so story and its assessment are validated/restored together BEFORE publishing a replacement. If restore fails, leave the previous state untouched. Use the authoritative stored draft, not older snapshot answers.
6. Wire your StoryService through the factory supplied to `BackendContext.create`. That factory receives the SAME AssessmentService used by the account coordinator. Do not construct a second independent AssessmentService inside your story implementation.
7. Keep existing first-answer grading, repeated completion protection, and Java baseline. Model code stays on its separate branch until the baseline is integrated.

## Dev 3: MySQL piece next

The group has chosen **MySQL**. This branch does not choose your schema migration tool, add a JDBC driver, install a server, or commit credentials. Implement the existing and new repository interfaces in `repository/jdbc/` with the agreed names.

| Operation | Must be true in MySQL |
| --- | --- |
| Registration | One transaction for account + encoded credential + student; same UUID; rollback on any failure; unique normalized username |
| Duplicate registration | Throw `IllegalArgumentException` for a uniqueness rejection so Dev 1 can return the same username-unavailable message, including a racing insert |
| Username lookup | Match Java's `trim().toLowerCase(Locale.ROOT)` naming; do not silently introduce additional accent/case equivalences through a default SQL collation |
| Password storage | Store only the encoded verifier returned by PasswordHasher; no plaintext, no password in Account; preserve the verifier exactly |
| First answer | Atomic insert-once per session/question; preserve original on retries/races; verify owner/chapter/option membership; refuse completed-assessment writes |
| Draft creation | Only new empty sessions; duplicate must not delete or reset answers |
| Completed result | Keep the original result per learner + prepared chapter variant; repeating an insert returns it |
| Full save | Update basic checkpoint columns and full JSON in ONE transaction; failure preserves old valid save; exact matching returned snapshot |
| Save time | Dev 1 truncates `savedAt` to microseconds; store with matching precision (for example `DATETIME(6)`) under a consistent UTC conversion |
| Stored JSON | Use GameStateCodec and the checked-in [version-1 example](../../database/examples/game-state-v1.json); UUID strings, integer option indexes, no renamed fields |
| Missing rows | Return empty Optional/list, not invented accounts, saves, or questions |
| Ordering | Saves by slot; history oldest first then ID; Continue newest timestamp then lower slot |

Atomic write behavior is a repository responsibility. SQL errors must not become fake successful values. Run failure, rollback, duplicate, account-isolation, and close/reopen persistence tests before declaring MySQL complete.

## How Dev 1 connects the finished pieces

Use `BackendContext.create(...)`. It accepts student, account, credential, registration, chapter, results, basic-save, full-save, story-node, and draft adapters; a story factory; PasswordHasher; DifficultyPolicy; and Clock. The same MySQL adapter object may implement several interfaces if its transaction boundaries support that. `storyFactory` receives the shared assessment service.

The application currently explicitly calls `BackendContext.inMemory()`. Replace that wiring with the approved MySQL adapters only after their tests pass. Controllers must continue using the account coordinator and must not contain SQL, grade answers, or read password verifiers.

## First integration milestone for the 70% target

We can call the main loop integrated only after all of these work together:

1. Signup/login against MySQL, then log out and back in.
2. One real story chapter with one legal narrative choice.
3. Separate reading-check questions with first-submit-only feedback and real results.
4. Save a full snapshot, close the application, reopen/login, and resume the same chapter/session without resetting answers.
5. Cancel overwrite/load/logout/quit and keep current progress unchanged.
6. Show Java's next difficulty and select its prepared next chapter.

Passing Dev 1 tests alone is not proof of 70% overall completion. The optional model and final UI polish come after the first integrated playable loop.

## Verification and limits

Focused auth/shared/account-coordinator tests cover invalid input, normalized duplicates, account IDs, invalid credentials, first-answer storage failure, student isolation, full saves, overwrite/load/logout cancellation, Continue ordering, old-save answer locking, and completed-assessment rejection. Opt-in JavaFX tests verify rendered account forms, signup success, login errors, menu, empty save slots, stored-results history, and logout/quit decisions.

The test story and fake storage used by account-coordinator tests live only in `src/test/`. They prove the Dev 1 connections against contracts, not finished Dev 2 gameplay or durable MySQL storage. The model helper was not merged. The original source ZIP and groupmate code were not changed. Main and other remote branches are not part of this push.

Password hashing uses the JDK's PBKDF2-HMAC-SHA256 implementation with 600,000 iterations, random 16-byte salts, 32-byte hashes, and bounded versioned verifiers. This is the standard algorithm implemented by Java, not our own hash algorithm. References: [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [Java cryptography reference](https://docs.oracle.com/en/java/javase/21/security/java-cryptography-architecture-jca-reference-guide.html).

The reference uses Times New Roman titles and Georgia body/menu text. If Georgia is unavailable, this branch explicitly displays a Times New Roman fallback notice; if both are unavailable it discloses system Serif. No fonts are bundled or installed by this change.
