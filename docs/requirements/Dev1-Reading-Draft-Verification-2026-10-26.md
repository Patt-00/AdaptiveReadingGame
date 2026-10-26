# Reading draft verification, 2026-10-26

This record describes the local, uncommitted `test-merge` worktree in `/home/patt/Desktop/AdaptiveReadingGame-Dev1`. The work remains local. No commit, merge, or push was performed.

## Playable content

The default pack is `assets/content/hailey-investigation-draft.json`, with chapter ID `hailey-investigation-reading-draft`. It contains 377 nodes and 21 nodes with authored narrative choices. Four checkpoint placements support Study-first and Hall-first routes, with two reading checks per playthrough. The Study and Hall each have Easy, Medium, and Hard question variants, giving six distinct question variants.

The [source snapshot and mapping](../../assets/content/source/README.md) trace the available script to the playable nodes. `SuppliedStoryboardTest` compares the narrative, choice labels, and question text with that snapshot. It also checks speaker attribution and that every referenced image exists. The documented exceptions cover the working Owen name, neutral player wording, and the Study prompt that names an absent clue.

The opening notice, reading passages, chapter title, and answer explanations identify the provisional keys. Story choices affect the narrative. Only the first submitted comprehension answer affects the score. Replaying the opposite route keeps that route's section score separate from the earlier route.

The ending screen displays the terminal node's text from the content pack. It states that the current draft ends after both investigations and that the culprit, antidote outcome, and conclusion remain unavailable. The killer-reveal image is outside the playable graph. Approved conclusion nodes can extend the pack; the [content guide](../../assets/README.md#add-the-conclusion) explains chapter versioning when learner records already exist.

## Reading presentation and storage

The live application has no voice button, media player, voice resolver, audio file rules, or JavaFX media dependency. V004's default-empty `voice_asset_key` column remains for schema compatibility. The application does not select or populate it. The disposable database contained no populated voice columns.

Long questions and answers keep their preferred text height and use scrolling. Feedback preserves answer readability while the first answer stays locked. Narration hides the empty character frame. The 40 `uiux-*.fxml` files remain visual references; their reference settings design is separate from the live reading settings.

MySQL verification used a disposable MySQL 8.4.11 container on loopback. It imported the entire current pack and restored answers, difficulty, route state, and terminal progress after creating new application contexts. Version-1 content and save compatibility remain covered by the existing tests.

## Verification results

The completed full run passed 167 Java cases. A later UI run passed all 19 UI cases after the final scrolling correction, including the additional Hard-layout case. Together, the reports cover all 168 distinct Java cases in the current test tree, with zero failures, errors, or skips.

| Check | Command or evidence | Result |
| --- | --- | --- |
| Full Java verification | `xvfb-run -a mvn verify -Darg.uiTests=true -Darg.mysqlTests=true -Darg.modelTests=true` | Passed, including four MySQL integration cases and the real-model smoke test. |
| Final UI and JAR build | `xvfb-run -a mvn verify -Dtest=Dev1UiTest -Darg.uiTests=true -Darg.mysqlTests=true` | Passed after the final layout changes; JAR rebuilt. |
| Python helper contract | From `model-tool/`: `.venv/bin/python -m unittest -v test_server` | Eight cases passed. |
| Source fidelity and graph | `SuppliedStoryboardTest`, `StoryPackLoaderTest`, and `AdaptiveEpisodeFlowTest` | Source content, finite paths, both orders, all starting difficulties, correct/incorrect outcomes, first-answer locking, and boundary saves passed. |
| Repository checks | `git diff --check` and `git lfs fsck --objects` | Passed. Image paths retain Git LFS attributes. |
| Desktop startup | `mvn -q javafx:run` from the project root | Loaded the default pack and opened the actual desktop window at Welcome. |

The test database variables were set as described in [the MySQL guide](../../database/README.md). Verification used `MAVEN_OPTS=-Xmx512m` and `-DargLine='-Xmx768m --enable-native-access=ALL-UNNAMED'`. The capture option was `-Darg.ui.captureDir=target/visual-qa-reading`.

Full-run logs and XML reports are under `target/verification-full-suite/`. The final UI evidence is under `target/verification-final-ui/`. Those directories are generated local artifacts. The JAR is `target/adaptive-reading-game-0.1.0-SNAPSHOT.jar`.

## Rendered review and limits

Rendered screens were inspected for the supplied character art, poisoning illustration, five authored choices, reading questions, feedback, and the final boundary. The Hard question was inspected at 800 by 600 pixels with a reading scale of 1.2. The assertions check full answer text and preferred height, rather than only the wrap property. Captures are under `target/visual-qa-reading/`.

Georgia is unavailable on this computer. The live UI explicitly reports its Times New Roman fallback. JavaFX native-access and Xvfb accessibility warnings were observed separately from test failures.

Java keeps the three-section AI trigger and validates recommendations. A fresh playthrough contains only two checkpoints, so Java rules can hold the level before the model is eligible. The separate smoke test used the bundled Qwen helper and verified an allowed, Java-checked decision. It does not establish reading-level accuracy.

The remaining author inputs are the approved conclusion, confirmation of the working character wording, and approval of the inferred question keys. The source also repeats the drinking and stasis-lock beats, which remain recorded for author review. Teacher and parent reporting are outside this student-game scope.
