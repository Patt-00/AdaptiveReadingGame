## Status

Dev 2's tasks, what must be developed, and their status.

| File | Location | Status |
|---|---|---|
| `SampleChapter` (Chapter 0 data) | `repository/memory/` | Done |
| `InMemoryStoryRepository` | `repository/memory/` | Done |
| `InMemoryAssessmentDraftRepository` | `repository/memory/` | Not started |
| `StoryService` implementation | `service/` | Not started |
| Wiring into `BackendContext.create(...)` | `backend/` (Dev 1's file) | Not started, ask Dev 1 |
| Next-chapter selector | `service/` | Not started |
| `restoreState` / `restoreAssessment` | `service/` | Later phase |
| Automated tests (story flow, seed sanity, repository) | `src/test/.../backend/` | Not committed yet |

Verification so far: `SampleChapter` and `InMemoryStoryRepository` compile against Dev 1's model and repository classes, and plain-Java checks (not JUnit) covered the node graph and the repository rules. `mvn test` has not been run with these files in the project.

## How the pieces connect

```
Screens -> AccountGameService (Dev 1)
              |-> StoryService --------> StoryRepository <--- SampleChapter.nodes()
              |-> AssessmentDraftRepository (first answers)
              |-> AssessmentService --> ChapterRepository <--- SampleChapter.load(...)
                        |-> BaselineDifficultyPolicy
                        |-> AssessmentRepository (results)
```

Player flow: chapter list (EASY), start chapter, read story and make choices, reach the reading-check node, submit answers (first answer counts), complete, get the score and next difficulty. Dev 1's `AccountGameService` coordinates the assessment calls. `StoryService` only tracks the story position.

## Chapter 0 data (`SampleChapter`)

Title: Chapter 0: Person Goes to Class. Difficulty: EASY.

| Node ID | Speaker | Behavior |
|---|---|---|
| `intro` | (narration) | next: `wakeUpScene` |
| `wakeUpScene` | (narration) | next: `greetingFriend1` |
| `greetingFriend1` | (narration) | 3 choices (`greet-hello`, `greet-hi`, `greet-whatsup`), all to `greetingFriend2` |
| `greetingFriend2` | Friend | next: `mathIntro` |
| `mathIntro` | (narration) | next: `mathTeacher` |
| `mathTeacher` | Teacher | 3 choices (`math-2`, `math-3`, `math-idk`), all to `mathAnswer` |
| `mathAnswer` | Teacher | next: `lunchScene` |
| `lunchScene` | (narration) | 3 choices to `lunchFish`, `lunchChicken`, `lunchSandwich` |
| `lunchFish` / `lunchChicken` / `lunchSandwich` | (narration) | next: `endingScene` |
| `endingScene` | (narration) | reading check: no choices, no next node |

Questions (correct option index counts from 0):

| # | Question | Correct |
|---|---|---|
| 1 | Why did he wake up, eat breakfast, shower, and get dressed? | 1 (To go to school) |
| 2 | Who did he greet? | 1 (Friend) |
| 3 | Which class did they go to first? | 2 (Math class) |
| 4 | What did they do after math class? | 1 (Eat lunch) |
| 5 | What happened after lunch? | 0 (They went back to class, then went home) |

Story choices change the route but never the score. Only the five questions are scored.

## Rules every node must follow (enforced by `StoryNode`)

- A node does exactly one of: show choices, go to `nextNodeId`, or be a reading check. A node with none of these ends the path.
- Dialogue text is required. Narration uses an empty speaker (`""`), never `null`.
- With no next node, `nextNodeId` is `""`, never `null`.
- Choice IDs are unique within a node, and every choice needs a destination.
- Every destination must exist. `InMemoryStoryRepository.register(...)` rejects a story with a missing destination, a missing first node, a key that does not match the node's ID, or a duplicate chapter.

## IDs: current state and risk

The chapter ID and the five question IDs (`chapterId`, `q1Id` to `q5Id`) currently come from `randomId()`, so they are created once per program run and change on every restart. This is fine while everything is stored in memory. It breaks saves, results, replay protection and Dev 3's MySQL seed once storage is persistent, because stored rows would point at IDs that no longer exist. Before MySQL integration, switch to fixed IDs (the previous fixed values are kept in the header comment of `SampleChapter.java`) or name-derived IDs, and send the final list to Dev 3.

Node and choice IDs are plain text, unique per chapter, and must never change once chosen.
