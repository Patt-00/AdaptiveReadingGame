# Start the game

Run the setup script for your device from the game folder.

## Windows

Open PowerShell in the game folder and run:

```powershell
.\scripts\setup-windows.ps1
```

## Linux or macOS

Open Terminal in the game folder and run the script for your system:

```sh
./scripts/setup-linux.sh
./scripts/setup-macos.sh
```

Desktop setup checks for missing components, prepares the optional model helper, and starts the game. It may ask you to approve installing missing components. If model dependencies are unavailable, the game still runs with Java's built-in difficulty fallback.

The music WAVs are included directly in GitHub source archives. Larger artwork and model files use Git LFS; [GitHub source archives contain LFS pointer files by default](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/managing-git-lfs-objects-in-archives-of-your-repository). To retrieve the full artwork and bundled model, clone the repository with Git LFS installed, or run `git lfs pull` from a Git clone if setup reports pointer files.

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

Install [Termux:X11](https://github.com/termux/termux-x11/releases). In Termux, from the game folder, run setup:

```sh
./scripts/setup-termux.sh
```

For later launches, run:

```sh
./scripts/termux-run-x11.sh
```

Termux setup prepares the model helper inside its Debian environment when supported. If the CPU model packages are unavailable on the device, the game still runs with Java's difficulty fallback.

When the opening and loading screens finish, press Space or click **PRESS SPACE TO ENTER**.
