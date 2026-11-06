# Start the game

## Before the first launch

1. Open the `AdaptiveReadingGame` project folder. It should contain `pom.xml` and the `scripts` folder.
2. Open a terminal in that folder.
3. Run the command for your device below. Keep the terminal open while setup runs.
4. Follow the prompts until the game opens. The setup checks the required tools, prepares a local database, and installs the optional model helper when supported.

The database stays on the device where you set it up. Accounts and saved progress do not sync between devices. The setup asks for a local database URL, an application database username, and its password. Passwords are hidden while you type. These are separate from the account you create inside the game.

The **application database user** is created for the game. The **MySQL administrator user** already belongs to the MySQL server and is used once to create the game's database and application user. It is usually `root`; it is not your Windows username or your in-game username.

## Windows

1. Open PowerShell in the project folder.
2. Run:

   ```powershell
   .\scripts\setup-windows.ps1
   ```

3. Allow the administrator prompt so setup can install and start the local MySQL service.
4. If asked to create a MySQL administrator password, enter it twice. Setup keeps MySQL restricted to this device and temporarily blocks inbound connections while it checks the listener.
5. Enter the local database URL, application username, and application password. Press Enter to keep the suggested values. If setup asks for the MySQL administrator username, keep `root` unless you configured a different MySQL administrator. Enter that account's MySQL password, not your Windows or game password.
6. Wait for the game to open.

To start the game later, run:

```powershell
.\scripts\Run-Game.ps1
```

## Linux

1. Open Terminal in the project folder.
2. Run:

   ```sh
   ./scripts/setup-linux.sh
   ```

3. If asked, enter your computer's administrator password to install packages and start the database service.
4. On Fedora, setup uses Fedora's MariaDB server when its MariaDB package is already installed. Otherwise, supported Fedora, Debian, and Ubuntu setups use MySQL. A new Fedora MySQL installation may create a temporary root password; setup prints the command to read it before asking for the database details.
5. Enter the local database details and wait for the game to open.

To start the game later, run:

```sh
./scripts/run-game.sh
```

## macOS

1. Install Homebrew if it is not already installed.
2. Open Terminal in the project folder and run:

   ```sh
   ./scripts/setup-macos.sh
   ```

3. Allow setup to install any missing tools and start local MySQL.
4. Enter the local database details when prompted, then wait for the game to open.

Run `./scripts/setup-macos.sh` again when you want to open the game later. It will use the saved database settings.

## Android tablet with Termux:X11

1. Install the Termux app and the separate [Termux:X11 app](https://github.com/termux/termux-x11/releases).
2. In Termux, open the project folder.
3. Run:

   ```sh
   ./scripts/setup-termux.sh
   ```

4. Let setup install the tablet's required packages and configure MariaDB inside its Debian environment. Open Termux:X11 if Android asks.
5. Wait for the game window to appear.

To open the game later, run this in the project folder:

```sh
./scripts/termux-run-x11.sh
```

## Change saved database settings

Use this only if you want to replace the saved database connection. Do not add the reconfiguration option to the first setup command.

Windows PowerShell:

```powershell
.\scripts\setup-windows.ps1 -ReconfigureDatabase
```

Linux:

```sh
./scripts/setup-linux.sh --reconfigure-database
```

macOS:

```sh
./scripts/setup-macos.sh --reconfigure-database
```

Termux:

```sh
./scripts/setup-termux.sh --reconfigure-database
```

Before reconfiguring, remove any `ARG_DB_URL`, `ARG_DB_USER`, and `ARG_DB_PASSWORD` environment settings so they do not override the saved connection. Choose an unused database username; setup will not change an existing account.

## If MySQL rejects the administrator login

An error such as `SQLState 28000, error code 1045` means MySQL rejected the administrator username and password. Setup stops before creating the game's database or application user. Check that you entered the MySQL administrator account and the password set for that account; leaving the password blank works only when that MySQL account has no password.

If you have forgotten the MySQL `root` password, follow the [official MySQL 8.4 Windows password reset steps](https://dev.mysql.com/doc/refman/8.4/en/resetting-permissions.html#resetting-permissions-windows). Run the reset procedure locally as a Windows administrator, remove its temporary password file when finished, then run `setup-windows.ps1` again. Do not send the MySQL password in chat.

## If artwork or story music is missing

Some large artwork and model files use Git LFS. If setup warns that files are pointer files, install Git LFS, open a terminal in the project folder, and run:

```sh
git lfs pull
```

The opening and loading screens finish before the main menu. Press **Space** or click **PRESS SPACE TO ENTER** to continue.
