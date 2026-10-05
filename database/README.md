# Database developer starting point

No database engine or JDBC driver has been selected, and no SQL implementation is included in this skeleton. Agree on the engine with the group before writing dialect-specific migrations. The in-memory repositories let the other developers continue meanwhile.

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
- Registration needs an agreed transaction boundary for account, credentials, and profile together. The current interfaces do not yet define this boundary.
- First-answer persistence needs an additional draft/session repository agreed with Dev 2; it is not implemented by the skeleton.
- Do not commit real credentials, local databases containing student data, or private sample data.

After choosing the engine, add versioned migration scripts, synthetic seed data, connection configuration documentation, and adapter integration tests. Verify constraints and rollback with a real test database, not just mocks.

See the [three-developer backend guide](../docs/requirements/Backend-Development-Guide.md) for ownership and integration order.
