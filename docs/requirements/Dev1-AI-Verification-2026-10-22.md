# AI policy verification

> Historical snapshot: This test record predates the current playable-flow integration. See [current playable-flow verification](Dev1-Playable-Flow-Verification-2026-10-22.md) for the present application status and latest combined test run.

The Dev1 Java policy and loopback Python helper passed contract tests, and the pinned Qwen model completed a real local inference on the tested Linux computer. This verifies the integration path, not its educational accuracy.

## Environment and commands

The real-model smoke used Linux x86_64, Python 3.14.8, CPU-only PyTorch 2.14.1, Transformers 5.17.0, and Hugging Face Hub 1.31.0. The model weights were already present in the Hugging Face cache. The model helper ran with Hugging Face offline mode enabled.

| Command | Result |
| --- | --- |
| `xvfb-run -a mvn -B -Darg.uiTests=true -Darg.mysqlTests=true -Darg.modelTests=true -Darg.ui.captureDir=target/visual-qa-ai verify` | PASS, 110 tests, 0 failures, 0 errors, 0 skipped; built the Maven JAR. MySQL test variables pointed to a disposable localhost database, and the model helper was ready on its loopback endpoint. |
| `model-tool/.venv/bin/python -m unittest discover -s model-tool -v` | PASS, 8 Python contract tests, including request-size and bundled-manifest checks. |
| `mvn -B -Dtest=QwenClientTest,DifficultyServiceTest,ModelAssistedDifficultyPolicyTest,AssessmentServiceConcurrencyTest test` | PASS, 22 Java policy, HTTP-contract, fallback, history, and concurrency tests. |
| `mvn -B -Darg.modelTests=true -Dtest=QwenModelSmokeTest test` | PASS, 1 real-model inference returned the expected model identity and a Java-guarded difficulty decision. |

After staging the bundled snapshot through Git LFS in the local checkout, I started the helper with `HF_HUB_OFFLINE=1`. Its `/health` response reported `source: repository`, and the real-model smoke test passed again. This confirms the bundled files load without contacting Hugging Face. It does not prove a fresh clone can download the LFS object because the Dev1 change has not been published.

The combined suite also rendered 13 account/menu test screenshots. It did not render a playable game screen.

## Behavior covered

`ModelAssistedDifficultyPolicy` reads chronological results from `AssessmentRepository`, converts them to difficulty and accuracy sections, adds the current section, and calls `DifficultyService`. The prompt contains no learner ID, chapter ID, answer key, story choice, or raw answer.

`DifficultyService` waits for three consecutive sections at the same difficulty before calling the helper. Java rejects recommendations that fail its accuracy safeguards, limits accepted changes to one level, and uses the deterministic baseline if the helper fails.

`AssessmentService.complete` runs the difficulty policy outside the service-wide monitor. Per-session completion futures keep duplicate calls idempotent while other sessions can continue during model inference.

## Limits

The first smoke test loaded the model from the local Hugging Face cache. The later offline smoke loaded the staged Git LFS snapshot. Install the CPU wheel separately as described in [the model-tool guide](../../model-tool/README.md); a plain `pip install -r requirements.txt` does not install PyTorch.

The root application wires the policy, but its story service and playable game screens remain unavailable. The app cannot yet produce a real player assessment through the UI. The Qwen model is a general instruction model, and these tests do not measure whether its recommendations improve reading outcomes.

The changes and test record are local to the Dev1 checkout. No commit, push, or merge was performed.
