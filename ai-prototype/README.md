# Java-owned adaptive difficulty prototype

This is a standalone, working console prototype for AdaptiveReadingGame. **Java owns every application policy. Python is only a local pretrained-model inference tool.** It does not change the current JavaFX screens, authentication, persistence, or the application's placeholder `AdaptiveEngine`.

## Responsibilities

| Java | Python model tool |
| --- | --- |
| Check first-attempt comprehension selections against answer keys | Load Qwen and its tokenizer |
| Calculate section scores, select comparable history, summarize accuracy and trend | Score the three allowed output labels using the model |
| Build the instruction prompt | Return the model's suggested label and model identity |
| Validate responses, apply safeguards, select the final difficulty | Return service errors; no fallback difficulty decision |
| Handle unavailable/loading/busy/invalid/timeout responses with Java baseline rules | No player accounts, game state, raw answers, database, or history API |

Java sends a prompt to `POST http://127.0.0.1:8765/infer`. Python responds with `suggested_action`; Java returns a `Decision` containing the actual `nextDifficulty`. No cloud inference API or API key is required. The first model load requires internet access to download approximately 1 GB of weights; CPU float32 inference uses additional RAM (allow several GB free). Later loads reuse the Hugging Face cache.

## Requirements

- JDK 21 or later and Maven. `mvn -version` must show Java 21 or later.
- A Python version supported by the pinned PyTorch release on your platform. The verification environment used Python 3.14 and CPU PyTorch; Python 3.11+ is a starting point, but check wheel availability for your OS/CPU.
- Two terminals: keep the model tool running in one and run the Java demo in the other.

Dependencies are isolated in `ai-prototype/pom.xml` and `model-tool/requirements.txt`. The root JavaFX POM is unchanged.

## Start the model tool

Run these commands from the repository's `ai-prototype` directory.

### Linux / macOS

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r model-tool/requirements.txt
.venv/bin/python model-tool/server.py
```

### Windows PowerShell

```powershell
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r model-tool/requirements.txt
.\.venv\Scripts\python.exe model-tool/server.py
```

No activation step is necessary. If PyTorch has no matching wheel, use a supported Python version and consult [official PyTorch installation instructions](https://pytorch.org/get-started/locally/). Do not silently substitute an untested dependency version; rerun the tests and real-model demo after changing dependencies.

Check `http://127.0.0.1:8765/health` in a browser. Wait for `"ready": true`. While weights are loading or if the model fails to load, Java will use the explicitly labeled baseline fallback. Stop the tool with Ctrl+C. You may choose another port with `--port 8766`.

## Run Java

In a second terminal, also in `ai-prototype`:

```sh
mvn test
mvn compile exec:java
```

The demo prints four example histories and the Java decision for each. It then demonstrates cold start. To require genuine model inference (fail if any case uses fallback):

```sh
mvn -DrequireModel=true compile exec:java
```

For an alternate local port:

```sh
mvn compile exec:java -Dexec.args="http://127.0.0.1:8766/infer"
```

To demonstrate the fallback, stop Python and rerun the ordinary Java demo. Output will identify `source: rules_fallback`. Running the root application's `mvn javafx:run` does not automatically start or integrate this prototype.

## Java policy

- Prototype levels: `EASY`, `MEDIUM`, `HARD`.
- Keep only the most recent three consecutive completed sections at the current difficulty (history oldest first, maximum 30 entries). Encountering another difficulty resets the comparable run.
- Until three comparable sections exist: keep the current difficulty, `source: cold_start`, and do not call Python.
- Java computes the mean of section percentages, not a question-weighted aggregate. An improvement/decline is an oldest-to-latest difference greater than 10 percentage points; otherwise the trend is steady.
- For a model suggestion to increase: both mean and latest accuracy must be at least 80%.
- For a model suggestion to decrease: both mean and latest accuracy must be below 60%.
- Java changes unsupported suggestions to `KEEP`. An accepted change is at most one level, clamped to the three available levels.
- On service failure or invalid output, Java uses the latest score: at least 80% increase, 60% to below 80% keep, below 60% decrease. This fallback is deliberately the original latest-score baseline, not the model acceptance policy.
- Record only first-attempt comprehension answers. Do not score personal story choices or infer ability from reading speed. The future app must enforce one completed-section record per actual section; this stateless prototype does not prevent a caller from submitting duplicate history entries.

