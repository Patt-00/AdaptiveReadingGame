# Dev 1 verification

Verified on October 11, 2026 (Asia/Manila), against the Dev 1 branch changes. This is evidence for the implemented Dev 1 scope, not a declaration that the whole game or MySQL integration is finished.

## Build and tests

```sh
xvfb-run -a mvn -B -Darg.uiTests=true -Darg.ui.captureDir=target/visual-qa verify
```

Result: **BUILD SUCCESS — 85 tests, 0 failures, 0 errors, 0 skipped**. Java source was compiled with `--release 21`; Maven used a Linux OpenJDK 25 runtime and platform-matching JavaFX 21.0.5. The package build produced the normal Maven JAR under ignored `target/`; run the app through Maven to resolve its dependencies, not as a standalone fat JAR.

| Test group | Tests | What it verifies |
| --- | ---: | --- |
| AccountGameServiceTest | 23 | Account isolation, full saves, cancellation/failure protection, Continue ordering, draft locking, current-session checks, logout cleanup |
| BackendSkeletonTest | 15 | Existing Java scoring, stable first answers/results, baseline difficulty, and basic checkpoint compatibility |
| SharedContractsTest | 16 | Immutable shared records, matching snapshots, strict version-1 JSON and malformed-data rejection |
| InMemoryAccountRepositoryTest | 5 | Temporary adapter atomic registration, uniqueness, account/profile/credential consistency |
| DefaultAuthServiceTest | 11 | Signup/login/logout validation, normalized usernames, generic credential failures, active-account protection, faulty-adapter rejection |
| Pbkdf2PasswordHasherTest | 4 | Real standard PBKDF2, salts, matching/nonmatching passwords, malformed/bounded verifiers, preserved input arrays |
| Dev1UiTest | 11 | Native JavaFX forms/tasks, signup success/errors, menu/slots/history, logout/quit cancellation, and cleanup-failure navigation |

Normal `mvn test` intentionally skips the opt-in native UI tests. The command above explicitly enables them, so none were skipped in the verified full run.

## Rendered UI checks

The native UI run produces 13 PNGs in ignored `target/visual-qa/`: welcome, signup form, signup validation, signup success, login validation, incorrect credentials, menu without Continue, menu with Continue, empty save slots, populated save slots, actual-score history, logout confirmation, and quit confirmation. Screens were inspected for readability, consistent styling, clipping, and clear storage/font notices. Two clipped save-slot labels were fixed and retested.

The populated-save and 50%-score history screens use **test-only fixtures**. Production startup does not seed pretend accounts, story chapters, saves, or completed results. No screenshots are evidence of MySQL persistence or a completed story screen.

Georgia is absent on this test machine. The screenshots explicitly disclose the Times New Roman fallback. No fonts were installed or silently substituted. Native tests emitted JavaFX/Maven newer-JDK and accessibility-bus warnings, but all tests and the package build passed; those warnings do not establish compatibility with every future JDK or every desktop environment.

## Preservation and remaining gaps

- The original source ZIP retains SHA-256 `f912b2910524c1abb791c06729db3e73ade8c99d90626848d2d473df0bda9264`.
- Work was done in a fresh checkout, not by repairing or modifying the moved original backend checkout's broken Git link.
- MySQL adapters, real chapter/story gameplay, durable reading-check restoration, complete game-screen navigation, and settings remain teammate/integration work.
- `AssessmentService.restoreAssessment` is an intentionally unimplemented, fail-before-mutation shared declaration. Do not count it as working resume logic.
- No Python/model branch was merged. Only Dev 1 branch publication is authorized; main and other branches remain separate.

See [the handoff guide](Dev1-Handoff.md) for the exact next pieces each teammate supplies.
