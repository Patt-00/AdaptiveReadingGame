# Start the game

Run the setup script for your device from the game folder.

## Windows

Open PowerShell in the game folder and run:

```powershell
.\scripts\Setup-And-Run.ps1
```

## Linux or macOS

Open Terminal in the game folder and run:

```sh
./scripts/setup-and-run.sh
```

The desktop setup checks for missing components and starts the game. It may ask you to approve installing them.

To check whether the desktop setup is ready without opening the game, run:

Windows PowerShell:

```powershell
.\scripts\Check-Prerequisites.ps1 -CheckOnly
```

Linux or macOS:

```sh
./scripts/check-prerequisites.sh --check-only
```

## Android tablets

Install [Termux:X11](https://github.com/termux/termux-x11/releases). In Termux, from the game folder, run setup once and use the second command each time you start the game:

```sh
./scripts/termux-setup-x11.sh
./scripts/termux-run-x11.sh
```

When the opening and loading screens finish, press Space or click **PRESS SPACE TO ENTER**.