These are experimental project rules, not validated educational assessment thresholds. Qwen is a general instruction model, not a trained reading-level classifier. Supplying player summaries does not retrain it. Its suggestion may be wrong or conservative, and Java's safeguards do not establish model quality.

## Plug into the JavaFX application later

The public integration seam is `DifficultyService.ModelTool`. `QwenClient` implements it, so the game-facing service does not depend on Python-specific implementation details.

```java
var service = new DifficultyService(
    new QwenClient(URI.create("http://127.0.0.1:8765/infer")));

// Java checks the answer key. Selections must be first attempts, one per question.
var questions = List.of(
    new DifficultyService.ComprehensionQuestion(3, 1),
    new DifficultyService.ComprehensionQuestion(3, 2));
var section = DifficultyService.Section.grade(
    DifficultyService.Level.MEDIUM, questions, List.of(1, 2));

// The application owns persistence and chronological history.
var decision = service.decide(currentDifficulty, completedSectionHistory);
// Select your prepared story variant using decision.nextDifficulty().
```

The snippet omits imports and the app-owned history/current-level variables. To use it, copy the three Java source files into the app's matching package directory and add the prototype's Gson dependency to the app POM, or build/publish this module as an internal Maven dependency. Use a JavaFX `Task` or other worker thread for `decide()`; never run network/model waits on the JavaFX application thread. Update views on the JavaFX thread and prevent overlapping analyze actions.

**Existing app mismatch:** the app's `ReadingLevel` currently has `BEGINNER`, `DEVELOPING`, `PROFICIENT`, `ADVANCED` (four bands). This prototype uses the three levels discussed for the game. Agree on a level mapping or revise the app's level scheme before integration; do not map by enum ordinal or silently drop the fourth band. No mapping or controller changes are included here.

Authentication, database persistence, automatic subprocess startup/shutdown, packaged distribution, and prepared story variants remain app responsibilities and are not implemented by this standalone prototype.

## Model contract

Python accepts exactly two text fields, both constructed by Java. It applies the tokenizer's chat template using `instruction` as the system message and `input` as the user message:

```json
{"instruction":"Java-created model instructions.","input":"Java-created aggregate performance summary."}
```

Successful response:

```json
{
  "suggested_action": "keep",
  "model_id": "Qwen/Qwen2.5-0.5B-Instruct",
  "revision": "7ae557604adf67be50417f59c2c2f167def9a775",
  "elapsed_ms": 7000
}
```

Elapsed time above is illustrative, not a performance guarantee. Java validates the model ID, revision, and allowed action. It limits response size to 8 KB and has a 45-second whole-response deadline. Python limits request size to 8 KB and each prompt part to 2,000 characters, binds to loopback only, and allows one inference at a time. Busy, loading, or failed inference returns HTTP 503. This local development service has no authentication: do not expose it to a network or treat it as production-hardened.

Inference uses mean completion-token log likelihood for the three fixed labels and returns the highest-scoring label. It does not produce arbitrary executable text, does not claim calibrated confidence, and does not implement game rules. The same model revision is pinned in Java and Python. Model details and Apache 2.0 license: [official Qwen model card](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct).

## Verification

Java unit and HTTP contract tests:

```sh
mvn test
```

Python model-tool contract tests (fake scorer, no weights required):

```sh
python -m unittest discover -s model-tool -v
```

Use your venv's Python executable for that command. These tests verify code and error handling; they do not evaluate the model's educational accuracy. Use the `requireModel` demo separately to verify the real model path. See [VERIFICATION.md](VERIFICATION.md) for the observed development results.
