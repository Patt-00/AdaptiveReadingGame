# Start the game

Run the setup script for your device from the game folder. Setup installs or checks the required tools, configures a local database for saved accounts and progress, prepares the optional model helper, and starts the game. The first run asks for a local database URL, an application username, and a password that is not shown while you type. It asks for an administrator password only when the local database requires one.

The database stays on the device where it was created. Accounts and saved progress do not sync between devices.

## Windows

Open PowerShell in the game folder and run:

```powershell
.\scripts\setup-windows.ps1
```

If MySQL is missing, setup installs the vendor package without opening its configuration UI, then uses MySQL Configurator to initialize the server with TCP disabled before enabling loopback-only access. Windows Defender Firewall must be enabled; setup blocks inbound access to ports 3306 and 33060 temporarily and removes those blocks after confirming the listener is local. Enter the MySQL root password twice when prompted, then enter the local database details. If setup stops before it finishes, rerun it after fixing the displayed issue; the temporary inbound block stays in place until setup confirms local-only listening.

## Linux

Open Terminal in the game folder and run:

```sh
./scripts/setup-linux.sh
```

The automatic MySQL installation supports Fedora and Debian or Ubuntu on supported processor types. It may ask for administrator permission. Setup writes a loopback-only MySQL setting before starting the local service. On a new Fedora install, MySQL generates a one-time root password; setup prints the command to read it from `/var/log/mysqld.log` before entering it in the database prompt. Other Linux distributions need MySQL installed before running setup.

## macOS

Open Terminal in the game folder and run:

```sh
./scripts/setup-macos.sh
```

Homebrew is required for automatic installation. Setup installs MySQL if needed and starts its local service. Homebrew configures MySQL to accept connections from this device only.

## Android tablet with Termux:X11

Install [Termux:X11](https://github.com/termux/termux-x11/releases). In Termux, open the game folder and run:

```sh
./scripts/setup-termux.sh
```

This installs MariaDB inside the tablet's Debian environment. The database files remain there, and the launcher starts MariaDB for the game session. The model helper is optional; Java uses its built-in difficulty fallback if the model cannot run on the tablet.

To open the game later, run:

```sh
./scripts/termux-run-x11.sh
```

## Reconfigure the local database

Run the matching setup script with its database reconfiguration option:

Windows PowerShell:

```powershell
.\scripts\setup-windows.ps1 -ReconfigureDatabase
```

Linux or macOS:

```sh
./scripts/setup-linux.sh --reconfigure-database
./scripts/setup-macos.sh --reconfigure-database
```

Termux:

```sh
./scripts/setup-termux.sh --reconfigure-database
```

Unset any `ARG_DB_URL`, `ARG_DB_USER`, or `ARG_DB_PASSWORD` environment variables before reconfiguring. They take priority over the saved settings. Choose an unused application database username; setup will not reset an existing account for that local host.

## Check setup without opening the game

Windows PowerShell:

```powershell
.\scripts\Check-Prerequisites.ps1 -CheckOnly
```

Linux or macOS:

```sh
./scripts/check-prerequisites.sh --check-only
```

The check reports whether saved database settings exist. It does not print the password. If no database settings are present, a direct game launch uses temporary storage; new accounts and save slots are lost when the game closes.

## Game files

The music WAV files are included directly in GitHub source archives. Larger artwork and model files use Git LFS; [GitHub source archives contain LFS pointer files by default](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/managing-git-lfs-objects-in-archives-of-your-repository). To retrieve the complete artwork and bundled model, clone the repository with Git LFS installed, or run `git lfs pull` from a Git clone if setup reports pointer files.

When the opening and loading screens finish, press Space or click **PRESS SPACE TO ENTER**.
