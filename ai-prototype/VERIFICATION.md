# Prototype verification

Verified on 2026-10-06 on Linux, with Maven using JDK 25 (compiling with `--release 21`), Python 3.14, CPU PyTorch 2.14.0, Transformers 5.17.0, and Hugging Face Hub 1.31.0. Windows/macOS setup commands are provided, but those platforms were not executed during this verification.

## Code and integration checks

- `mvn test`: 18 Java tests passed. Coverage includes Java answer checking, cold start, comparable-history selection, model prompt construction, accepted/rejected recommendations, level bounds, fallback rules, and local HTTP response validation.
- `python3 -m unittest discover -s model-tool -v`: 6 model-tool contract tests passed. These use a fake scorer and are not model accuracy tests.
- `mvn package`: successful standalone module build.
- Repository-root `mvn -B -DskipTests compile`: existing JavaFX app compiled successfully. No JavaFX GUI launch or gameplay integration is claimed.
- Ordinary Java demo against an unused local port: all four cases explicitly used `rules_fallback`, and cold start used `cold_start`.
- Real Qwen model loaded from the pinned revision and was called by Java through HTTP. The real-model demo uses `-DrequireModel=true`, so a fallback cannot be mistaken for model inference.

## Final prompt format: observed model behavior

All cases below start at MEDIUM, with three first-attempt section scores out of five.

| History | Model suggestion | Java applied action | Final level |
| --- | --- | --- | --- |
| 4/5, 4/5, 5/5 | INCREASE | INCREASE | HARD |
| 3/5, 3/5, 3/5 | INCREASE | KEEP, rejected by Java safeguards | MEDIUM |
| 2/5, 1/5, 1/5 | DECREASE | DECREASE | EASY |
| 3/5, 4/5, 5/5 | INCREASE | INCREASE | HARD |

This verifies the transport and Java's ownership of final decisions. It does **not** establish educational validity or reliable model accuracy. Qwen's steady-60% recommendation disagreed with the illustrative expected action, and the Java safeguard was necessary. Earlier combined instruction/input prompt formats also produced an unsupported increase on the struggling case; the final contract keeps Java's system instruction and numeric input as separate chat messages. Prompt sensitivity is a limitation, not evidence of validated improvement.

Before using this with students, evaluate representative, held-out histories and compare the model against the Java baseline. Include moderate/mixed scores, transitions, varying question counts, and repeated sections. Do not claim the model learns individual ability, has calibrated confidence, or improves learning outcomes based on these four examples.

## Scope and remaining integration

The standalone prototype works; the app's existing `AdaptiveEngine` remains unchanged. The app currently has four reading bands and the prototype has three levels, so the group must agree on that mapping. JavaFX background-task wiring, per-account history persistence, duplicate-section prevention, packaged model-tool startup, and story-variant selection are not included.

Only prototype source, tests, and documentation are intended for the feature-branch commit. Downloaded model weights, Python environments, bytecode caches, compiled JARs, and unrelated Desktop checkout changes are excluded.
