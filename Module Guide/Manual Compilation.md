# Manual JavaFX Compilation — Windows, Linux, and macOS

This guide compiles AdaptiveReadingGame using `javac` and runs it using `java` in a terminal. Maven and an IDE are not required for these commands.

## 1. Folder layout

Run the commands from the **AdaptiveReadingGame project root**, the parent folder of `src` and `Modules`. Create `bin` here, not inside `src` or inside the guide folder.

```text
AdaptiveReadingGame/
├── bin/                         ← compiled .class files
├── Module Guide/
│   └── Manual Compilation.md
├── Modules/
│   └── javafx-sdk-21.0.5/
│       └── lib/                 ← JavaFX SDK for your OS
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── adaptivereadinggame/
│       │           ├── AdaptiveReadingGameApp.java
│       │           └── other Java source files
│       └── resources/
└── pom.xml
```

The main application's package is `com.adaptivereadinggame`. Its launch class is **`com.adaptivereadinggame.AdaptiveReadingGameApp`**, and compilation produces `bin/com/adaptivereadinggame/*.class`. The earlier beginner dashboard is also retained under `main.java`; run `main.java.AdaptiveReadingGameApp` instead if you want that learning example.

## 2. Requirements

- Install a JDK with both `java` and `javac`. Use JDK 21 or newer to match the project's compiler release of 21.
- Download and extract the JavaFX 21.0.5 **SDK** for your operating system and processor architecture. Match Windows, Linux, or macOS and x64 or ARM64 to your computer.
- Put the extracted SDK in `Modules`. The examples assume `Modules/javafx-sdk-21.0.5/lib`. Change the path if your SDK folder has a different name.
- Verify Java in your terminal:

```text
java -version
javac -version
```

Use the same JDK installation for compiling and running. JavaFX SDKs include native libraries, so each person must use the SDK for their operating system and processor architecture.

## 3. Windows — PowerShell

Open PowerShell. Replace `C:\path\to\AdaptiveReadingGame` with the actual project folder on your computer. All following paths are relative to that folder.

```powershell
cd "C:\path\to\AdaptiveReadingGame"

# Create bin in the project root. Existing contents are kept.
New-Item -ItemType Directory -Path .\bin -Force | Out-Null

# Point to the Windows JavaFX SDK's lib folder.
$javafxLib = (Resolve-Path ".\Modules\javafx-sdk-21.0.5\lib").Path

# Collect every Java source file, including files in subfolders.
$javaSources = @(Get-ChildItem ".\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName })

# Compile into bin. Run the next command only if this succeeds.
javac --release 21 --module-path "$javafxLib" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 -d .\bin @javaSources

# Run the JavaFX application.
java --enable-native-access=javafx.graphics --module-path "$javafxLib" --add-modules javafx.controls,javafx.fxml -cp "bin;src/main/resources" com.adaptivereadinggame.AdaptiveReadingGameApp
```

These commands are for **PowerShell**, not Windows Command Prompt. Keep using the same terminal so `$javafxLib` remains available.

## 4. Linux — Bash

Open a Bash terminal. Replace `/path/to/AdaptiveReadingGame` with the actual project folder on your computer. All following paths are relative to that folder.

```bash
cd "/path/to/AdaptiveReadingGame"

# Create bin in the project root. Existing contents are kept.
mkdir -p bin

# Point to the Linux JavaFX SDK's lib folder.
javafx_lib="$PWD/Modules/javafx-sdk-21.0.5/lib"

# Collect source paths safely, including names containing spaces.
mapfile -d '' -t java_sources < <(find src/main/java -type f -name '*.java' -print0)

# Compile into bin. Run the next command only if this succeeds.
javac --release 21 --module-path "$javafx_lib" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 -d bin "${java_sources[@]}"

# Run the JavaFX application.
java --enable-native-access=javafx.graphics --module-path "$javafx_lib" --add-modules javafx.controls,javafx.fxml -cp "bin:src/main/resources" com.adaptivereadinggame.AdaptiveReadingGameApp
```

These source-collection commands require Bash. Keep using the same terminal so the variables remain available.

## 5. macOS — Terminal / zsh

Open Terminal. Replace `/path/to/AdaptiveReadingGame` with the actual project folder on your computer. Use the macOS SDK for your Mac's processor: ARM64 for Apple Silicon or x64 for Intel.

```zsh
cd "/path/to/AdaptiveReadingGame"

# Create bin in the project root. Existing contents are kept.
mkdir -p bin

# Point to the macOS JavaFX SDK's lib folder.
javafx_lib="$PWD/Modules/javafx-sdk-21.0.5/lib"

# zsh recursively collects Java files into an array.
java_sources=(src/main/java/**/*.java(N))

# Compile into bin. Run the next command only if this succeeds.
javac --release 21 --module-path "$javafx_lib" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 -d bin "${java_sources[@]}"

# Run the JavaFX application.
java --enable-native-access=javafx.graphics --module-path "$javafx_lib" --add-modules javafx.controls,javafx.fxml -cp "bin:src/main/resources" com.adaptivereadinggame.AdaptiveReadingGameApp
```

The source-collection syntax above is for **zsh**, the default shell on modern macOS. Keep using the same terminal so the variables remain available.

## 6. What the options mean

| Option | Purpose |
|---|---|
| `--release 21` | Compile for Java 21, matching the project's Maven configuration. |
| `--module-path` | Tell Java where to find JavaFX modules. |
| `--add-modules javafx.controls,javafx.fxml` | Enable JavaFX controls, the FXML loader, and required graphics/base modules. |
| `-encoding UTF-8` | Read source files using UTF-8. |
| `-d bin` | Put compiled classes in the project-root `bin` directory. |
| `-cp` | Locate compiled classes and resources when running. |
| `--enable-native-access=javafx.graphics` | Allow JavaFX graphics to access native platform libraries. |

Windows separates classpath entries with `;`. Linux and macOS use `:`. The resource folder is included so CSS, FXML, and images placed there can be loaded from the classpath without copying them into `bin`.

## 7. After changing the code

Run the compilation command again, then run the launch command. Compilation normally prints nothing when it succeeds. Do not launch after compiler errors: old `.class` files might still exist in `bin`.

The compile commands include all Java source files and enable FXML for the main application's dashboard. If files declare a different package, update the launch class accordingly.

## 8. Common problems

| Message or symptom | What to check |
|---|---|
| `javac` not found or not recognized | Install a JDK and make its `bin` directory available on PATH. Reopen the terminal. |
| `release version 21 not supported` | Your compiler is older than JDK 21. |
| `Module javafx.controls not found` | Check the SDK `lib` path and that it contains `javafx.controls.jar`. |
| `Could not find or load main class` | Compile successfully, stay in the project root, and use `com.adaptivereadinggame.AdaptiveReadingGameApp`. |
| `UnsupportedClassVersionError` | The runtime JDK is older than the compiler target or JavaFX dependency requirements. |
| Graphics/native-library errors | Check the SDK's OS and processor architecture; a Windows DLL does not work on Linux/macOS. |
| GUI cannot open through SSH/headless session | Run from a desktop session with a display. |

## References

- [Official JavaFX manual setup and command-line instructions](https://openjfx.io/openjfx-docs/)
- [JavaFX SDK downloads](https://gluonhq.com/products/javafx/)
