# Local Qwen helper

The application can ask a loopback-only Python helper for an optional difficulty recommendation. Java scores first answers, selects the learner's history, applies the safeguards, and chooses the final difficulty. The helper receives only a short numeric summary and returns one of `increase`, `keep`, or `decrease`.

The integration uses `Qwen/Qwen2.5-0.5B-Instruct` at the pinned revision `7ae557604adf67be50417f59c2c2f167def9a775`. The model card lists Apache 2.0 licensing, and the license text is stored beside the model files in `model-tool/models/Qwen2.5-0.5B-Instruct/LICENSE`: [official Qwen model card](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct). The model is a general instruction model, not a validated reading-level classifier.

## Set up a local Python environment

Run these Linux commands from the project root. They use CPU-only PyTorch so the install does not pull CUDA libraries that this helper does not use.

```sh
python3 -m venv model-tool/.venv
model-tool/.venv/bin/python -m pip install --no-cache-dir -r model-tool/requirements-cpu.txt --index-url https://download.pytorch.org/whl/cpu
model-tool/.venv/bin/python -m pip install --no-cache-dir -r model-tool/requirements.txt
```

The verified environment used Linux x86_64, Python 3.14.8, PyTorch 2.14.1 CPU, Transformers 5.17.0, and Hugging Face Hub 1.31.0. PyTorch wheel availability varies by operating system and processor. Use the [official PyTorch installer](https://pytorch.org/get-started/locally/) to select a supported Python and CPU wheel for another computer. The project has not yet been verified on Windows or macOS.

For Windows PowerShell, create the environment and install the same CPU packages with:

```powershell
py -3.14 -m venv model-tool\.venv
.\model-tool\.venv\Scripts\python.exe -m pip install --no-cache-dir -r model-tool\requirements-cpu.txt --index-url https://download.pytorch.org/whl/cpu
.\model-tool\.venv\Scripts\python.exe -m pip install --no-cache-dir -r model-tool\requirements.txt
```

Run the server with `model-tool\.venv\Scripts\python.exe model-tool\server.py`. macOS users should select a Python and processor combination that has an available PyTorch CPU wheel.

This repository tracks the pinned snapshot in `model-tool/models/Qwen2.5-0.5B-Instruct/`. Its 988,097,824-byte `model.safetensors` file is stored through Git LFS because GitHub limits regular Git files to 100 MiB: [GitHub large-file guidance](https://docs.github.com/en/repositories/working-with-files/managing-large-files/about-large-files-on-github).

Clone the branch with Git LFS enabled, or run `git lfs pull` after cloning, to retrieve the weight file. The helper validates the local manifest, revision, weight size, and checksum before using the bundled snapshot. If those files are absent, it falls back to the pinned Hugging Face revision and may need internet access.

## Start the helper

Open a terminal at the project root and run:

```sh
model-tool/.venv/bin/python model-tool/server.py
```

Wait for the model to load, then check `http://127.0.0.1:8765/health` in a browser. Continue when the response reports `"ready": true`. The helper binds only to `127.0.0.1` and has no authentication. Do not expose the port to a network.

In a second terminal, launch the JavaFX application with `mvn javafx:run`. Java uses `http://127.0.0.1:8765/infer` by default. Set `ARG_MODEL_URL` only when using a different loopback port. After a learner completes a reading checkpoint, the results screen reports whether the model recommendation, Java's guardrail, or Java's fallback selected the next difficulty.

## Policy and fallback

Java waits for three consecutive completed sections at the same difficulty before asking the model. It sends percentages, the current difficulty label, the mean, and the trend. It does not send learner or chapter IDs, story choices, answer keys, or raw answers.

Java rejects an increase unless the latest and mean accuracy are both at least 80%. It rejects a decrease unless both are below 60%. Java limits an accepted change to one adjacent level and clamps it to Easy through Hard. When the helper is loading, busy, invalid, interrupted, or unavailable, Java uses the latest score: at least 80% increases one level, 60% to below 80% keeps, and below 60% decreases one level.

These thresholds are rules for the school project and remain unvalidated as educational criteria. The real-model smoke test proves that the pinned model can answer the request contract on the tested computer. It does not show that the model improves reading outcomes.

## Run the tests

The standard Java tests use a fake model tool and do not download model weights:

```sh
mvn -B -Dtest=QwenClientTest,DifficultyServiceTest,ModelAssistedDifficultyPolicyTest,AssessmentServiceConcurrencyTest test
```

The Python tests use a fake scorer and do not need PyTorch:

```sh
model-tool/.venv/bin/python -m unittest discover -s model-tool -v
```

For the opt-in real-model check, start the helper and run:

```sh
mvn -B -Darg.modelTests=true -Dtest=QwenModelSmokeTest test
```

The smoke test requires the helper to report ready. See [the AI verification record](../docs/requirements/Dev1-AI-Verification-2026-10-22.md) for the observed test results.

The [reading draft verification](../docs/requirements/Dev1-Reading-Draft-Verification-2026-10-26.md) records the current application and helper checks.
