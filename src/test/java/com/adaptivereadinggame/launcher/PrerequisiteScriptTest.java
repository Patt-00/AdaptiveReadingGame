package com.adaptivereadinggame.launcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs({OS.LINUX, OS.MAC})
class PrerequisiteScriptTest {
    @TempDir Path temporary;
    private Path tools;
    private Path runtime;
    private final Path script = Path.of("scripts/check-prerequisites.sh").toAbsolutePath();

    @BeforeEach void prepare() throws Exception {
        tools = Files.createDirectories(temporary.resolve("fake tools"));
        runtime = Files.createDirectories(temporary.resolve("fake jdk/bin")).getParent();
        executable(tools.resolve("java"), "#!/bin/sh\nif [ -n \"${JAVA_TOOL_OPTIONS:-}\" ]; then printf 'Picked up JAVA_TOOL_OPTIONS: %s\\n' \"$JAVA_TOOL_OPTIONS\" >&2; fi\nprintf 'openjdk version \"25\"\\n' >&2\n");
        executable(tools.resolve("javac"), "#!/bin/sh\nif [ -n \"${JAVA_TOOL_OPTIONS:-}\" ]; then printf 'Picked up JAVA_TOOL_OPTIONS: %s\\n' \"$JAVA_TOOL_OPTIONS\" >&2; fi\nprintf 'javac 25\\n'\n");
        executable(runtime.resolve("bin/javac"), "#!/bin/sh\nexit 0\n");
        executable(tools.resolve("mvn"), "#!/bin/sh\nprintf 'Apache Maven 3.9.0\\nJava version: %s, runtime: %s\\n' \"$FAKE_MAVEN_JAVA\" \"$FAKE_RUNTIME\"\n");
    }

