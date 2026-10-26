# Adaptive Reading Game — JavaFX UI/UX FXML Prototype

The `uiux-*.fxml` files are visual references. Their audio settings belong to that reference design. The live student game uses reading text size and character-art settings, with no audio playback.

This is a visual reference for the 40 UI/UX screens in
src/main/resources/com/adaptivereadinggame/view/. The original application
entry point and existing menu are unchanged. Launch this separate preview from
the project root with:

    mvn -Puiux-prototype javafx:run

Use the screen list and Previous/Next controls in the preview to inspect each
view. The visible action areas also navigate to example destination screens.
These routes are sample navigation only: the preview does not authenticate
players, score answers, call the model, save/load data, or connect to MySQL.
Screen 36 is the flowchart's “application closed” endpoint, not a rendered
screen, so it does not have an FXML file.

The FXML preserves the source design's 1280 × 720 layout, white menu/account
screens, serif typography, and burgundy/gold story styling. Text and vector
shapes are represented in JavaFX FXML, with transparent JavaFX buttons over
design actions for preview navigation. The font family names are retained;
Georgia is not installed in the current Linux environment, so JavaFX renders
that family using its local serif fallback. No fonts are bundled.

## Screen guide

### Account

1. [01 — Welcome](../src/main/resources/com/adaptivereadinggame/view/uiux-01-welcome.fxml)<br>
   The player chooses whether to log in or create an account. The sample links
   open the login or signup view.

2. [02 — Sign up](../src/main/resources/com/adaptivereadinggame/view/uiux-02-sign-up.fxml)<br>
   The player enters an account name and password to register. The sample
   signup action shows success, while Back returns to Welcome.

3. [03 — Signup · missing fields](../src/main/resources/com/adaptivereadinggame/view/uiux-03-signup-missing-fields.fxml)<br>
   An inline message explains that required signup information is missing.
   The form remains visible so the player can correct it without losing context.

4. [04 — Signup · duplicate name](../src/main/resources/com/adaptivereadinggame/view/uiux-04-signup-duplicate-name.fxml)<br>
   An inline message explains that the chosen account name is unavailable.
   The player can try a different name or return to the account entry screen.

5. [05 — Signup success → login](../src/main/resources/com/adaptivereadinggame/view/uiux-05-signup-success-login.fxml)<br>
   A confirmation shows that signup succeeded and presents the login form.
   The sample action continues to a main-menu state; no account is created here.

6. [06 — Log in](../src/main/resources/com/adaptivereadinggame/view/uiux-06-log-in.fxml)<br>
   The player enters an account name and password to access progress. The
   preview action goes to an example main menu and does not check credentials.

7. [07 — Login · invalid credentials](../src/main/resources/com/adaptivereadinggame/view/uiux-07-login-invalid-credentials.fxml)<br>
   A neutral error explains that the supplied login details did not match.
   The player can correct them and retry without revealing which field was wrong.

### Main menu

8. [08 — Main menu · no progress](../src/main/resources/com/adaptivereadinggame/view/uiux-08-main-menu-no-progress.fxml)<br>
   A new player can start a game, load a save, open settings, log out, or quit.
   Continue is shown unavailable because this example has no saved progress.

9. [09 — Main menu · returning player](../src/main/resources/com/adaptivereadinggame/view/uiux-09-main-menu-returning-player.fxml)<br>
   A returning player can continue a checkpoint or start a new playthrough.
   Starting over first opens a confirmation so the current progress is clear.

10. [10 — New game confirmation](../src/main/resources/com/adaptivereadinggame/view/uiux-10-new-game-confirmation.fxml)<br>
    The player confirms starting a new playthrough from the returning-player menu.
    Cancel returns without deleting manual saves.

### Story

11. [11 — How to play](../src/main/resources/com/adaptivereadinggame/view/uiux-11-how-to-play.fxml)<br>
    The screen explains how to advance story text and make narrative choices.
    It distinguishes those choices from scored Reading Check answers.

12. [12 — Introduction](../src/main/resources/com/adaptivereadinggame/view/uiux-12-introduction.fxml)<br>
    The player reads the story introduction before Chapter 1 begins.
    Play advances into the first scene; the story text remains sample content.

