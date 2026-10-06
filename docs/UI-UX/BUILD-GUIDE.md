# Adaptive Reading Game — UI/UX build guide

This is a proposed complete design reference, not an implementation or a claim that the source features work.

## Open the reference

Open `index.html` in a browser. It is self-contained and works without a server or internet connection. Use All screens to inspect every state, Player flow for the overview, and Build guide for priorities and contracts. The Print / Save PDF button lays out the flow and screen explanations for optional export.

## Scope and source

Original ZIP: `/home/patt/Desktop/AdaptiveReadingGame-main_UI.zip`. The original is unchanged. The package uses the existing JavaFX theme and flourish paths, and adds the requested assessment, persistence, authentication, and exit states. Frames are 1280 × 720, a 2/3 reference scale of the source 1920 × 1080 canvas.

Existing base means the layout is retained, not that its backend is complete. Existing code state means a message is in source but not necessarily in submitted screenshots. Proposed addition means a new design state. The closed application endpoint is a guide only.

## Build order

1. Shared controls and navigation state.
2. Authentication + main menu + logout.
3. Story dialogue, branches, and pause/resume.
4. Reusable Reading Check, selection, first-attempt recording, and feedback.
5. Java-owned results, validated difficulty, optional-model timeout fallback.
6. Save/load persistence, confirmations, and error recovery.
7. Settings, keyboard navigation, text-size support, and end-of-story handling.

## Important contracts

- Narrative choices do not determine comprehension score or difficulty.
- Selection can change before Submit; the first submission is recorded once and cannot be rescored.
- Source UI has six slots per page and a ten-page label, not working pagination. Agree on slot count and backend mapping before implementing.
- Return restores the exact caller, branch, question index, selection, and locked submissions.
- Load should validate the full checkpoint before replacing the live session.
- Save success is shown only after persistence succeeds; keep the prior save intact on failure.
- Record completed answers before optional model analysis; Java owns final difficulty and fallback.
- Result scores and next difficulties are illustrative values, not thresholds.
- Replace placeholder introduction and sample passages with authored, reviewed game content.
- No password recovery, verification email, teacher dashboard, leaderboard, or cloud sync has been assumed. These are outside the supplied requirements.

## Fonts and Figma import

Source title: Times New Roman. Source body/menu: Georgia. Georgia is not installed here; the preview falls back explicitly to Times New Roman. No font files are distributed. Install a licensed Georgia font or approve a substitute before final visual signoff. SVGs specify Georgia first.

Import numbered SVGs from `screens/` into the existing Figma file manually. These are real vector shapes and SVG text, not screenshot images. Figma may alter/outline text during import; verify font handling and rebuild native text if necessary. Native components, auto-layout, variables, and clickable Figma reactions are NOT included by SVG import. Use the interaction notes below to connect prototype routes manually.

Direct Figma work was blocked by the connected Starter plan tool-call limit. This package does not populate or verify the online Figma file.

## Preview limitations

Forms, sliders, model calls, and saving are not functional. No credentials are entered or stored. Screens are representative states linked for review. The preview skips assessment questions 2–4 and shares a sample story branch; production must use real distinct content. The final question reuses sample copy only to show a reusable layout. Save success represents a state, not real storage.

## Screen explanations and destinations

### 01 — Welcome

Group: Account. Status: Existing base.

Players choose whether to log in or create an account. This preserves the original simple welcome screen and provides the entry point for personalized progress.

Interaction: LOG IN → 06; SIGN UP → 02.

### 02 — Sign up

Group: Account. Status: Existing base.

Players supply an account name and password. A successful signup takes them to login rather than entering the game automatically.

Interaction: SIGN UP → 05 (sample success); BACK → 01. Validation variants: 03 and 04.

### 03 — Signup · missing fields

Group: Account. Status: Existing code state.

An inline message explains that the required fields are missing. The form stays available so the player can correct the problem without losing context.

