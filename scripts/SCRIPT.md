# Start the game

## Before the first launch

1. Open the `AdaptiveReadingGame` project folder. It should contain `pom.xml` and the `scripts` folder.
2. Open a terminal in that folder.
3. Run the command for your device below. Keep the terminal open while setup runs.
4. Follow the prompts until the game opens. The setup checks the required tools, prepares a local database, and installs the optional model helper when supported.

The database stays on the device where you set it up. Accounts and saved progress do not sync between devices. Setup generates the game's database username and password, saves them for the current Windows user, and keeps them separate from the account you create inside the game.

On Windows, setup creates a separate game-only MySQL service and protects its generated administrator password for the current Windows user. It leaves other MySQL services and databases alone. On Linux or macOS, setup may need the existing local database administrator password once; the game database login is still generated automatically.

## Windows

1. Open PowerShell in the project folder.
2. Run:

   ```powershell
   .\scripts\setup-windows.ps1
   ```

3. Allow the Windows administrator prompt so setup can create the local game database service.
4. Wait while setup prepares the database and starts the game. No MySQL username or password entry is needed.

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
4. On Fedora, setup uses Fedora's MariaDB server when its MariaDB package is already installed. Otherwise, supported Fedora, Debian, and Ubuntu setups use MySQL. Setup generates and saves the game's database login. Some existing MySQL installations may ask once for the local MySQL administrator password.
5. Wait for the game to open.

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
4. If the existing MySQL server requests its administrator password, enter it once. Setup generates and saves the game's database login.
5. Wait for the game to open.

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

Before reconfiguring, remove any `ARG_DB_URL`, `ARG_DB_USER`, and `ARG_DB_PASSWORD` environment settings so they do not override the saved connection. On Windows, reconfiguration uses the dedicated game service; it does not reset or modify another MySQL service.

## If an existing database server rejects the administrator login

An error such as `SQLState 28000, error code 1045` means the existing MySQL server rejected its administrator username or password. Setup stops before creating the game's database or application user. Check that you entered the MySQL administrator account and its password. The Windows setup creates a separate game service, so it does not need the password for another installed MySQL service.

If you have forgotten an existing server's MySQL `root` password, use that server's official recovery instructions. The game's Windows service stores its own generated password in a file protected for the Windows account that set it up.

## If artwork or story music is missing

Some large artwork and model files use Git LFS. If setup warns that files are pointer files, install Git LFS, open a terminal in the project folder, and run:

```sh
git lfs pull
```

The opening and loading screens finish before the main menu. Press **Space** or click **PRESS SPACE TO ENTER** to continue.