    @Test void checksMavensJavaRatherThanOnlyThePathJava() throws Exception {
        Result result = run(Map.of("FAKE_MAVEN_JAVA", "17"));
        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("Maven uses Java 17"));
    }

    @Test void platformSetupScriptsExposeHelpWithoutInstallingAnything() throws Exception {
        for (String name : java.util.List.of("setup-linux.sh", "setup-macos.sh", "setup-termux.sh")) {
            Result result = runScript(Path.of("scripts", name), "--help");
            assertEquals(0, result.exitCode(), name + ": " + result.output());
            assertTrue(result.output().toLowerCase(java.util.Locale.ROOT).contains("usage:"),
                    name + " should explain its startup command.");
        }
    }

    @Test void modelHelperSetupExposesNonInstallingHelp() throws Exception {
        Result result = runScript(Path.of("scripts", "setup-model-helper.sh"), "--help");
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().toLowerCase(java.util.Locale.ROOT).contains("model helper"));
        assertTrue(result.output().contains("Linux, including ARM64, uses CPU-only PyTorch."), result.output());
    }

    @Test void bundledMusicIsIncludedAsReadableWavFilesInSourceArchives() throws Exception {
        for (Path track : java.util.List.of(
                Path.of("src/main/resources/com/adaptivereadinggame/audio/comfortable-mystery.wav"),
                Path.of("assets/local-music/investigation.wav"),
                Path.of("assets/local-music/tense.wav"))) {
            byte[] header = Files.readAllBytes(track);
            assertTrue(header.length > 512, track + " should contain the WAV track, not an LFS pointer.");
            assertEquals("RIFF", new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII), track.toString());
            assertEquals("WAVE", new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII), track.toString());
            Process process = new ProcessBuilder("git", "check-attr", "filter", "--", track.toString())
                    .redirectErrorStream(true).start();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            String attribute = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), attribute);
            assertTrue(attribute.trim().endsWith("filter: unspecified"), track + ": " + attribute);
        }
    }

    @Test void termuxWrapperUsesTheSelectedProjectAndAcceptsFlagOrder() throws Exception {
        Path wrapperScripts = Files.createDirectories(temporary.resolve("wrapper/scripts"));
        executable(wrapperScripts.resolve("termux-setup-x11.sh"), "#!/bin/sh\nprintf 'SETUP_ARGS:%s\\n' \"$*\"\n");
        executable(wrapperScripts.resolve("termux-run-x11.sh"), "#!/bin/sh\nprintf 'DEFAULT_RUNNER\\n'\n");
        Path selectedScripts = Files.createDirectories(temporary.resolve("selected-project/scripts"));
        executable(selectedScripts.resolve("termux-run-x11.sh"), "#!/bin/sh\nprintf 'SELECTED_RUNNER\\n'\n");
        Path wrapper = wrapperScripts.resolve("setup-termux.sh");
        executable(wrapper, Files.readString(Path.of("scripts/setup-termux.sh")));

        Result installOnly = runScript(wrapper, "--project-dir", selectedScripts.getParent().toString(), "--install-only");
        assertEquals(0, installOnly.exitCode(), installOnly.output());
        assertTrue(installOnly.output().contains("SETUP_ARGS:"), installOnly.output());
        assertFalse(installOnly.output().contains("RUNNER"), installOnly.output());

        Result launch = runScript(wrapper, "--project-dir", selectedScripts.getParent().toString());
        assertEquals(0, launch.exitCode(), launch.output());
        assertTrue(launch.output().contains("SELECTED_RUNNER"), launch.output());
        assertFalse(launch.output().contains("DEFAULT_RUNNER"), launch.output());
    }

    @Test void setupInstallsPythonSupportWhenTheModelVenvIsMissing() throws Exception {
        Path packageLog = temporary.resolve("package-install.log");
        executable(tools.resolve("python3"), "#!/bin/sh\nexit 1\n");
        executable(tools.resolve("dnf"), "#!/bin/sh\nprintf '%s\\n' \"$*\" > \"$FAKE_PACKAGE_LOG\"\n");
        executable(tools.resolve("sudo"), "#!/bin/sh\nexec \"$@\"\n");

        ProcessBuilder builder = new ProcessBuilder("bash", Path.of("scripts/setup-and-run.sh").toAbsolutePath().toString(), "--install-only")
                .directory(Path.of(".").toAbsolutePath().toFile()).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(key -> key.startsWith("ARG_DB_") || key.equals("ARG_CONTENT_PACK") || key.equals("ARG_ASSET_DIR"));
        environment.put("PATH", tools + ":" + environment.getOrDefault("PATH", "/usr/bin:/bin"));
        environment.put("FAKE_RUNTIME", runtime.toString());
        environment.put("FAKE_MAVEN_JAVA", "25");
        environment.put("FAKE_PACKAGE_LOG", packageLog.toString());
        environment.put("ARG_MODEL_PYTHON", "python3");
        environment.put("ARG_MODEL_VENV_DIR", temporary.resolve("model helper venv").toString());

        Process process = builder.start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(Files.exists(packageLog), output);
        String installedPackages = Files.readString(packageLog);
        assertTrue(installedPackages.contains("python3"), installedPackages);
    }

    @Test void gameLauncherStartsTheConfiguredModelVenv() throws Exception {
        Path fixture = temporary.resolve("game-runner");
        Path scripts = Files.createDirectories(fixture.resolve("scripts"));
        Files.copy(Path.of("scripts/run-game.sh"), scripts.resolve("run-game.sh"));
        executable(scripts.resolve("check-prerequisites.sh"), "#!/bin/sh\nexit 0\n");

        Path customPython = Files.createDirectories(temporary.resolve("custom-model/bin")).resolve("python");
        executable(customPython, "#!/bin/sh\nif [ \"$1\" = \"-c\" ]; then exit 0; fi\nprintf 'CUSTOM_HELPER_STARTED\\n' >> \"$FAKE_HELPER_LOG\"\nexec sleep 30\n");
        executable(tools.resolve("mvn"), "#!/bin/sh\nsleep 0.2\nprintf 'GAME_LAUNCHED\\n'\n");

        Path helperLog = temporary.resolve("model-helper-started.log");
        ProcessBuilder builder = new ProcessBuilder("bash", scripts.resolve("run-game.sh").toString())
                .directory(fixture.toFile()).redirectErrorStream(true);
        var environment = builder.environment();
        environment.put("PATH", tools + ":" + environment.getOrDefault("PATH", "/usr/bin:/bin"));
        environment.put("ARG_MODEL_VENV_DIR", customPython.getParent().getParent().toString());
        environment.put("ARG_MODEL_PYTHON", "python3");
        environment.put("FAKE_HELPER_LOG", helperLog.toString());

        Process process = builder.start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("Starting the optional local model helper"), output);
        assertTrue(Files.readString(helperLog).contains("CUSTOM_HELPER_STARTED"));
    }

    @Test void termuxSetupForwardsTheModelVenvIntoDebian() throws Exception {
        Path projectRoot = Path.of(".").toAbsolutePath().normalize();
        Path helper = Path.of("scripts/termux-model-venv-path.sh").toAbsolutePath();
        ProcessBuilder builder = new ProcessBuilder("bash", "-c",
                "source \"$1\" && termux_model_venv_for_proot \"$2\"",
                "bash", helper.toString(), projectRoot.toString())
                .redirectErrorStream(true);
        builder.environment().put("ARG_MODEL_VENV_DIR", "model-cache/.venv");
        Process process = builder.start();
        process.getOutputStream().close();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertEquals("/workspace/AdaptiveReadingGame/model-cache/.venv", output.trim());

        for (Path script : java.util.List.of(Path.of("scripts/termux-setup-x11.sh"), Path.of("scripts/termux-run-x11.sh"))) {
            String contents = Files.readString(script);
            assertTrue(contents.contains("termux_model_venv_for_proot \"$project_root\""), script.toString());
            assertTrue(contents.contains("--env \"ARG_MODEL_VENV_DIR=$model_venv_in_debian\""), script.toString());
        }
    }

    @Test void parsesJavaVersionsWhenJavaToolOptionsAddsAStderrLine() throws Exception {
        Result result = run(Map.of("JAVA_TOOL_OPTIONS", "-Dprism.order=sw"));
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("OK: Java 25"));
        assertTrue(result.output().contains("OK: javac 25"));
    }

    @Test void acceptsAnExplicitEmptyPasswordAndNeverPrintsIt() throws Exception {
        Result result = run(Map.of("ARG_DB_URL", "jdbc:mysql://localhost/test", "ARG_DB_USER", "reader",
                "ARG_DB_PASSWORD", ""));
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Connectivity and migrations were not checked"));
    }

    @Test void rejectsPartialDatabaseSettingsAndMissingPack() throws Exception {
        Result missingPassword = run(Map.of("ARG_DB_URL", "jdbc:mysql://localhost/test", "ARG_DB_USER", "reader"));
        assertNotEquals(0, missingPassword.exitCode());
        assertTrue(missingPassword.output().contains("ARG_DB_PASSWORD"));
        Result missingPack = run(Map.of("ARG_CONTENT_PACK", temporary.resolve("missing.json").toString()));
        assertNotEquals(0, missingPack.exitCode());
        assertTrue(missingPack.output().contains("ARG_CONTENT_PACK"));
    }

    @Test void warnsAboutUnmaterializedArtwork() throws Exception {
        Path assets = temporary.resolve("artwork");
        Files.createDirectories(assets.resolve("characters"));
        Files.writeString(assets.resolve("characters/example.png"), "version https://git-lfs.github.com/spec/v1\n");
        Result result = run(Map.of("ARG_ASSET_DIR", assets.toString()));
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Git LFS pointers"));
    }

    @Test void warnsAboutUnmaterializedBundledStoryMusic() throws Exception {
        Path assets = temporary.resolve("story-music");
        Files.createDirectories(assets.resolve("local-music"));
        Files.writeString(assets.resolve("local-music/investigation.wav"),
                "version https://git-lfs.github.com/spec/v1\n");
        Result result = run(Map.of("ARG_ASSET_DIR", assets.toString()));
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Git LFS pointers"), result.output());
        assertTrue(result.output().contains("story music"), result.output());
    }

    private Result run(Map<String, String> overrides) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", script.toString(), "--check-only");
        builder.directory(temporary.toFile());
        builder.redirectErrorStream(true);
        var environment = builder.environment();
        environment.keySet().removeIf(key -> key.startsWith("ARG_DB_") || key.equals("ARG_CONTENT_PACK") || key.equals("ARG_ASSET_DIR"));
        environment.put("PATH", tools + ":" + environment.getOrDefault("PATH", "/usr/bin:/bin"));
        environment.put("FAKE_RUNTIME", runtime.toString());
        environment.put("FAKE_MAVEN_JAVA", "25");
        environment.putAll(overrides);
        Process process = builder.start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    private Result runScript(Path path, String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("bash");
        command.add(path.toAbsolutePath().toString());
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command)
                .directory(Path.of(".").toAbsolutePath().toFile()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void executable(Path path, String contents) throws Exception {
        Files.writeString(path, contents);
        assertTrue(path.toFile().setExecutable(true));
    }
    private record Result(int exitCode, String output) {}
}
