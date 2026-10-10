# Database developer starting point

The group has chosen **MySQL**. No JDBC driver, schema, or SQL adapter is supplied by this Dev 1 branch; those remain Dev 3's piece. The in-memory repositories are temporary test/development helpers, not persistence. See [the current handoff](../docs/requirements/Dev1-Handoff.md) for the fixed shared contracts and integration steps.

## Logical data to design

| Entity | Data and constraints to coordinate |
| --- | --- |
| Account | UUID, unique normalized username, display name; public account records contain no verifier |
| Credential | Account foreign key, encoded password verifier; backend-only access |
| Student profile | Linked account/student UUID; canonical gameplay difficulty/profile migration agreed with Dev 1 |
| Chapter / variant | Stable logical-story and variant identifiers, title, passage, EASY/MEDIUM/HARD, node relationships |
| Question / choice | Stable question ID, chapter/variant FK, prompt, ordered choices, valid correct-choice index |
| First-answer draft | Student, assessment session, question, first selected choice; persist once before feedback |
| Completed assessment | Attempt UUID, student/chapter IDs, correct/total counts, assessed/next difficulty, completion time; atomic insert-once rule |
| Save slot | Student ID + slot unique key, chapter/variant, stable story-node ID, difficulty, save time; additional state for durable resume agreed with Dev 2 |

`AssessmentRepository` currently deduplicates by student and chapter variant. Decide the logical-chapter replay policy before finalizing uniqueness constraints across difficulty variants. `Attempt.materialId` is the current Java field for the chapter identifier.

## Repository contracts

Implement `StudentRepository`, `AccountRepository`, `CredentialRepository`, `ChapterRepository`, `AssessmentRepository`, and `SaveGameRepository` under a storage-specific package such as `com.adaptivereadinggame.repository.jdbc`.

- `saveCompleted` must atomically return the original completion on duplicates, including races.
- `historyFor` returns chronological results, scoped to one student.
- Save-slot writes overwrite only the specified student's slot.
- Empty lookups return `Optional.empty()`.
- `RegistrationRepository.register` now defines the atomic account/credential/profile boundary; implement it as one MySQL transaction.
- `AssessmentDraftRepository` now defines first-answer storage; implement atomic insert-once and read the original on repeats.
- `StoryRepository` provides prepared nodes and choices. `SaveSnapshotRepository` writes full GameState JSON and checkpoint details together. Use the [version-1 example](examples/game-state-v1.json) and `GameStateCodec` without renaming fields.
- Do not commit real credentials, local databases containing student data, or private sample data.

Add versioned MySQL migration scripts, synthetic seed data, connection configuration documentation, and adapter integration tests. Verify constraints and rollback with a real test database, not just mocks.

See the [three-developer backend guide](../docs/requirements/Backend-Development-Guide.md) for ownership and integration order.
