# Playable flow verification, 2026-10-25

## Scope

This check covers the local `test-merge` worktree based on `bb2bd99`. The changes are uncommitted and have not been pushed. The source project ZIP and other project checkouts were left unchanged.

The checked group materials are the [shared art folder](https://drive.google.com/drive/u/0/folders/1oihsQAPN30tr8q1Eucr1dgo07W3n_s5l) and the [current storyboard document](https://docs.google.com/document/d/1XCnuefpntPIm9DIGByQVLk0237jc4Sa2aV80zqCjbm8/edit).

## Current content in the application

The app loads `assets/content/hailey-investigation-draft.json` by default. It provides a playable review slice with 38 story nodes, Study-first and Hall-first routes, and two reading checkpoints per route. Each checkpoint has Easy, Medium, and Hard question variants. Narrative choices affect the story route. Only the first submitted comprehension answer is stored and scored.

The pack condenses parts of the storyboard into screen-sized dialogue and evidence summaries. It is not a line-for-line import of the full document. The draft ends after the two investigation checkpoints. It does not reveal a culprit or select one of the four proposed ending combinations.

The supplied story still has open decisions and inconsistencies:

- The player is described as a servant, while the document still lists the main-character decision as open.
- The script uses Prince Owen in the early and Study sections, then calls him Prince Ellis in the Hall section.
- The Study hard question refers to a revoked knighthood decree that is not described among the supplied Study evidence. The pack uses the evidence that the document does describe.
- The six answer keys are working interpretations of the clues. The group and teacher have not approved them.
- The two ending CGs are not equivalent: the poisoning image is used, while the killer-reveal image is left out until the ending is agreed.

## FXML and art

The current project FXML was retained. The supplied ZIP contains 49 source FXML files; 34 match the current files, eight differ, and seven are absent from the current resource tree. The app's active `gameplay.fxml` supports character sprites, scene backgrounds, the poisoning illustration, and optional voice playback.

The shared art is organized under `assets/characters/`, `assets/scenes/`, and `assets/cg/`. The voice directory has no recordings yet. The Play Voice control appears only when the selected voice asset exists; dialogue text remains available without audio. Artwork and future audio paths have Git LFS rules.

## Verification results

| Check | Result |
| --- | --- |
| `xvfb-run -a mvn verify -Darg.mysqlTests=true -Darg.uiTests=true` | Passed; 139 tests, 0 failures, 0 errors, 1 skipped. The skipped test is the opt-in real Qwen model smoke test. |
| Disposable MySQL 8.4 integration | Passed; 3 tests, including adaptive checkpoint route, answer, and restore behavior. |
| JavaFX UI suite | Passed; 17 tests. The rendered Hailey scene and poisoning illustration were inspected. |
| `python3 -m unittest discover -s model-tool -v` | Passed; 8 tests. |
| `git diff --check` | Passed. |
| `git lfs fsck --objects` | Passed. |
| Default app launch under Xvfb | Loaded one chapter from the draft pack and remained open until the 25-second smoke-test timeout. |

The code compiles for Java 21. Maven ran with Java 25.0.4.1. JavaFX reported that Georgia is unavailable in this environment, so the UI displayed its Times New Roman fallback notice. The accessibility-bus warning from Xvfb did not fail the UI tests.

## Still needed

- Group approval of the player character, Owen/Ellis naming, question wording, and answer keys.
- The final culprit, ending text, and decision about when to use the killer-reveal image.
- Voice recordings and their mapping to dialogue nodes.
- A visible review using the finalized pack and the group's own MySQL setup.
- A real-model quality review with the approved story and questions. The automated suite verifies the helper contract and fallback; it does not establish reading-level accuracy.