Interaction: Keep the same form; do not create an account until valid. Preview SIGN UP follows the corrected path.

### 04 — Signup · duplicate name

Group: Account. Status: Existing code state.

An inline message explains that the account name already exists. Players can choose another name or return to login.

Interaction: Keep credentials masked; do not reveal another account’s details.

### 05 — Signup success → login

Group: Account. Status: Existing code state.

A success message confirms account creation on the login form. The player then logs in to reach the main menu.

Interaction: LOG IN → 08 (new-player example). Real code must read actual account progress.

### 06 — Log in

Group: Account. Status: Existing base.

Players enter their account name and password to access their saved progress. The preview is a visual route demonstration and does not authenticate or collect credentials.

Interaction: LOG IN → 08 (sample success); BACK → 01; error variant → 07.

### 07 — Login · invalid credentials

Group: Account. Status: Existing code state.

A neutral error message says the name or password is invalid. Players can correct their entries and retry without disclosing which credential was wrong.

Interaction: Stay on login; LOG IN previews a successful corrected attempt.

### 08 — Main menu · no progress

Group: Menu. Status: Existing base + additions.

New players can start a game, open load/settings, or quit. Continue is disabled and explained because no resumable checkpoint exists.

Interaction: NEW GAME → 11; LOAD → 30; SETTINGS → 32; LOG OUT → 34; QUIT → 35. Account name and Logout are additions.

### 09 — Main menu · returning player

Group: Menu. Status: Proposed state.

Returning players can continue their latest checkpoint or start a new game. This gives Continue a clear purpose and protects existing progress before a restart.

Interaction: CONTINUE → 13 (sample checkpoint); NEW GAME → 10.

### 10 — New game confirmation

Group: Menu. Status: Proposed addition.

A returning player confirms starting a new playthrough. The message distinguishes restarting current progress from deleting manual saves.

Interaction: START NEW → 11; CANCEL → 09. Keep manual saves unless the player explicitly deletes them.

### 11 — How to play

Group: Story. Status: Existing base + clarified copy.

Players learn the story controls before playing. The explanation clearly distinguishes narrative choices from scored Reading Checks.

Interaction: NEXT → 12; BACK → 08. Space advances narrative only, never submits an assessment answer.

### 12 — Introduction

Group: Story. Status: Existing base.

The introduction establishes the player’s role before Chapter 1 begins. It preserves the existing brief servant premise, with final story copy still to be supplied by the team.

Interaction: PLAY → 13; BACK → 11. Text is source placeholder, not an approved final script.

### 13 — Story · dialogue

Group: Story. Status: Existing base.

The player reads a character’s dialogue in the existing burgundy-and-gold panel. NEXT advances the conversation, while MENU opens navigation without advancing the story.

Interaction: NEXT / Space → 14; MENU / Esc → 26. Restore the exact scene when returning from a menu.

### 14 — Story · thoughts

Group: Story. Status: Existing base.

The lighter panel and italic text distinguish the player’s private thoughts from spoken dialogue. Players advance when they finish reading.

Interaction: NEXT / Space → 15; MENU / Esc → 26.

### 15 — Story · narrative choice

Group: Story. Status: Existing base + sample copy.

The player chooses how to respond to the character. These choices affect story branches and relationships, not comprehension score or difficulty.

Interaction: All three preview choices → 16; real game must restore the chosen branch. Story choice text is illustrative.

### 16 — Story · branch continuation

Group: Story. Status: Proposed state.

The story acknowledges the chosen narrative action before the Reading Check. This prevents a story choice from jumping directly to a fabricated comprehension result.

Interaction: NEXT → 17. Branch-specific dialogue is sample content; author each actual branch separately.

### 17 — Reading Check · unanswered

Group: Reading Check. Status: Proposed addition.

A separate passage and question measure comprehension. Submit stays disabled until an answer is selected, and the player can read at their own pace.

Interaction: B → 18; A or C → 19. MENU pauses; no time limit or autoplay submission.

