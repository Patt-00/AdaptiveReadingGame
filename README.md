# Adaptive Reading Game

Adaptive Reading Game is a story-driven reading game for elementary readers. Read a mystery story, make choices that shape its route, and answer comprehension questions at reading checkpoints.

## What you can do

- Create an account, sign in, and choose your player appearance.
- Read story scenes and make choices that change what happens next.
- Answer comprehension questions and see feedback and results.
- Continue a story and use up to three save slots when account-based saving is available.
- Adjust reading text and available audio in Settings. Choose Reduce Motion on the opening screen if you prefer fewer animations.

Story choices change the narrative. Your first submitted answer to each question determines its score. Results may change the difficulty of a later checkpoint.

## Start the game

Open the game folder in PowerShell on Windows, or Terminal on Linux and macOS. Run the setup script for your computer:

Windows PowerShell:

```powershell
.\scripts\Setup-And-Run.ps1
```

Linux or macOS:

```sh
./scripts/setup-and-run.sh
```

The setup script checks for the components needed to run the game and starts it. It may ask you to approve installation of missing components. When the opening and loading screens finish, press Space or click **PRESS SPACE TO ENTER**.

### Android tablets

Android play requires Termux and Termux:X11. Install the [Termux:X11 app](https://github.com/termux/termux-x11/releases), then run these commands from the game folder in Termux. Run setup once; use the second command whenever you want to start the game.

```sh
./scripts/termux-setup-x11.sh
./scripts/termux-run-x11.sh
```

## Save your progress

Use Save/Load from the main menu to save your current story in a slot. Save again after making more progress. If the game warns that your current progress is unsaved, return to the game, save from Save/Load, then quit.

The account and main menu tell you whether progress is saved to your account or temporary. The standard local setup uses temporary progress unless persistent account saving has been set up. If the notice says progress is temporary, your account and save slots will be lost when the game closes, even if you saved to a slot. If it says progress is saved to your account, saved slots remain available when you sign in again.

## If startup fails

If startup shows an error, select Retry once. If the error continues, keep the message and ask the person who provided the game for help.