13. [13 — Story · dialogue](../src/main/resources/com/adaptivereadinggame/view/uiux-13-story-dialogue.fxml)<br>
    The player reads spoken dialogue in the burgundy-and-gold story panel.
    Next advances the conversation, while Menu opens the pause/navigation view.

14. [14 — Story · thoughts](../src/main/resources/com/adaptivereadinggame/view/uiux-14-story-thoughts.fxml)<br>
    A distinct text treatment marks the player's private thoughts.
    Next advances the story, and Menu opens navigation without advancing it.

15. [15 — Story · narrative choice](../src/main/resources/com/adaptivereadinggame/view/uiux-15-story-narrative-choice.fxml)<br>
    The player chooses how to respond to a story character. The choice changes
    the narrative path, not the comprehension score or difficulty.

16. [16 — Story · branch continuation](../src/main/resources/com/adaptivereadinggame/view/uiux-16-story-branch-continuation.fxml)<br>
    The story acknowledges the selected action before the reading assessment.
    Next opens the first Reading Check view; the displayed branch text is sample.

### Reading Checks

17. [17 — Reading Check · unanswered](../src/main/resources/com/adaptivereadinggame/view/uiux-17-reading-check-unanswered.fxml)<br>
    The player reads a passage and question on a separate assessment screen.
    Submit is unavailable until an answer is selected; the sample Menu pauses.

18. [18 — Reading Check · selected B](../src/main/resources/com/adaptivereadinggame/view/uiux-18-reading-check-selected-b.fxml)<br>
    The screen shows answer B selected before it is submitted. The player may
    change the selection until Submit records the first answer.

19. [19 — Reading Check · selected A](../src/main/resources/com/adaptivereadinggame/view/uiux-19-reading-check-selected-a.fxml)<br>
    The screen shows an incorrect choice selected without revealing feedback yet.
    Submit records that first attempt and opens the incorrect-feedback example.

20. [20 — Reading Check · correct feedback](../src/main/resources/com/adaptivereadinggame/view/uiux-20-reading-check-correct-feedback.fxml)<br>
    The player sees whether the submitted answer was correct and why. The first
    attempt is final for scoring; Next advances to another question state.

21. [21 — Reading Check · incorrect feedback](../src/main/resources/com/adaptivereadinggame/view/uiux-21-reading-check-incorrect-feedback.fxml)<br>
    The player receives a supportive explanation after an incorrect first answer.
    Feedback does not allow a second score-changing submission.

22. [22 — Reading Check · final question](../src/main/resources/com/adaptivereadinggame/view/uiux-22-reading-check-final-question.fxml)<br>
    The final-question state marks the end of a five-question check. It reuses
    the same layout so implementation can use one assessment view.

23. [22b — Reading Check · final feedback](../src/main/resources/com/adaptivereadinggame/view/uiux-22b-reading-check-final-feedback.fxml)<br>
    The final submitted answer receives the same explanatory feedback as earlier
    questions. View Results continues to analysis rather than another question.

### Difficulty and results

24. [23 — Preparing the next challenge](../src/main/resources/com/adaptivereadinggame/view/uiux-23-preparing-the-next-challenge.fxml)<br>
    A status view tells the player the completed answers are being analyzed.
    The two sample routes show a normal result and a model-unavailable fallback.

25. [24 — Chapter results · normal](../src/main/resources/com/adaptivereadinggame/view/uiux-24-chapter-results-normal.fxml)<br>
    The result presents a comprehension score, first-answer count, and next
    difficulty. The shown 80% and Medium values are examples, not fixed outputs.

26. [25 — Chapter results · model fallback](../src/main/resources/com/adaptivereadinggame/view/uiux-25-chapter-results-model-fallback.fxml)<br>
    The result remains available when the optional model cannot respond.
    Java should apply its deterministic fallback; the displayed outcome is only
    an example.

### Pause, saves, and settings

27. [26 — Game menu / pause](../src/main/resources/com/adaptivereadinggame/view/uiux-26-game-menu-pause.fxml)<br>
    The player can pause and open save, load, settings, or exit actions.
    Return should restore the exact story or assessment state that was paused.