### 18 — Reading Check · selected B

Group: Reading Check. Status: Proposed addition.

The selected answer is visibly highlighted before submission. Players can change their selection until Submit records their first attempt.

Interaction: SUBMIT → 20; A/C → 19. Selection alone must not score or advance the question.

### 19 — Reading Check · selected A

Group: Reading Check. Status: Proposed addition.

This variant shows a selected incorrect answer before submission. It demonstrates the wrong-answer route without disclosing correctness before the player submits.

Interaction: SUBMIT → 21; B → 18. The example groups A/C into the incorrect selection route.

### 20 — Reading Check · correct feedback

Group: Reading Check. Status: Proposed addition.

The player sees confirmation and a passage-based explanation. The first submitted answer is locked, and the next action advances to another question.

Interaction: NEXT QUESTION → 22 (representative final-question jump); the actual game must show questions 2–4.

### 21 — Reading Check · incorrect feedback

Group: Reading Check. Status: Proposed addition.

The player receives a supportive explanation of the correct reasoning. The incorrect first attempt remains recorded; feedback does not offer a score-changing retry.

Interaction: NEXT QUESTION → 22 (representative final-question jump). No resubmission for this scored question.

### 22 — Reading Check · final question

Group: Reading Check. Status: Proposed state.

The final-question state marks the end of a five-question check. It reuses the same question layout so the team can implement one reusable assessment view.

Interaction: SUBMIT → 22b after first-attempt recording. This is a fixed selected-state preview; sample passage repeats for layout only.

### 22b — Reading Check · final feedback

Group: Reading Check. Status: Proposed state.

The final submitted answer receives the same explanatory feedback as earlier questions. View Results then moves to difficulty analysis rather than presenting another question.

Interaction: VIEW RESULTS → 23. Reuse the feedback component with a final-question action variant; incorrect final feedback follows the same route.

### 23 — Preparing the next challenge

Group: Difficulty / Results. Status: Proposed addition.

A brief status panel tells the player that the completed answers are being analyzed. Progress is recorded before optional model analysis so a timeout does not lose the completed check.

Interaction: NEXT (preview success) → 24; MODEL UNAVAILABLE (preview test route) → 25. Real Java code should transition automatically with a bounded timeout.

### 24 — Chapter results · normal

Group: Difficulty / Results. Status: Existing base + real-data design.

The result combines a comprehension score, first-answer count, and the next difficulty. Java validates any model recommendation before the player continues.

Interaction: CONTINUE → 40. 80% / Medium is illustrative, not a fixed rule or a model guarantee.

### 25 — Chapter results · model fallback

Group: Difficulty / Results. Status: Proposed addition.

The result remains usable when the optional model fails or times out. Java applies its deterministic fallback policy and explains the outcome without alarming the player.

Interaction: CONTINUE → 40. Easy is an example outcome; use actual validated policy output.

### 26 — Game menu / pause

Group: Navigation / Saves. Status: Proposed addition.

This menu pauses narrative progression and exposes save, load, settings, and exit actions. Return restores the exact prior scene or assessment state instead of restarting it.

Interaction: SAVE → 27; LOAD → 30; SETTINGS → 32; MAIN MENU → 33; RETURN → captured previous scene in the preview.

### 27 — Save · occupied and empty slots

Group: Navigation / Saves. Status: Existing base + populated states.

Players can identify saves by chapter, scene, difficulty, and timestamp. Empty slots accept new saves, while occupied slots require overwrite confirmation.

Interaction: Slot 1 → 29; empty Slot 2–6 → 28. Preserve the original six-slot page visually; finalize count with backend team.

### 28 — Save · success

Group: Navigation / Saves. Status: Proposed addition.

A visible confirmation appears only after persistence succeeds. The player stays on Save and can return to the held scene without losing their place.

Interaction: RETURN → previous screen. Example shows a newly populated Slot 2; overwrite instead updates the chosen occupied slot.

