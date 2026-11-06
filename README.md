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

Choose the setup steps for your device in the [script guide](scripts/SCRIPT.md). It covers Windows, Linux, macOS, and Android tablets using Termux:X11.

## Save your progress

Use Save/Load from the main menu to save your current story in a slot. Save again after making more progress. If the game warns that your current progress is unsaved, return to the game, save from Save/Load, then quit.

The setup script configures a database stored on the device. Accounts and progress do not sync between devices. Windows, macOS, and most desktop Linux setups use MySQL; Fedora can use its MariaDB server when Fedora's MariaDB package is already installed. Termux stores its MariaDB files inside its Debian environment. The setup asks for a local database URL, an application username, and a hidden password. If the game is launched without setup, it uses temporary storage and accounts and save slots are lost when it closes.

## If startup fails

If startup shows an error, select Retry once. If the error continues, keep the message and ask the person who provided the game for help.