28. [27 — Save · occupied and empty slots](../src/main/resources/com/adaptivereadinggame/view/uiux-27-save-occupied-and-empty-slots.fxml)<br>
    The player compares save metadata and chooses an empty or occupied slot.
    Empty slots can accept a save; an occupied slot asks before replacement.

29. [28 — Save · success](../src/main/resources/com/adaptivereadinggame/view/uiux-28-save-success.fxml)<br>
    A confirmation communicates that saving succeeded. Return goes back to the
    held screen without changing the story location.

30. [29 — Save · overwrite confirmation](../src/main/resources/com/adaptivereadinggame/view/uiux-29-save-overwrite-confirmation.fxml)<br>
    The player confirms before replacing data in an occupied slot. Cancel
    preserves the existing save and returns to the slot list.

31. [30 — Load · occupied and empty slots](../src/main/resources/com/adaptivereadinggame/view/uiux-30-load-occupied-and-empty-slots.fxml)<br>
    The player selects a saved checkpoint using its visible metadata. Empty slots
    are unavailable; unsaved active progress requires a warning before loading.

32. [31 — Load · unsaved progress warning](../src/main/resources/com/adaptivereadinggame/view/uiux-31-load-unsaved-progress-warning.fxml)<br>
    The player confirms whether to discard current unsaved progress before load.
    Cancel returns to the slot list without changing the current session.

33. [32 — Settings](../src/main/resources/com/adaptivereadinggame/view/uiux-32-settings.fxml)<br>
    The player reviews audio, reading-speed, and proposed text-size controls.
    The displayed controls are visual only and autoplay should pause during a
    Reading Check.

### Exit and completion

34. [33 — Return to main menu confirmation](../src/main/resources/com/adaptivereadinggame/view/uiux-33-return-to-main-menu-confirmation.fxml)<br>
    The player confirms leaving an active session before unsaved progress is
    discarded. Cancel returns to the paused game.

35. [34 — Logout confirmation](../src/main/resources/com/adaptivereadinggame/view/uiux-34-logout-confirmation.fxml)<br>
    The player confirms ending the signed-in session. Logout should clear session
    access but keep saved progress attached to the account.

36. [35 — Quit confirmation](../src/main/resources/com/adaptivereadinggame/view/uiux-35-quit-confirmation.fxml)<br>
    The warning explains that unsaved progress will be lost if the player quits.
    Cancel returns to the caller; confirmed quit closes the actual app.

37. [37 — Story complete](../src/main/resources/com/adaptivereadinggame/view/uiux-37-story-complete.fxml)<br>
    The player sees a clear end point after the last available chapter.
    Main Menu returns to the menu without offering Continue into missing content.

38. [38 — Load · unreadable save](../src/main/resources/com/adaptivereadinggame/view/uiux-38-load-unreadable-save.fxml)<br>
    An error explains that the chosen checkpoint could not be loaded. Back to
    Load lets the player choose another slot without deleting the unreadable save.

39. [39 — Save · persistence failed](../src/main/resources/com/adaptivereadinggame/view/uiux-39-save-persistence-failed.fxml)<br>
    The player is told the save did not succeed and current progress remains
    unsaved. Retry attempts again; Cancel returns without claiming success.

40. [40 — Next chapter · loading / completion](../src/main/resources/com/adaptivereadinggame/view/uiux-40-next-chapter-loading-completion.fxml)<br>
    A transition view represents loading the next chapter with the validated
    difficulty. If no next chapter exists, the game should show Story Complete.

## Rules to keep when wiring the real game

- Narrative choices affect story events, never the comprehension score.
- Only the first submitted answer counts toward scoring. A player may change an
  answer selection before submitting.
- Java owns authentication, scoring, saves, state transitions, and final
  difficulty selection. A future Python model helper may only make an optional
  recommendation; Java validates it and applies fallback rules.
- These screens do not implement real application behavior. In particular, the
  result percentages, difficulty values, account examples, story copy, and save
  contents are design samples. Existing prototype fixed scores or print-only
  save messages are not completed persistence.
- The reference drawings show more save slots than the current Dev 1 account/game
  code supports. Keep the agreed three-slot behavior when integrating the actual
  save screens unless the group coordinates a change first.