### 29 — Save · overwrite confirmation

Group: Navigation / Saves. Status: Proposed addition.

The player confirms replacing an occupied slot before data is changed. Cancel preserves the existing save and returns to the slot list.

Interaction: OVERWRITE → 28 after successful write; CANCEL → 27. Failure route is 39.

### 30 — Load · occupied and empty slots

Group: Navigation / Saves. Status: Existing base + populated states.

Players select a saved checkpoint using meaningful metadata. Empty slots are disabled, so the interface does not pretend to load nonexistent progress.

Interaction: Slot 1 → 31 if current state is unsaved, otherwise resume immediately. If called from main menu with no active game, skip discard confirmation.

### 31 — Load · unsaved progress warning

Group: Navigation / Saves. Status: Proposed addition.

The player confirms discarding unsaved session progress before loading a checkpoint. Cancel returns to Load without changing the current session.

Interaction: LOAD SAVE → 13 only after validation and successful restore; CANCEL → 30. Invalid save route is 38.

### 32 — Settings

Group: Navigation / Saves. Status: Existing base + text-size addition.

The existing audio and reading-speed controls retain their two-column layout. Text-size controls are a proposed accessibility addition, and autoplay pauses whenever a Reading Check is active.

Interaction: RETURN restores caller. Preview sliders/text-size are appearance specifications, not working controls.

### 33 — Return to main menu confirmation

Group: Exit / Completion. Status: Proposed addition.

The player confirms leaving an active session before unsaved changes are discarded. This distinguishes Main Menu from Return, which resumes the held game.

Interaction: MAIN MENU → 09; CANCEL → previous screen. Saved checkpoints remain available.

### 34 — Logout confirmation

Group: Exit / Completion. Status: Proposed addition.

The player confirms ending their authenticated session. Logout clears session access, but keeps saved progress associated with the account.

Interaction: LOG OUT → 01; CANCEL → 09. If active unsaved play exists, use the same loss warning and allow cancel.

### 35 — Quit confirmation

Group: Exit / Completion. Status: Existing base.

The existing quit card warns that unsaved progress will be lost. Players can cancel without navigating or changing game state.

Interaction: QUIT → 36 (terminal-state representation); CANCEL restores caller. Real app closes its window only on confirmation.

### 36 — Application closed · flow endpoint

Group: Exit / Completion. Status: Guide endpoint, not app screen.

This reference-only endpoint represents the application window closing. It is not a new screen for the JavaFX team to implement.

Interaction: Preview RESTART → 01; real program has no rendered screen after exit.

### 37 — Story complete

Group: Exit / Completion. Status: Proposed addition.

The player receives a clear endpoint after the last available chapter is completed. They can return to the main menu without a Continue button pointing to nonexistent content.

Interaction: MAIN MENU → 09 with story-complete metadata. Continue should replay or be disabled only if the team explicitly specifies that behavior.

### 38 — Load · unreadable save

Group: Navigation / Saves. Status: Proposed addition.

An error explains that the selected checkpoint could not be loaded. The current session and save data are preserved so the player can choose another slot.

Interaction: BACK TO LOAD → 30. Do not partially restore or silently delete a damaged save.

### 39 — Save · persistence failed

Group: Navigation / Saves. Status: Proposed addition.

The player is told that the save did not succeed and the session remains unsaved. Retry attempts persistence again; cancel restores the session without claiming progress was saved.

Interaction: RETRY → 28 is the successful retry example; CANCEL → previous screen. Keep the old slot intact on failure.

### 40 — Next chapter · loading / completion

Group: Exit / Completion. Status: Proposed addition.

The player sees a transition while the next chapter and validated difficulty are loaded. If there is no next chapter, the game shows Story Complete instead of entering an empty scene.

Interaction: NEXT CHAPTER → 13 is a reusable-layout sample; STORY FINISHED → 37 is a reviewer test route. Real application chooses automatically.
